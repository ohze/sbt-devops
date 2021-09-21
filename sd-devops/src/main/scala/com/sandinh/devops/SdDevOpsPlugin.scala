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

import scala.util.{Failure, Success, Try}
import sys.env

object SdDevOpsPlugin extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = JvmPlugin && DynVerPlugin && GitPlugin

  object autoImport {
    val sdQA = taskKey[Unit]("SanDinh QA (Quality Assurance)")
  }
  import autoImport._

  override lazy val buildSettings: Seq[Setting[_]] = Seq(
    dynverSonatypeSnapshots := true,
    scmInfo ~= {
      case Some(info) => Some(info)
      case None       => gitHubScmInfo
    },
  )

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    sdQA := {
      validateScalafmtConf.value
      scalafmtCheckAll.value
    },
  )

  val scalafmtVersion = "3.0.5"

  val validateScalafmtConf: Initialize[Task[Unit]] = Def.task {
    validateScalafmtConf((ThisBuild / baseDirectory).value / ".scalafmt.conf")
  }

  def validateScalafmtConf(f: File): Unit = {
    orBoom(f.isFile, ".scalafmt.conf: File not found!")

    val c = ConfigFactory.parseFile(f)
    orBoom(c.hasPath("version"), ".scalafmt.conf: File not found!")

    orBoom(
      c.getString("version") == scalafmtVersion,
      s".scalafmt.conf: You must update `version` to $scalafmtVersion"
    )
  }

  def orBoom(check: => Boolean, msg: String): Unit = {
    if (! check) throw new MessageOnlyException(".scalafmt.conf: File not found!")
  }

  def releaseTag: String = env.getOrElse("GITHUB_REF", "<unknown>")

  def currentBranch: String = releaseTag

  def isTag: Boolean = env.get("GITHUB_REF").exists(_.startsWith("refs/tags"))

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
    val GitHubGit   = s"git://github.com:$identifier/$identifier(?:\\.git)?".r
    val GitHubSsh   = s"git@github.com:$identifier/$identifier(?:\\.git)?".r
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
