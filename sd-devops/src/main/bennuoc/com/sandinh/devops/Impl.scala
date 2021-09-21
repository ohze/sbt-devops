package com.sandinh.devops

import com.sandinh.devops.SdDevOpsPlugin.env
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
}
