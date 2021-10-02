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

    val skipPublish: Seq[Setting[?]] = Seq(
      publish / skip := true,
      publishLocal / skip := true,
    )

    val scalaColCompat: ModuleID =
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.5.0"
  }

  def silencer(m: String): ModuleID =
    "com.github.ghik" % s"silencer-$m" % "1.7.6" cross CrossVersion.full

  override def globalSettings: Seq[Setting[?]] = Seq(
    devopsNexusHost := "repo.bennuoc.com",
  )

  override lazy val buildSettings: Seq[Setting[?]] = Seq(
    organization := "com.sandinh",
  )

  override def projectSettings: Seq[Setting[?]] = Seq(
    scalacOptions ++= sdScalacOptions(scalaVersion.value),
    Compile / scalacOptions ++= fatalWarnings(scalaBinaryVersion.value),
    Test / scalacOptions -= "-Xfatal-warnings",
    Compile / doc / scalacOptions -= "-Xfatal-warnings",
    libraryDependencies ++= silencerDeps(scalaBinaryVersion.value),
  )

  /** @param scalaVersion scala version. Ex 2.11.12, 3.1.0-RC2,..
    * @return default scalacOptions for all sandinh's projects
    * @see [[https://docs.scala-lang.org/scala3/guides/migration/options-lookup.html Compiler Options Lookup Table]]
    * @see [[https://www.scala-lang.org/2021/01/12/configuring-and-suppressing-warnings.html Configuring and suppressing warnings]]
    * @see [[https://github.com/lampepfl/dotty/pull/12857 Support -Wconf and @nowarn in scala3]]
    */
  def sdScalacOptions(scalaVersion: String): Seq[String] = {
    val Some((major, minor)) = CrossVersion.scalaApiVersion(scalaVersion)
    val opts = ListBuffer(  // format: off
      "-encoding", "UTF-8", // format: on
      "-deprecation",
      "-feature",
    )
    if ((major, minor) == (2, 11)) opts += "-Ybackend:GenBCode"
    if (major == 2 && minor < 13) opts += "-target:jvm-1.8"
    // scala 2.11.12 still not support -Xsource:3
    if (major == 2 && minor > 11) opts += "-Xsource:3"
    opts.result()
  }

  // https://github.com/ghik/silencer
  def silencerDeps(scalaBinVersion: String): Seq[ModuleID] = {
    if (scalaBinVersion != "2.11") Nil
    else Seq(compilerPlugin(silencer("plugin")), silencer("lib") % Provided)
  }

  def fatalWarnings(scalaBinVersion: String): Seq[String] =
    (scalaBinVersion match {
      // TODO enable -Xfatal-warnings when this is RELEASED in scala3:
      // https://github.com/lampepfl/dotty/pull/12857
      case "3" => Nil
      case _   => Seq("-Xfatal-warnings")
    })
}
