package com.sandinh.devops

import sbt.*
import sbt.Keys.version
import scala.collection.immutable.Seq
import scala.sys.env
import scala.sys.process.*
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

  def isTag: Boolean = env.get("GITHUB_REF").exists(_.startsWith("refs/tags"))

  def isSnapshotVersion(state: State): Boolean = {
    (ThisBuild / version).get(Project.extract(state).structure.data) match {
      case Some(v) => v.endsWith("-SNAPSHOT")
      case None    => throw new NoSuchFieldError("version")
    }
  }

  def boom(msg: String) = throw new MessageOnlyException(msg)
  def orBoom(check: => Boolean, msg: String): Unit = if (!check) boom(msg)

  private[devops] def envKeys(suffix: String): Seq[String] =
    Seq("MATTERMOST_", "SLACK_", "DEVOPS_").map(_ + suffix)

  private[devops] implicit class EnvOps(val m: Map[String, String])
      extends AnyVal {
    def any(keySuffix: String): Option[String] =
      envKeys(keySuffix).flatMap(m.get).headOption
  }

  implicit class ResultOps(val r: Result[?]) extends AnyVal {
    def isSuccess: Boolean = r match {
      case Value(_) => true
      case Inc(_)   => false
    }
  }
}
