package com.sandinh.devops

import sbt.{ScmInfo, url}

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
    import scala.sys.process._
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
}
