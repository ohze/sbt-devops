package com.sandinh.devops

import com.typesafe.config.ConfigFactory
import com.typesafe.sbt.GitPlugin
import org.scalafmt.sbt.ScalafmtPlugin.autoImport.scalafmtCheckAll
import sbt._
import sbt.Keys._
import sbt.Def.Initialize
import sbt.io.Using
import sbt.plugins.JvmPlugin
import sbtdynver.DynVerPlugin

import java.nio.file.Files
import scala.util.matching.Regex

object SdDevOpsPlugin extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = JvmPlugin && DynVerPlugin && GitPlugin

  val scalafmtVersion = "3.0.4"

  object autoImport {
    val sdQA = taskKey[Unit]("SanDinh QA (Quality Assurance)")
    val sdSetup = taskKey[Unit]("Setup devops stuff")
  }
  import autoImport._

  override lazy val buildSettings: Seq[Setting[_]] = Seq(
    organization := "com.sandinh",
    homepage := scmInfo.value.map(_.browseUrl),
  ) ++ Impl.buildSettings

  object env {
    def unapply(key: String): Option[String] = sys.env.get(key)
    def apply(keys: String*): Option[String] = keys.collectFirst {
      case env(v) => v
    }
  }

  override lazy val globalSettings: Seq[Setting[_]] = Seq(
    sdQA := {
      // <task>.value macro causing spurious “a pure expression does nothing” warning
      // This `val _ =` is not need if we set `pluginCrossBuild` to a newer sbt version
      val _ = validates.value
      val __ = scalafmtCheckAll.all(ScopeFilter(inAnyProject)).value
    },
    sdSetup := sdSetupTask.value,
  ) ++ Impl.globalSettings

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
  ) ++ Impl.projectSettings

  lazy val sdSetupTask: Initialize[Task[Unit]] = Def.task {
    val baseUrl = "https://raw.githubusercontent.com/ohze/sd-devops/main"
    val baseDir = (ThisBuild / baseDirectory).value
    val log = streams.value.log
    for {
      f <- Seq(
        ".scalafmt.conf",
        ".github/workflows/test.yml",
        ".github/workflows/release.yml"
      )
      to = baseDir / f
      if (!to.exists())
    } {
      log.info(s"download $f")
      Using.urlInputStream(new URL(s"$baseUrl/$f"))(IO.transfer(_, to))
    }

    val readme = baseDir / "README.md"
    def badge(user: String, repo: String) = {
      val url = s"https://github.com/$user/$repo/actions/workflows/test.yml"
      s"[![CI]($url/badge.svg)]($url)"
    }
    if (!readme.exists()) {
      Utils.gitHubInfo match {
        case None => IO.write(readme, "# <repo>\n<badge>\n\nTODO\n")
        case Some((user, repo)) =>
          IO.write(
            readme,
            s"""# $repo
               |${badge(user, repo)}
               |
               |TODO
               |""".stripMargin
          )
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
            val insert = badge(user, repo) :: "" :: Nil
            IO.writeLines(readme, head ++ insert ++ tail)
        }
      }
    }

    log.info("Done")
  }

  lazy val validates: Initialize[Task[Unit]] = Def.task {
    val baseDir = (ThisBuild / baseDirectory).value
    validateScalafmtConf(baseDir)
    validateGithubCI(baseDir)
    validatePluginsSbt(baseDir)
  }

  private def validatePluginsSbt(baseDir: File): Unit = {
    val f = baseDir / "project/plugins.sbt"
    val lines = IO.readLines(f)

    def has(plugin: String) = {
      val s = Regex.quote(plugin)
      val r = s""".+%\\s*"$s".*"""
      lines.exists { line =>
        !line.stripLeading.startsWith("//") && line.matches(r)
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
    val words = s.substring(prefix.length).split("""[\s"']""")
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

//  def releaseTag: String = env("GITHUB_REF").getOrElse("<unknown>")
//
//  def currentBranch: String = releaseTag
//
//  def isTag: Boolean = env("GITHUB_REF").exists(_.startsWith("refs/tags"))
}
