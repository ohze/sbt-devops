package com.sandinh.devops

import scala.collection.immutable.Seq
import scala.sys.env
import sbt.*
import sbt.Keys.*
import com.github.sbt.git.GitPlugin
import sbtdynver.DynVerPlugin
import DevopsPlugin.autoImport.devopsNexusHost

object Impl extends ImplTrait {
  val isOss = false

  def requiresImpl: Plugins = DynVerPlugin && GitPlugin

  val nexusRealm = "Sonatype Nexus Repository Manager"

  private def repo(host: String, tpe: String) =
    s"nexus-$tpe" at s"https://$host/repository/maven-$tpe"

  lazy val buildSettingsImpl: Seq[Setting[?]] = Seq(
    resolvers ++= devopsNexusHost.?.value.map(repo(_, "public")),
  )

  lazy val globalSettingsImpl: Seq[Setting[?]] = Seq(
    credentials ++= {
      for {
        u <- env.get("NEXUS_USER")
        p <- env.get("NEXUS_PASS")
        host <- devopsNexusHost.?.value
      } yield Credentials(nexusRealm, host, u, p)
    },
  )

  lazy val projectSettingsImpl: Seq[Setting[?]] = Seq(
    publishTo := {
      val tpe = if (isSnapshot.value) "snapshots" else "releases"
      devopsNexusHost.?.value.map(repo(_, tpe))
    },
  )

  private[devops] val ciReleaseSnapshotCmds = List(
    env.getOrElse("CI_SNAPSHOT_RELEASE", "+publish"),
  )

  private[devops] val ciReleaseCmds = List(
    "versionCheck",
    env.getOrElse("CI_CLEAN", "clean"),
    env.getOrElse("CI_RELEASE", "+publish"),
  )
}
