package com.sandinh.devops

import scala.collection.immutable.Seq
import scala.sys.env
import sbt._
import sbt.Keys._
import sbt.Def.Initialize
import com.typesafe.sbt.GitPlugin
import sbtdynver.DynVerPlugin
import DevopsPlugin.autoImport.sdNexusHost

object Impl extends ImplTrait {
  val isOss = false

  def requiresImpl: Plugins = DynVerPlugin && GitPlugin

  val nexusRealm = "Sonatype Nexus Repository Manager"

  def nexusRepo(tpe: String): Initialize[String] = sdNexusHost { host =>
    s"https://$host/repository/maven"
  }

  lazy val buildSettingsImpl: Seq[Setting[_]] = Seq(
    resolvers += "sdNexus" at nexusRepo("public").value,
  )

  lazy val globalSettingsImpl: Seq[Setting[_]] = Seq(
    credentials ++= {
      for {
        u <- env.get("NEXUS_USER")
        p <- env.get("NEXUS_PASS")
      } yield Credentials(nexusRealm, sdNexusHost.value, u, p)
    },
  )

  lazy val projectSettingsImpl: Seq[Setting[_]] = Seq(
    publishTo := {
      val tpe = if (isSnapshot.value) "snapshots" else "releases"
      Some(tpe at nexusRepo(tpe).value)
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
