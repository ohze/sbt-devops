package com.sandinh.devops

import scala.collection.immutable.Seq
import scala.sys.env
import sbt._
import sbt.Keys._
import com.typesafe.sbt.GitPlugin
import sbt.plugins.JvmPlugin
import sbtdynver.DynVerPlugin

object Impl extends ImplTrait {
  val isOss = false

  def requiresImpl: Plugins = JvmPlugin && DynVerPlugin && GitPlugin

  val nexusRealm = "Sonatype Nexus Repository Manager"
  val bennuoc = "repo.bennuoc.com"
  val bennuocMaven = s"https://$bennuoc/repository/maven"

  lazy val buildSettingsImpl: Seq[Setting[_]] = Seq(
    resolvers += "bennuoc" at s"$bennuocMaven-public",
  )

  lazy val globalSettingsImpl: Seq[Setting[_]] = Seq(
    credentials ++= {
      for {
        u <- env.get("NEXUS_USER")
        p <- env.get("NEXUS_PASS")
      } yield Credentials(nexusRealm, bennuoc, u, p)
    },
  )

  lazy val projectSettingsImpl: Seq[Setting[_]] = Seq(
    publishTo := {
      val tpe = if (isSnapshot.value) "snapshots" else "releases"
      Some(tpe at s"$bennuocMaven-$tpe")
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
