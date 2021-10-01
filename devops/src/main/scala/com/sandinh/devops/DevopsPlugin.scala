package com.sandinh.devops

import com.typesafe.config.ConfigFactory
import org.scalafmt.sbt.ScalafmtPlugin.autoImport.{
  scalafmtCheck,
  scalafmtSbtCheck
}
import sbt.*
import sbt.Keys.*
import sbt.Def.Initialize
import sbt.io.Using
import sbtdynver.DynVerPlugin.autoImport.*

import java.nio.file.Files
import scala.util.matching.Regex
import scala.collection.JavaConverters.*
import scala.collection.immutable.Seq
import scala.sys.env
import Utils.{gitHubScmInfo, isSnapshotVersion, isTag, orBoom, ResultOps}

object DevopsPlugin extends AutoPlugin {
  private[this] val impl: ImplTrait = Impl
  import impl.*

  override def trigger = allRequirements
  override def requires = requiresImpl

  val scalafmtVersion = "3.0.4"

  object autoImport {
    val devopsSetup = taskKey[Unit]("Setup devops stuff")
    val devopsQA = taskKey[Unit]("QA (Quality Assurance)")
    val devopsNexusHost = settingKey[String](
      "Your private nexus host, ex repo.example.com. Not used in devops-oss"
    )
  }
  import autoImport.*

  override lazy val buildSettings: Seq[Setting[?]] = Seq(
    homepage := scmInfo.value.map(_.browseUrl),
    dynverTagPrefix := "v",
    dynverSonatypeSnapshots := true,
    scmInfo ~= {
      case Some(info) => Some(info)
      case None       => gitHubScmInfo
    },
  ) ++ buildSettingsImpl

  private val inAny = ScopeFilter(inAnyProject, inAnyConfiguration)

  private val savePublishInfo = taskKey[Unit](
    "Save publish info into a file to be used in later Mattermost/ Slack notify CI job"
  )
  private val publishInfoPath = "target/publish.info"

  override lazy val globalSettings: Seq[Setting[?]] = Seq(
    devopsSetup := sdSetupTask.value,
    devopsQA := {
      // <task>.value macro causing spurious “a pure expression does nothing” warning
      // This `val _ =` is not need if we set `pluginCrossBuild` to a newer sbt version
      val _ = sdQaBaseTask.value
      val __ = qaVersionTask.all(ScopeFilter(inAnyProject)).value
      val fmtOk = scalafmtCheck.?.all(inAny).result.value.isSuccess
      val sbtOk = scalafmtSbtCheck.?.all(inAny).result.value.isSuccess
      orBoom(
        fmtOk && sbtOk,
        """Some files aren't formatted properly.
          |You should format code by running: sbt "+scalafmtAll; +scalafmtSbt"
          |RECOMMEND: git commit or stash all changes before formatting.""".stripMargin
      )
    },
    savePublishInfo := savePublishInfoTask.value,
  ) ++ ciReleaseSettings ++ globalSettingsImpl

  // see CiReleasePlugin.globalSettings
  lazy val ciReleaseSettings: Seq[Setting[?]] = Seq(
    Test / publishArtifact := false,
    publishMavenStyle := true,
    commands += Command.command("ci-release")(ciRelease),
  )

  def ciRelease(state: State): State = {
    println(s"Running ci-release.\n  branch=${env.get("GITHUB_REF")}")
    IO.write(state.baseDir / publishInfoPath, "")
    if (!isTag) {
      if (isSnapshotVersion(state)) {
        println(s"No tag push, publishing SNAPSHOT")
        ciReleaseSnapshotCmds ::: "savePublishInfo" :: state
      } else {
        // Happens when a tag is pushed right after merge causing the master branch
        // job to pick up a non-SNAPSHOT version even if isTag=false.
        println(
          "Snapshot releases must have -SNAPSHOT version number, doing nothing"
        )
        state
      }
    } else {
      println("Tag push detected, publishing a stable release")
      ciReleaseCmds ::: "savePublishInfo" :: state
    }
  }

  override lazy val projectSettings: Seq[Setting[?]] = projectSettingsImpl

  def qaVersionTask: Initialize[Task[Unit]] = Def.task {
    val dynver = (ThisBuild / dynverInstance).value
    val date = (ThisBuild / dynverCurrentDate).value
    orBoom(
      version.value == dynver.sonatypeVersion(date),
      s"Project ${name.value} define `version` manually!"
    )
  }

  private val projectAndSkip = Def.task {
    projectID.value -> (publish / skip).value
  }

  def savePublishInfoTask: Initialize[Task[Unit]] = Def.task {
    val version = projectAndSkip
      .all(ScopeFilter(inAnyProject))
      .value
      .collect { case (m, false) => m }
      .groupBy(_.revision)
      .map { case (rev, ms) => s"**$rev**: " + ms.map(_.name).mkString(", ") }
      .mkString("\n")
    IO.write((ThisBuild / baseDirectory).value / publishInfoPath, version)
  }

  def sdSetupTask: Initialize[Task[Unit]] = Def.task {
    val baseDir = (ThisBuild / baseDirectory).value
    val log = streams.value.log
    setupFiles(baseDir, log)
    setupReadme(baseDir / "README.md", log)
    log.info("Done")
  }

  private lazy val ymlReplace: Map[String, Seq[String]] = {
    def envReleaseOss =
      """env:
        |  PGP_PASSPHRASE: ${{ secrets.PGP_PASSPHRASE }}
        |  PGP_SECRET: ${{ secrets.PGP_SECRET }}
        |  SONATYPE_PASSWORD: ${{ secrets.SONATYPE_PASSWORD }}
        |  SONATYPE_USERNAME: ${{ secrets.SONATYPE_USERNAME }}
        |  # optional
        |  #CI_CLEAN: '; clean ; sonatypeBundleClean'
        |  #CI_RELEASE: '+publishSigned'
        |  #CI_SONATYPE_RELEASE: 'sonatypeBundleRelease'
        |  #CI_SNAPSHOT_RELEASE: '+publish'""".stripMargin.linesIterator.toList
    val envNexus =
      """env:
        |  NEXUS_USER: ${{ secrets.NEXUS_USER }}
        |  NEXUS_PASS: ${{ secrets.NEXUS_PASS }}""".stripMargin.linesIterator.toList
    def envReleaseNexus = envNexus ++
      """  # optional
        |  #CI_CLEAN: 'clean'
        |  #CI_RELEASE: '+publish'
        |  #CI_SNAPSHOT_RELEASE: '+publish'""".stripMargin.linesIterator

    def condOss =
      """if: |
        |  success() &&
        |  github.event_name == 'push' &&
        |  (github.ref == 'refs/heads/main' ||
        |    github.ref == 'refs/heads/master' ||
        |    startsWith(github.ref, 'refs/tags/'))""".stripMargin.linesIterator.toList
    def condNexus = Seq("if: success()")

    Map(
      "# env: nexus" -> (if (isOss) Nil else envNexus),
      "# env: ci-release" -> (if (isOss) envReleaseOss else envReleaseNexus),
      "# if: publish-condition" -> (if (isOss) condOss else condNexus)
    )
  }

  private def setupFiles(baseDir: File, log: Logger): Unit = {
    // workaround for `scripted all/setup` sbt-test when test on branch != main
    val branch = sys.props.getOrElse("devops.branch", "main")
    val baseUrl = s"https://raw.githubusercontent.com/ohze/sbt-devops/$branch"

    def fetch(filename: String, toDir: String)(
        linesTransformer: Seq[String] => Seq[String]
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

    fetch("sbt-devops.yml", ".github/workflows")(_.flatMap { line =>
      ymlReplace.get(line.trim) match {
        case None => Seq(line)
        case Some(replace) =>
          val spaces = " " * line.indexWhere(_ != ' ')
          replace.map(spaces + _)
      }
    })
  }

  private def badge(user: String, repo: String) = {
    val url = s"https://github.com/$user/$repo/actions/workflows/sbt-devops.yml"
    s"[![CI]($url/badge.svg)]($url)"
  }

  def setupReadme(readme: File, log: Logger): Unit =
    Utils.gitHubInfo match {
      case None =>
        log.warn("""Can't add CI badge to README.md
            |Pls set github repo as your git `origin` remote and re-run sbt devopsSetup
            |""".stripMargin)
        IO.touch(readme)

      case Some((user, repo)) if !readme.exists() =>
        IO.write(readme, s"# $repo\n\n${badge(user, repo)}\n\nTODO\n")

      case Some((user, repo)) =>
        val b = badge(user, repo)
        val hasBadge = Files.lines(readme.toPath).anyMatch(_ == b)
        if (!hasBadge) {
          val lines = IO.readLines(readme).toIndexedSeq
          def onlyEq(i: Int) = i < lines.size && lines(i).forall(_ == '=')
          def isH1(i: Int) = i < lines.size && lines(i).startsWith("# ")

          def h1(from: Int): Int =
            if (isH1(from)) 0
            else if (onlyEq(from + 1)) 1
            else -1

          val patchIdx = lines.indices
            .collectFirst { case i if h1(i) != -1 => i + h1(i) + 1 }
            .getOrElse(-1)

          IO.writeLines(readme, lines.patch(patchIdx, "" :: b :: "" :: Nil, 0))
        }
    }

  def sdQaBaseTask: Initialize[Task[Unit]] = Def.task {
    val baseDir = (ThisBuild / baseDirectory).value
    validateScalafmtConf(baseDir)
    validateGithubCI(baseDir)
    validatePluginsSbt(baseDir)

    val f = baseDir / "README.md"
    orBoom(f.isFile, "You should create README.md by running sbt devopsSetup")
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

    (
      has("sbt-devops-oss") || has("sd-devops-oss"),
      has("sbt-devops") || has("sd-devops")
    ) match {
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
    words.contains("devopsQA")
  }

  def validateGithubCI(baseDir: File): Unit = {
    val f = baseDir / ".github" / "workflows" / "sbt-devops.yml"
    orBoom(f.isFile, s"$f: File not found!")
    orBoom(
      IO.readLines(f).exists(isSdQAStep),
      s"$f must define step:\n- run: sbt <optional params> devopsQA"
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
}
