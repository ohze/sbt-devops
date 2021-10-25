package com.sandinh.sbtsd

import sbt.*
import sbt.Keys.*
import sbt.Def.Initialize
import com.sandinh.devops.DevopsPlugin
import DevopsPlugin.autoImport.devopsNexusHost
import scala.collection.immutable.Seq
import scala.collection.Seq as CSeq
import scala.collection.mutable.ListBuffer
import scala.sys.env

object SdPlugin extends AutoPlugin {
  override def trigger = allRequirements
  override def requires: Plugins = DevopsPlugin
  val isOss: Boolean = DevopsPlugin.isOss

  object autoImport {
    val (scala211, scala212, scala213, scala3) =
      ("2.11.12", "2.12.15", "2.13.6", "3.1.0")

    val skipPublish: Seq[Setting[?]] = Seq(
      publish / skip := true,
      publishLocal / skip := true,
    )

    val scalaColCompat: ModuleID =
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.5.0"

    /** We should use {{{
      * libraryDependencies ++= scalatest("-funsuite", "-mustmatchers")
      * }}}
      */
    def scalatest(modules: String*): CSeq[ModuleID] = modules.map { m =>
      "org.scalatest" %% s"scalatest$m" % "3.2.10" % Test
    }

    /** Usage example {{{
      * libraryDependencies ++= specs2("-core")
      * }}}
      */
    def specs2(modules: String*): Initialize[CSeq[ModuleID]] = Def.setting {
      val specs2Version = scalaBinaryVersion.value match {
        case "2.11" => "4.10.6"
        case "3"    => "5.0.0-RC-11"
        case _      => "4.12.12"
      }
      modules.map { m => "org.specs2" %% s"specs2$m" % specs2Version % Test }
    }

    /** Test workaround for java 16+ by set `fork := true` and add javaOptions `--add-opens xxx`
      * @param open Default opens java.base/java.lang for java 16+ to workaround errors while test such as:
      * {{{
      *   InaccessibleObjectException: Unable to make protected final java.lang.Class
      *   java.lang.ClassLoader.defineClass(java.lang.String,byte[],int,int,java.security.ProtectionDomain)
      *   throws java.lang.ClassFormatError accessible:
      *   module java.base does not "opens java.lang" to unnamed module @42a6eabd (ReflectUtils.java:61)
      * }}}
      */
    def addOpensForTest(
        open: String = "java.base/java.lang=ALL-UNNAMED"
    ): Seq[Setting[?]] = addOpensForTest(Seq(open))

    /** @see other overloaded `addOpensForTest` def */
    def addOpensForTest(opens: Seq[String]): Seq[Setting[?]] = Seq(
      Test / fork := javaVersion >= 16,
      Test / javaOptions ++= {
        if (javaVersion < 16) Nil
        else opens.flatMap(s => Seq("--add-opens", s))
      }
    )

    val sdDockerServer = "r.bennuoc.com"
    def dockerLogin: Initialize[Task[Unit]] = {
      if (isOss)
        dockerLoginTask(env("DOCKER_USERNAME"), env("DOCKER_PASSWORD"), "")
      else dockerLoginTask(env("NEXUS_USER"), env("NEXUS_PASS"), sdDockerServer)
    }
  }
  import autoImport.sdDockerServer

  def dockerLoginTask(
      username: => String,
      password: => String,
      server: String = sdDockerServer
  ): Initialize[Task[Unit]] = Def.task {
    import scala.sys.process.*
    val log = streams.value.log
    log.info("docker login ...")
    val login =
      s"docker login $server -u $username --password-stdin"
    log.info((s"echo $password" #| login).!!)
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
    scalaBinVersion match {
      // TODO enable -Xfatal-warnings when this is RELEASED in scala3:
      // https://github.com/lampepfl/dotty/pull/12857
      case "3" => Nil
      case _   => Seq("-Xfatal-warnings")
    }

  /** @throws java.lang.NumberFormatException  If the string does not contain a parsable `Int`. */
  def javaVersion: Int = scala.sys
    .props("java.specification.version")
    .split('.')
    .dropWhile(_ == "1")
    .head
    .toInt
}
