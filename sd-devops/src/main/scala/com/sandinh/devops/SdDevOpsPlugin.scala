package com.sandinh.devops

import com.typesafe.config.ConfigFactory
import com.typesafe.sbt.GitPlugin
import org.scalafmt.sbt.ScalafmtPlugin.autoImport.scalafmtCheckAll
import sbt._
import sbt.Keys._
import sbt.Def.Initialize
import sbt.plugins.JvmPlugin
import sbtdynver.DynVerPlugin
import sbtdynver.DynVerPlugin.autoImport.dynverSonatypeSnapshots

import scala.util.Try

object SdDevOpsPlugin extends AutoPlugin with Info {
  override def trigger = allRequirements
  override def requires = JvmPlugin && DynVerPlugin && GitPlugin

  val scalafmtVersion = "3.0.4"

  val nexusRealm = "Sonatype Nexus Repository Manager"
  val bennuoc = "repo.bennuoc.com"
  val bennuocMaven = s"https://$bennuoc/repository/maven"

  object autoImport {
    val sdQA = taskKey[Unit]("SanDinh QA (Quality Assurance)")
  }
  import autoImport._

  override lazy val buildSettings: Seq[Setting[_]] = Seq(
    organization := "com.sandinh",
    homepage := scmInfo.value.map(_.browseUrl),
  ) ++ (if (isOss) ossBuildSettings else bennuocBuildSetting)

  private lazy val ossBuildSettings = Seq(
    licenses := List(
      "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
    ),
  )

  private lazy val bennuocBuildSetting = Seq(
    dynverSonatypeSnapshots := true,
    scmInfo ~= {
      case Some(info) => Some(info)
      case None       => gitHubScmInfo
    },
    resolvers += "bennuoc" at s"$bennuocMaven-public",
  )

  object env {
    def unapply(key: String): Option[String] = sys.env.get(key)
    def apply(keys: String*): Option[String] = keys.collectFirst {
      case env(v) => v
    }
  }

  override lazy val globalSettings: Seq[Setting[_]] = Seq(
    sdQA := {
      validates.value
      val _ = scalafmtCheckAll.all(ScopeFilter(inAnyProject)).value
    },
  ) ++ (if (isOss) Nil else bennuocGlobalSettings)

  lazy val bennuocGlobalSettings: Seq[Setting[_]] = Seq(
//    credentials ++= {
//      for {
//        u <- env("NEXUS_USER", "BENNUOC_USER", "SONATYPE_USERNAME")
//        p <- env("NEXUS_PASS", "BENNUOC_PASS", "SONATYPE_PASSWORD")
//      } yield Credentials(nexusRealm, bennuoc, u, p)
//    },
  )

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
  ) ++ (if (isOss) Nil else bennuocSettings)

  private lazy val bennuocSettings = Seq(
    publishMavenStyle := true,
    publishTo := {
      val tpe = if (isSnapshot.value) "snapshots" else "releases"
      Some(tpe at s"$bennuocMaven-$tpe")
    },
  )

  lazy val validates: Initialize[Task[Unit]] = Def.task {
    val baseDir = (ThisBuild / baseDirectory).value
    validateScalafmtConf(baseDir)
    validateGithubCI(baseDir)
  }

  private def isSdQAStep(line: String) =
    line.trim.startsWith("- run: sbt ") && line.endsWith(" sdQA")

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

  def gitHubScmInfo(user: String, repo: String) =
    ScmInfo(
      url(s"https://github.com/$user/$repo"),
      s"scm:git:https://github.com/$user/$repo.git",
      Some(s"scm:git:git@github.com:$user/$repo.git")
    )

  def gitHubScmInfo: Option[ScmInfo] = {
    import scala.sys.process._
    val identifier = """([^\/]+?)"""
    val GitHubHttps = s"https://github.com/$identifier/$identifier(?:\\.git)?".r
    val GitHubGit = s"git://github.com:$identifier/$identifier(?:\\.git)?".r
    val GitHubSsh = s"git@github.com:$identifier/$identifier(?:\\.git)?".r
    Try {
      "git ls-remote --get-url origin".!!.trim()
    }.toOption.flatMap {
      case GitHubHttps(user, repo) => Some(gitHubScmInfo(user, repo))
      case GitHubGit(user, repo)   => Some(gitHubScmInfo(user, repo))
      case GitHubSsh(user, repo)   => Some(gitHubScmInfo(user, repo))
      case _                       => None
    }
  }
}
