package com.sandinh.devops

import com.sandinh.devops.SdDevOpsPlugin.env
import sbt._
import sbt.Keys._
import sbtdynver.DynVerPlugin.autoImport.dynverSonatypeSnapshots

import scala.util.Try

object Impl {
  val nexusRealm = "Sonatype Nexus Repository Manager"
  val bennuoc = "repo.bennuoc.com"
  val bennuocMaven = s"https://$bennuoc/repository/maven"

  lazy val buildSettings: Seq[Setting[_]] = Seq(
    dynverSonatypeSnapshots := true,
    scmInfo ~= {
      case Some(info) => Some(info)
      case None       => gitHubScmInfo
    },
    resolvers += "bennuoc" at s"$bennuocMaven-public",
  )

  lazy val globalSettings: Seq[Setting[_]] = Seq(
    credentials ++= {
      for {
        u <- env("NEXUS_USER", "BENNUOC_USER", "SONATYPE_USERNAME")
        p <- env("NEXUS_PASS", "BENNUOC_PASS", "SONATYPE_PASSWORD")
      } yield Credentials(nexusRealm, bennuoc, u, p)
    },
  )

  lazy val projectSettings: Seq[Setting[_]] = Seq(
    publishMavenStyle := true,
    publishTo := {
      val tpe = if (isSnapshot.value) "snapshots" else "releases"
      Some(tpe at s"$bennuocMaven-$tpe")
    },
  )

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
