package com.sandinh.sbtsd

import sbt.*
import sbt.Keys.*
import com.sandinh.devops.DevopsPlugin
import DevopsPlugin.autoImport.devopsNexusHost

import scala.collection.immutable.Seq
import scala.collection.mutable.ListBuffer

object SdPlugin extends AutoPlugin {
  override def trigger = allRequirements
  override def requires: Plugins = DevopsPlugin

  object autoImport {
    val (scala211, scala212, scala213, scala3) =
      ("2.11.12", "2.12.15", "2.13.6", "3.0.2")

    val skipPublish: Seq[Setting[?]] = SdPlugin.skipPublish
  }

  override def globalSettings: Seq[Setting[?]] = Seq(
    devopsNexusHost := "repo.bennuoc.com",
  )

  override lazy val buildSettings: Seq[Setting[?]] = Seq(
    organization := "com.sandinh",
  )

  override def projectSettings: Seq[Setting[?]] = Seq(
    scalacOptions ++= sdScalacOptions(scalaVersion.value)
  )

  val skipPublish: Seq[Setting[?]] = Seq(
    publish / skip := true,
    publishLocal / skip := true,
  )

  /** @param scalaVersion scala version. Ex 2.11.12, 3.1.0-RC2,..
    * @return default scalacOptions for all sandinh's projects
    * @see [[https://docs.scala-lang.org/scala3/guides/migration/options-lookup.html Compiler Options Lookup Table]]
    */
  def sdScalacOptions(scalaVersion: String): Seq[String] = {
    val Some((major, minor)) = CrossVersion.scalaApiVersion(scalaVersion)
    val opts = ListBuffer(  // format: off
      "-encoding", "UTF-8", // format: on
      "-deprecation",
      "-feature",
      "-Xfatal-warnings",
    )
    if ((major, minor) == (2, 11)) opts += "-Ybackend:GenBCode"
    if (major == 2 && minor < 13) opts += "-target:jvm-1.8"
    if (major == 2) opts ++= Seq("-Xsource:3")
    opts.result()
  }
}
