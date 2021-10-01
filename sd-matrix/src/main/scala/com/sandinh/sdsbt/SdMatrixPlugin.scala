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
    lazy val akkaVersion = settingKey[String]("akkaVersion")
    lazy val playVersion = settingKey[String]("playVersion")

    val (akka25, akka26) = (LibAxis("2.5.32"), LibAxis("2.6.16", ""))

    val (play26, play27, play28) =
      (LibAxis("2.6.25"), LibAxis("2.7.9"), LibAxis("2.8.8", ""))

    // TODO remove `for3Use2_13` when this issue is fixed: https://github.com/akka/akka/issues/30243
    def akka(modules: NameAnConfig*): Initialize[Seq[ModuleID]] = akkaVersion {
      v =>
        modules.map { case NameAnConfig(m, c) =>
          "com.typesafe.akka" %% s"akka-$m" % v withConfigurations c cross CrossVersion.for3Use2_13
        }
    }

    def play(modules: NameAnConfig*): Initialize[Seq[ModuleID]] = playVersion {
      v =>
        modules.map {
          case NameAnConfig("" | "play", c) =>
            "com.typesafe.play" %% "play" % v withConfigurations c
          case NameAnConfig(m @ "json", _) =>
            sys.error(s"play-$m don't have save version as play")
          case NameAnConfig(m, c) =>
            "com.typesafe.play" %% s"play-$m" % v withConfigurations c
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
      ): ProjectMatrix = p
        .customRow(
          scalaVersions,
          axisValues = Seq(lib, jvm),
          p =>
            process(
              p.settings(
                moduleName := moduleName.value + lib.suffix,
                akkaVersion := lib.version,
              )
            )
        )

      def playAxis(
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
                playVersion := lib.version,
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
