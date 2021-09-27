package com.sandinh.sbtsd

import sbt.{Def, Setting, _}
import sbt.Keys._
import CrossVersion.for3Use2_13
import Def.Initialize
import com.sandinh.devops.DevopsPlugin
import com.sandinh.devops.DevopsPlugin.autoImport.sdNexusHost

import scala.collection.immutable.Seq
import scala.collection.{Seq => CSeq}

object SdPlugin extends AutoPlugin {
  override def trigger = allRequirements
  override def requires: Plugins = DevopsPlugin // && ProjectMatrixPlugin

  object autoImport {
    val (scala211, scala212, scala213, scala3) =
      ("2.11.12", "2.12.15", "2.13.6", "3.0.2")

    val skipPublish: Seq[Setting[_]] = SdPlugin.skipPublish

    lazy val akkaVersion = settingKey[String]("akkaVersion")

    def akka(modules: String*): Initialize[CSeq[ModuleID]] = akkaVersion { v =>
      modules.map { m =>
        "com.typesafe.akka" %% s"akka-$m" % v cross for3Use2_13
      }
    }
  }

  override def globalSettings: Seq[Setting[_]] = Seq(
    sdNexusHost := "repo.bennuoc.com",
  )

  override lazy val buildSettings: Seq[Setting[_]] = Seq(
    organization := "com.sandinh",
  )

  override def projectSettings: Seq[Setting[_]] = Seq(
    scalacSetting,
  )

  val skipPublish: Seq[Setting[_]] = Seq(
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
