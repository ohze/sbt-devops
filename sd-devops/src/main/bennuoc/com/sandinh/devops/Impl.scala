package com.sandinh.devops

import sys.env
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
        println(s"No tag push, publishing SNAPSHOT")
        "version" ::
          env.getOrElse("CI_SNAPSHOT_RELEASE", "+publish") ::
          state
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

  private[devops] val releaseYml = "sd-devops/src/release.yml"
}
