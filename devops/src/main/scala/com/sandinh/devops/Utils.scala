package com.sandinh.devops

import sbt.Keys.version
import sbt.{Project, ScmInfo, State, ThisBuild, url}
import scala.sys.env
import scala.sys.process._
import scala.util.Try

object Utils {
  def gitHubScmInfo(user: String, repo: String) =
    ScmInfo(
      url(s"https://github.com/$user/$repo"),
      s"scm:git:https://github.com/$user/$repo.git",
      Some(s"scm:git:git@github.com:$user/$repo.git")
    )

  def gitHubScmInfo: Option[ScmInfo] =
    gitHubInfo.map { case (user, repo) => gitHubScmInfo(user, repo) }

  def gitHubInfo: Option[(String, String)] = {
    val identifier = """([^\/]+?)"""
    val GitHubHttps = s"https://github.com/$identifier/$identifier(?:\\.git)?".r
    val GitHubGit = s"git://github.com:$identifier/$identifier(?:\\.git)?".r
    val GitHubSsh = s"git@github.com:$identifier/$identifier(?:\\.git)?".r
    Try {
      "git ls-remote --get-url origin".!!.trim()
    }.toOption.flatMap {
      case GitHubHttps(user, repo) => Some(user -> repo)
      case GitHubGit(user, repo)   => Some(user -> repo)
      case GitHubSsh(user, repo)   => Some(user -> repo)
      case _                       => None
    }
  }

  def currentBranch: Try[String] = Try {
    "git rev-parse --abbrev-ref HEAD".!!.trim()
  }

  def isTag: Boolean = env.get("GITHUB_REF").exists(_.startsWith("refs/tags"))

  def isSnapshotVersion(state: State): Boolean = {
    (ThisBuild / version).get(Project.extract(state).structure.data) match {
      case Some(v) => v.endsWith("-SNAPSHOT")
      case None    => throw new NoSuchFieldError("version")
    }
  }
}
