package com.sandinh.devops

import com.typesafe.config.ConfigFactory
import com.typesafe.sbt.GitPlugin
import org.scalafmt.sbt.ScalafmtPlugin.autoImport.{
  scalafmtCheck,
  scalafmtSbtCheck
}
import sbt._
import sbt.Keys._
import sbt.Def.Initialize
import sbt.io.Using
import sbt.plugins.JvmPlugin
import sbtdynver.DynVerPlugin
import sbtdynver.DynVerPlugin.autoImport.{
  dynverCurrentDate,
  dynverGitDescribeOutput,
  dynverSeparator,
  dynverSonatypeSnapshots
}

import java.nio.file.Files
import scala.util.matching.Regex
import scala.collection.JavaConverters._
import scala.collection.immutable.Seq

object SdDevOpsPlugin extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = JvmPlugin && DynVerPlugin && GitPlugin

  val scalafmtVersion = "3.0.4"

  object autoImport {
    val sdQA = taskKey[Unit]("SanDinh QA (Quality Assurance)")
    val sdSetup = taskKey[Unit]("Setup devops stuff")
  }
  import autoImport._

  private val dynVer =
    settingKey[String]("version defined by DynVerPlugin.buildSettings")

  private val validateVersion =
    taskKey[Unit]("validate that you don't define version manually")

  override lazy val buildSettings: Seq[Setting[_]] = Seq(
    organization := "com.sandinh",
    homepage := scmInfo.value.map(_.browseUrl),
    dynVer := { // @see in DynVerPlugin.buildSettings
      val out = dynverGitDescribeOutput.value
      val date = dynverCurrentDate.value
      val separator = dynverSeparator.value
      if (dynverSonatypeSnapshots.value)
        out.sonatypeVersionWithSep(date, separator)
      else out.versionWithSep(date, separator)
    },
  ) ++ Impl.buildSettings

  private val inAny = ScopeFilter(inAnyProject, inAnyConfiguration)

  override lazy val globalSettings: Seq[Setting[_]] = Seq(
    sdQA := {
      // <task>.value macro causing spurious “a pure expression does nothing” warning
      // This `val _ =` is not need if we set `pluginCrossBuild` to a newer sbt version
      val _ = validatesTask().value
      val fmtOk = scalafmtCheck.?.all(inAny).result.value.isSuccess
      val sbtOk = scalafmtSbtCheck.?.all(inAny).result.value.isSuccess
      orBoom(
        fmtOk && sbtOk,
        """Some files aren't formatted properly.
          |You should format code by running: sbt "+scalafmtAll; +scalafmtSbt"
          |RECOMMEND: git commit or stash all changes before formatting.""".stripMargin
      )
    },
    sdSetup := sdSetupTask().value,
  ) ++ Impl.globalSettings

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    validateVersion := validateVersionTask().value
  ) ++ Impl.projectSettings

  def sdSetupTask(): Initialize[Task[Unit]] = Def.task {
    val baseDir = (ThisBuild / baseDirectory).value
    val log = streams.value.log
    setupFiles(baseDir, log)
    setupReadme(baseDir / "README.md", log)
    log.info("Done")
  }

  private def setupFiles(baseDir: File, log: Logger): Unit = {
    val baseUrl = "https://raw.githubusercontent.com/ohze/sd-devops/main"
    type Trans = Seq[String] => Seq[String]

    def fetch(filename: String, toDir: String)(
        linesTransformer: Trans
    ): Unit = {
      val to = baseDir / toDir / filename
      if (!to.exists()) {
        val url = new URL(s"$baseUrl/files/$filename")
        log.info(s"download $url\nto $to")
        val lines =
          Using.urlReader(IO.utf8)(url)(_.lines().iterator().asScala.toList)
        Using.fileWriter()(to) { w =>
          linesTransformer(lines).foreach { s => w.write(s); w.newLine() }
        }
      }
    }

    fetch(".scalafmt.conf", "")(identity)

    fetch("release.yml", ".github/workflows") { lines =>
      val (head, tail0) = lines.span(_ != "# magic-comment: oss")
      val (mid, tail1) = tail0.span(_ != "# magic-comment: bennuoc")
      val tail = (if (Impl.isOss) mid else tail1).drop(1)
      val (mandatory, optional) = tail.span(_ != "# optional")
      head ++ mandatory.map(_.substring(1)) ++ optional
    }

    val removeEnvs: Trans = lines => {
      val (head, tail) = lines.span(!isSdQAStep(_))
      (head :+ tail.head) ++ tail.drop(4)
    }
    fetch("test.yml", ".github/workflows")(
      if (Impl.isOss) removeEnvs else identity
    )
  }

  def setupReadme(readme: File, log: Logger): Unit = {
    def badge(user: String, repo: String) = {
      val url = s"https://github.com/$user/$repo/actions/workflows/test.yml"
      s"[![CI]($url/badge.svg)]($url)"
    }
    if (!readme.exists()) {
      Utils.gitHubInfo match {
        case None => IO.write(readme, "# <repo>\n\n<badge>\n\nTODO\n")
        case Some((user, repo)) =>
          IO.write(readme, s"# $repo\n\n${badge(user, repo)}\n\nTODO\n")
      }
    } else {
      val hasBadge = Files
        .lines(readme.toPath)
        .anyMatch(_.contains(".yml/badge.svg)](https://github.com/"))
      if (!hasBadge) {
        Utils.gitHubInfo match {
          case None =>
            log.warn(
              "Can't get github url from `git ls-remote --get-url origin`"
            )
          case Some((user, repo)) =>
            val (head, tail) = IO
              .readLines(readme)
              .span(l => l.trim.isEmpty || l.trim.startsWith("#"))
            val insert = "" :: badge(user, repo) :: "" :: Nil
            IO.writeLines(readme, head ++ insert ++ tail)
        }
      }
    }
  }

  def validatesTask(): Initialize[Task[Unit]] = Def.task {
    val baseDir = (ThisBuild / baseDirectory).value
    validateScalafmtConf(baseDir)
    validateGithubCI(baseDir)
    validatePluginsSbt(baseDir)
    val _ = validateVersion.all(ScopeFilter(inAnyProject)).value
  }

  def validateVersionTask(): Initialize[Task[Unit]] = Def.task {
    val v = version.value
    val dv = (ThisBuild / dynVer).value
    orBoom(v == dv, s"Project ${name.value} define `version` manually!")
  }

  private def validatePluginsSbt(baseDir: File): Unit = {
    val f = baseDir / "project/plugins.sbt"
    val lines = IO.readLines(f)

    def has(plugin: String) = {
      val s = Regex.quote(plugin)
      val r = s""".+%\\s*"$s".*"""
      lines.exists { line =>
        !line.trim.startsWith("//") && line.matches(r)
      }
    }
    def check(plugin: String): Unit =
      orBoom(!has(plugin), s"You should remove $plugin from $f")

    (has("sd-devops-oss"), has("sd-devops")) match {
      case (false, false) => // do nothing
      case (isOss, _) =>
        Seq("sbt-dynver", "sbt-git", "sbt-scalafmt").foreach(check)
        if (isOss)
          Seq("sbt-ci-release", "sbt-sonatype", "sbt-pgp").foreach(check)
    }
  }

  private def isSdQAStep(line: String): Boolean = {
    val prefix = "- run: sbt "
    val s = line.trim
    if (!s.startsWith(prefix)) return false
    val words = s.substring(prefix.length).split("""[/\s"']""")
    words.contains("sdQA")
  }

  def validateGithubCI(baseDir: File): Unit = {
    for {
      name <- Seq("test.yml", "release.yml")
      f = baseDir / ".github" / "workflows" / name
    } orBoom(f.isFile, s"$f: File not found!")

    val testYml = baseDir / ".github" / "workflows" / "test.yml"
    orBoom(
      IO.readLines(testYml).exists(isSdQAStep),
      s"$testYml must define step:\n- run: sbt <optional params> sdQA"
    )
  }

  def validateScalafmtConf(baseDir: File): Unit = {
    val f = baseDir / ".scalafmt.conf"
    orBoom(f.isFile, s"$f: File not found!")

    val c = ConfigFactory.parseFile(f)
    orBoom(c.hasPath("version"), s"$f: `version` config not found!")

    orBoom(
      c.getString("version") == scalafmtVersion,
      s"$f: You must update `version` to $scalafmtVersion"
    )
  }

  def orBoom(check: => Boolean, msg: String): Unit = {
    if (!check) throw new MessageOnlyException(msg)
  }

  implicit class ResultOps(val r: Result[_]) extends AnyVal {
    def isSuccess: Boolean = r match {
      case Value(_) => true
      case Inc(_)   => false
    }
  }
}
