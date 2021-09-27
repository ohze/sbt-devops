package com.sandinh.devops

import scala.collection.immutable.Seq
import scala.sys.env
import sbt._
import sbt.Keys._
import com.typesafe.sbt.GitPlugin
import sbtdynver.DynVerPlugin
import DevopsPlugin.autoImport.devopsNexusHost

object Impl extends ImplTrait {
  val isOss = false

  def requiresImpl: Plugins = DynVerPlugin && GitPlugin

  val nexusRealm = "Sonatype Nexus Repository Manager"

  private def repo(host: String, tpe: String) =
    "nexus" at s"https://$host/repository/maven$tpe"

  lazy val buildSettingsImpl: Seq[Setting[_]] = Seq(
    resolvers += repo(devopsNexusHost.value, "public"),
  )

  lazy val globalSettingsImpl: Seq[Setting[_]] = Seq(
    credentials ++= {
      for {
        u <- env.get("NEXUS_USER")
        p <- env.get("NEXUS_PASS")
      } yield Credentials(nexusRealm, devopsNexusHost.value, u, p)
    },
  )

  lazy val projectSettingsImpl: Seq[Setting[_]] = Seq(
    publishTo := {
      val tpe = if (isSnapshot.value) "snapshots" else "releases"
      Some(repo(devopsNexusHost.value, tpe))
    },
  )

  private[devops] val ciReleaseSnapshotCmds = List(
    env.getOrElse("CI_SNAPSHOT_RELEASE", "+publish"),
  )

  private[devops] val ciReleaseCmds = List(
    env.getOrElse("CI_CLEAN", "clean"),
    env.getOrElse("CI_RELEASE", "+publish"),
  )
}
