package com.sandinh.sbtsd

import sbt.*
import sbt.Keys.*
import com.sandinh.devops.DevopsPlugin
import DevopsPlugin.autoImport.devopsNexusHost

import scala.collection.immutable.Seq

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
    scalacSetting,
  )

  val skipPublish: Seq[Setting[?]] = Seq(
    publish / skip := true,
    publishLocal / skip := true,
  )

  lazy val scalacSetting = scalacOptions ++=
    Seq("-encoding", "UTF-8", "-deprecation", "-feature") ++
      (CrossVersion.scalaApiVersion(scalaVersion.value) match {
        case Some((2, 11)) => Seq("-Ybackend:GenBCode", "-target:jvm-1.8")
        case Some((2, 12)) => Seq("-target:jvm-1.8")
        case _             => Nil
      })
}
