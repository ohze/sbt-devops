package com.sandinh.sdsbt

import sbt.*
import sbt.Keys.*
import sbt.Def.Initialize
import sbt.VirtualAxis.jvm
import sbt.internal.ProjectMatrix
import sbtprojectmatrix.ProjectMatrixPlugin
import scala.language.implicitConversions

object SdMatrixPlugin extends AutoPlugin {
  override def trigger = allRequirements
  override def requires: Plugins = ProjectMatrixPlugin

  object autoImport {

    /** Usage example: {{{
      *   libraryDependencies ++= Seq(
      *     "com.sandinh" %% s"mqtt-proto${akkaAxis.value.suffix}" % "1.10.1",
      *     "com.typesafe.akka" %% "akka-actor" % akkaAxis.value.version cross CrossVersion.for3Use2_13,
      *   )
      *   // for multiple akka modules, you should use `akka` method:
      *   libraryDependencies ++= akka("actor", "testkit" -> Test).value
      * }}}
      */
    lazy val akkaAxis = settingKey[LibAxis]("Current akka LibAxis")

    /** Usage example: {{{
      *   libraryDependencies ++= Seq(
      *     "com.sandinh" %% "couchbase-play${playAxis.value.suffix}" % "9.1.0",
      *     "com.typesafe.play" %% "play-cache" % playAxis.value.version,
      *   )
      *   // for multiple play modules, you should use `play` method:
      *   libraryDependencies ++= play("play", "cache", "jdbc" -> Test).value
      * }}}
      */
    lazy val playAxis = settingKey[LibAxis]("Current play LibAxis")

    val (akka25, akka26) = (LibAxis("2.5.32"), LibAxis("2.6.16", ""))

    val (play26, play27, play28) =
      (LibAxis("2.6.25"), LibAxis("2.7.9"), LibAxis("2.8.8", ""))

    /** Usage example: {{{
      *   libraryDependencies ++= akka("actor", "testkit" -> Test).value
      *   // same as:
      *   libraryDependencies ++= Seq(
      *     "com.typesafe.akka" %% "akka-actor" % akkaAxis.value.version cross CrossVersion.for3Use2_13,
      *     "com.typesafe.akka" %% "akka-testkit" % akkaAxis.value.version % Test cross CrossVersion.for3Use2_13,
      *   )
      * }}}
      */
    def akka(modules: NameAnConfig*): Initialize[Seq[ModuleID]] = akkaAxis {
      a =>
        modules.map { case NameAnConfig(m, c) =>
          // TODO remove `for3Use2_13` when this issue is fixed: https://github.com/akka/akka/issues/30243
          "com.typesafe.akka" %% s"akka-$m" % a.version withConfigurations c cross CrossVersion.for3Use2_13
        }
    }

    /** Usage example: {{{
      *   libraryDependencies ++= play("play", "cache", "jdbc" -> Test).value
      *   // same as:
      *   libraryDependencies ++= Seq(
      *     "com.typesafe.play" %% "play" % playAxis.value.version,
      *     "com.typesafe.play" %% "play-cache" % playAxis.value.version,
      *     "com.typesafe.play" %% "play-jdbc" % playAxis.value.version % Test,
      *   )
      * }}}
      */
    def play(modules: NameAnConfig*): Initialize[Seq[ModuleID]] = playAxis {
      p =>
        modules.map {
          case NameAnConfig("" | "play", c) =>
            "com.typesafe.play" %% "play" % p.version withConfigurations c
          case NameAnConfig(m @ "json", _) =>
            sys.error(s"play-$m don't have save version as play")
          case NameAnConfig(m, c) =>
            "com.typesafe.play" %% s"play-$m" % p.version withConfigurations c
        }
    }

    implicit class ProjectMatrixOps(val p: ProjectMatrix) extends AnyVal {
      def libAxis(
          lib: LibAxis,
          scalaVersions: Seq[String],
          process: Project => Project = identity,
      ): ProjectMatrix = p
        .customRow(
          scalaVersions,
          axisValues = Seq(lib, jvm),
          p =>
            process(
              p.settings(
                moduleName := moduleName.value + lib.suffix,
              )
            )
        )

      def akkaAxis(
          lib: LibAxis,
          scalaVersions: Seq[String],
          process: Project => Project = identity,
      ): ProjectMatrix =
        libAxis(
          lib,
          scalaVersions,
          p => process(p.settings(autoImport.akkaAxis := lib))
        )

      def playAxis(
          lib: LibAxis,
          scalaVersions: Seq[String],
          process: Project => Project = identity,
      ): ProjectMatrix =
        libAxis(
          lib,
          scalaVersions,
          p =>
            process(
              p.settings(
                autoImport.playAxis := lib,
                autoImport.akkaAxis := (lib match {
                  case `play26` | `play27` => akka25
                  case `play28`            => akka26
                  case _                   => sys.error(s"Not support $lib")
                }),
              )
            )
        )
    }
  }
}

private case class NameAnConfig(name: String, configuration: Option[String])
private object NameAnConfig {
  implicit def from(name: String): NameAnConfig = NameAnConfig(name, None)

  implicit def from(nameAndConfig: (String, String)): NameAnConfig =
    NameAnConfig(nameAndConfig._1, Some(nameAndConfig._2))

  implicit def fromConfig(
      nameAndConfig: (String, Configuration)
  ): NameAnConfig =
    NameAnConfig(nameAndConfig._1, Some(nameAndConfig._2.name))
}
