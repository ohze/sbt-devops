package com.sandinh.devops

import scala.collection.immutable.Seq
import scala.sys.env
import sbt._
import sbt.Keys._
import sbtdynver.DynVerPlugin.autoImport.dynverSonatypeSnapshots
import Utils.gitHubScmInfo

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
        u <- env.get("NEXUS_USER")
        p <- env.get("NEXUS_PASS")
      } yield Credentials(nexusRealm, bennuoc, u, p)
    },
    // see CiReleasePlugin.globalSettings
    commands += Command.command("ci-release") { state =>
      println(s"Running ci-release.\n  branch=$currentBranch")
      if (!isTag) {
        if (isSnapshotVersion(state)) {
          println(s"No tag push, publishing SNAPSHOT")
          env.getOrElse("CI_SNAPSHOT_RELEASE", "+publish") :: state
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
        env.getOrElse("CI_CLEAN", "clean") ::
          env.getOrElse("CI_RELEASE", "+publish") ::
          state
      }
    },
  )

  lazy val projectSettings: Seq[Setting[_]] = Seq(
    publishMavenStyle := true,
    publishTo := {
      val tpe = if (isSnapshot.value) "snapshots" else "releases"
      Some(tpe at s"$bennuocMaven-$tpe")
    },
  )

  def currentBranch: String = env.getOrElse("GITHUB_REF", "<unknown>")

  def isTag: Boolean = env.get("GITHUB_REF").exists(_.startsWith("refs/tags"))

  def isSnapshotVersion(state: State): Boolean = {
    version.in(ThisBuild).get(Project.extract(state).structure.data) match {
      case Some(v) => v.endsWith("-SNAPSHOT")
      case None    => throw new NoSuchFieldError("version")
    }
  }

  val isOss = false
}
