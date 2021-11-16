package com.sandinh.sdsbt

import sbt.*
import sbt.Keys.*
import sbt.Def.Initialize
import sbt.VirtualAxis.jvm
import sbt.internal.ProjectMatrix
import sbt.plugins.MiniDependencyTreeKeys.{asString, dependencyTree}
import sbtprojectmatrix.ProjectMatrixPlugin

import scala.language.implicitConversions
import scala.util.matching.Regex

object SdMatrixPlugin extends AutoPlugin {
  override def trigger = allRequirements
  override def requires: Plugins = ProjectMatrixPlugin

  object autoImport {
    lazy val checkAkkaVersioning = taskKey[Unit](
      "https://doc.akka.io/docs/akka/current/common/binary-compatibility-rules.html#mixed-versioning-is-not-allowed"
    )

    def akkaVersioningSettings(overrides: String*): Seq[Setting[?]] = Seq(
      dependencyOverrides ++= overrides.map { m =>
        "com.typesafe.akka" %% m % akkaAxis.value.version
      },
      checkAkkaVersioning := {
        val av = akkaAxis.value.version
        val sv = Regex.quote(scalaBinaryVersion.value)
        // ex: "  | +-com.typesafe.akka:akka-actor_2.13:2.6.16 [S]"
        val LinePattern =
          raw"[ |+\-]+com\.typesafe\.akka:(.+)_$sv:([0-9.]+)(?: \[S])?".r
        val inconsistentModules =
          (Runtime / dependencyTree / asString).value.linesIterator
            .filter(_.contains("com.typesafe.akka:"))
            .collect { case LinePattern(module, v) if v != av => module -> v }
            .toMap
            .map { case (m, v) => s"$m:$v" }
        if (inconsistentModules.nonEmpty)
          throw new MessageOnlyException(
            s"""Mixed akka versions is not allowed.
               |Please update dependencyOverrides for the following akka modules to version $av:
               |${inconsistentModules.mkString("\n")}
               |""".stripMargin
          )
      },
    )

    lazy val configAxis = settingKey[LibAxis]("Current typesafe config LibAxis")

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

    val Seq(config14, config13) = LibAxis("config", Seq("1.4.1", "1.3.4"))

    val Seq(akka26, akka25) = LibAxis("akka", Seq("2.6.16", "2.5.32"))

    val Seq(play28, play27, play26) =
      LibAxis("play", Seq("2.8.8", "2.7.9", "2.6.25"))

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

    def configFromAkka = configAxis := (akkaAxis.value match {
      case `akka25` => config13
      case `akka26` => config14
      case lib      => sys.error(s"Not support $lib")
    })

    def akkaFromPlay = akkaAxis := (playAxis.value match {
      case `play26` | `play27` => akka25
      case `play28`            => akka26
      case lib                 => sys.error(s"Not support $lib")
    })

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

      def configAxis(
          lib: LibAxis,
          scalaVersions: Seq[String],
          process: Project => Project = identity,
      ): ProjectMatrix =
        libAxis(
          lib,
          scalaVersions,
          p => process(p.settings(autoImport.configAxis := lib))
        )

      def akkaAxis(
          lib: LibAxis,
          scalaVersions: Seq[String],
          process: Project => Project = identity,
      ): ProjectMatrix =
        libAxis(
          lib,
          scalaVersions,
          p => process(p.settings(autoImport.akkaAxis := lib, configFromAkka))
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
                akkaFromPlay,
                configFromAkka,
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
