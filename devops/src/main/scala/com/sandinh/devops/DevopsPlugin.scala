package com.sandinh.devops

import com.typesafe.config.ConfigFactory
import org.scalafmt.sbt.ScalafmtPlugin.autoImport.{
  scalafmtCheck,
  scalafmtSbtCheck
}
import sbt._
import sbt.Keys._
import sbt.Def.Initialize
import sbt.io.Using
import sbtdynver.DynVer
import sbtdynver.DynVerPlugin.autoImport._

import java.nio.file.Files
import java.util.Date
import scala.util.matching.Regex
import scala.collection.JavaConverters._
import scala.collection.immutable.Seq
import scala.sys.env

import com.sandinh.devops.Utils.{
  currentBranch,
  gitHubScmInfo,
  isSnapshotVersion,
  isTag
}

object SdDevOpsPlugin extends AutoPlugin {
  private[this] val impl: ImplTrait = Impl
  import impl._

  override def trigger = allRequirements
  override def requires = requiresImpl

  val scalafmtVersion = "3.0.4"

  object autoImport {
    val sdSetup = taskKey[Unit]("Setup devops stuff")
    val sdQA = taskKey[Unit]("SanDinh QA (Quality Assurance)")
    val sdMmNotify = taskKey[Unit]("Mattermost notify")
    val sdNexusHost = settingKey[String](
      "Your private nexus host, ex repo.example.com. Not used in devops-oss"
    )
  }
  import autoImport._

  override lazy val buildSettings: Seq[Setting[_]] = Seq(
    organization := "com.sandinh",
    homepage := scmInfo.value.map(_.browseUrl),
    dynverTagPrefix := "v",
    dynverSonatypeSnapshots := true,
    scmInfo ~= {
      case Some(info) => Some(info)
      case None       => gitHubScmInfo
    },
  ) ++ buildSettingsImpl

  private val inAny = ScopeFilter(inAnyProject, inAnyConfiguration)

  override lazy val globalSettings: Seq[Setting[_]] = Seq(
    sdSetup := sdSetupTask.value,
    sdQA := {
      // <task>.value macro causing spurious “a pure expression does nothing” warning
      // This `val _ =` is not need if we set `pluginCrossBuild` to a newer sbt version
      val _ = sdQaBaseTask.value
      val __ = dynverAssertVersion.all(ScopeFilter(inAnyProject)).value
      val fmtOk = scalafmtCheck.?.all(inAny).result.value.isSuccess
      val sbtOk = scalafmtSbtCheck.?.all(inAny).result.value.isSuccess
      orBoom(
        fmtOk && sbtOk,
        """Some files aren't formatted properly.
          |You should format code by running: sbt "+scalafmtAll; +scalafmtSbt"
          |RECOMMEND: git commit or stash all changes before formatting.""".stripMargin
      )
    },
    sdMmNotify := sdMmNotifyTask.value,
  ) ++ ciReleaseSettings ++ globalSettingsImpl

  // see CiReleasePlugin.globalSettings
  lazy val ciReleaseSettings: Seq[Setting[_]] = Seq(
    Test / publishArtifact := false,
    publishMavenStyle := true,
    commands += Command.command("ci-release") { state =>
      println(s"Running ci-release.\n  branch=$currentBranch")
      if (!isTag) {
        if (isSnapshotVersion(state)) {
          println(s"No tag push, publishing SNAPSHOT")
          ciReleaseSnapshotCmds ::: state
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
        ciReleaseCmds ::: state
      }
    },
  )

  override lazy val projectSettings: Seq[Setting[_]] =
    dynVerSettings ++ projectSettingsImpl

  /** Those settings are similar to [[sbtdynver.DynVerPlugin.buildSettings]] but:
    * + dynverSonatypeSnapshots is hardcode = true
    * + Don't use dynverVTagPrefix.
    *   If you don't set dynverTagPrefix then logic will be same as set dynverTagPrefix = "v"
    * + Can be used for projectSettings
    *   So, each project can customize version by setting dynverVTagPrefix
    */
  lazy val dynVerSettings: Seq[Setting[_]] = Seq(
    version := dynverGitDescribeOutput.value.sonatypeVersionWithSep(
      (ThisBuild / dynverCurrentDate).value,
      (ThisBuild / dynverSeparator).value
    ),
    isSnapshot := dynverGitDescribeOutput.value.isSnapshot,
    dynverInstance := DynVer(
      Some((ThisBuild / baseDirectory).value),
      (ThisBuild / dynverSeparator).value,
      dynverTagPrefix.or(ThisBuild / dynverTagPrefix).value
    ),
    dynverGitDescribeOutput := dynverInstance.value.getGitDescribeOutput(
      (ThisBuild / dynverCurrentDate).value
    ),
    dynver := dynverInstance.value.sonatypeVersion(new Date),
    dynverCheckVersion := (dynver.value == version.value),
    dynverAssertVersion := orBoom(
      dynverCheckVersion.value,
      s"Project ${name.value} define `version` manually!"
    ),
  )

  private case class Job(name: String, result: String) {
    def emoji: String = result match {
      case "success"   => ":white_check_mark:"
      case "failure"   => ":x:"
      case "cancelled" => ":white_circle:"
      case "skipped" =>
        ":black_right_pointing_double_triangle_with_vertical_bar:"
      case _ => boom("invalid Job result")
    }

    private def publishSuccess = name == "publish" && result == "success"

    def asAttachmentField(version: String): ujson.Obj = ujson.Obj(
      "short" -> true,
      "title" -> name,
      "value" -> (emoji + (if (publishSuccess) " " + version else ""))
    )

    override def toString: String = s"$name: $result"
  }

  private def commitMsg = {
    import sys.process._
    s"git show -s --format=%s ${env("GITHUB_SHA")}".!!.trim
  }

  // https://developers.mattermost.com/integrate/incoming-webhooks/#parameters
  def sdMmNotifyTask: Initialize[Task[Unit]] = Def.task {
    val version = projectID
      .all(ScopeFilter(inAnyProject))
      .value
      .map { m => m.name + ":" + m.revision }
      .mkString(", ")
    val webhook = env.getOrElse(
      "MATTERMOST_WEBHOOK_URL",
      boom("MATTERMOST_WEBHOOK_URL env is not set")
    )
    val runId = env.getOrElse(
      "GITHUB_RUN_ID",
      boom("sdMmNotify task must be run in Github Action")
    )
    val home = s"${env("GITHUB_SERVER_URL")}/${env("GITHUB_REPOSITORY")}"
    val link = s"$home/actions/runs/$runId"
    val jobs = for {
      v <- env.get("SD_MM_NEEDS").toSeq
      (jobName, job) <- ujson.read(v).obj
    } yield Job(jobName, job.obj("result").str)

    val text = env("GITHUB_EVENT_NAME") match {
      case "pull_request" =>
        val payloadFile = file(env("GITHUB_EVENT_PATH"))
        val pr = ujson.read(payloadFile).obj("number").num.toLong
        s"pull request [#$pr]($home/pull/$pr)"
      case _ => s"commit: $commitMsg"
    }
    val attachment = ujson.Obj(
      "fallback" -> jobs.mkString("CI jobs status: ", ", ", ""),
      "author_name" -> env("GITHUB_REPOSITORY"),
      "author_icon" -> "https://chat.ohze.net/api/v4/emoji/tu6nrabuftrk78rm78mapoq7to/image",
      "text" -> s"[CI jobs status]($link) for $text",
      "fields" -> jobs.map(_.asAttachmentField(version)),
    )
    env.get("MATTERMOST_PRETEXT").foreach(attachment("pretext") = _)

    val data = ujson.Obj("attachments" -> ujson.Arr(attachment))

    val urlPattern = "https?://.*".r
    env.get("MATTERMOST_ICON") match {
      case Some(url @ urlPattern()) => data("icon_url") = url
      case Some(emoji)              => data("icon_emoji") = emoji
      case None => data("icon_emoji") = ":electric_plug:" // ":dizzy:"
    }

    for {
      (key, field) <- Seq(
        "MATTERMOST_CHANNEL" -> "channel",
        "MATTERMOST_USERNAME" -> "username",
      )
      v <- env.get(key)
    } data(field) = v

    requests.post(webhook, data = data)
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
    val envBennuoc =
      """env:
        |  NEXUS_USER: ${{ secrets.NEXUS_USER }}
        |  NEXUS_PASS: ${{ secrets.NEXUS_PASS }}""".stripMargin.linesIterator.toList
    def envReleaseBennuoc = envBennuoc ++
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
    def condBennuoc = Seq("if: success()")

    Map(
      "# env: bennuoc" -> (if (isOss) Nil else envBennuoc),
      "# env: ci-release" -> (if (isOss) envReleaseOss else envReleaseBennuoc),
      "# if: publish-condition" -> (if (isOss) condOss else condBennuoc)
    )
  }

  private def setupFiles(baseDir: File, log: Logger): Unit = {
    val baseUrl = "https://raw.githubusercontent.com/ohze/sbt-devops/main"

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
            |Pls set github repo as your git `origin` remote and re-run sbt sdSetup
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
    orBoom(f.isFile, "You should create README.md by running sbt sdSetup")
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
    words.contains("sdQA")
  }

  def validateGithubCI(baseDir: File): Unit = {
    val f = baseDir / ".github" / "workflows" / "sbt-devops.yml"
    orBoom(f.isFile, s"$f: File not found!")
    orBoom(
      IO.readLines(f).exists(isSdQAStep),
      s"$f must define step:\n- run: sbt <optional params> sdQA"
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

  def boom(msg: String) = throw new MessageOnlyException(msg)
  def orBoom(check: => Boolean, msg: String): Unit = if (!check) boom(msg)

  implicit class ResultOps(val r: Result[_]) extends AnyVal {
    def isSuccess: Boolean = r match {
      case Value(_) => true
      case Inc(_)   => false
    }
  }
}