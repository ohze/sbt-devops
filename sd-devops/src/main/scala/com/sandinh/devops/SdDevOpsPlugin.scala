package com.sandinh.devops

import com.typesafe.config.ConfigFactory
import com.typesafe.sbt.GitPlugin
import org.scalafmt.sbt.ScalafmtPlugin.autoImport.scalafmtCheckAll
import sbt._
import sbt.Keys._
import sbt.Def.Initialize
import sbt.plugins.JvmPlugin
import sbtdynver.DynVerPlugin

object SdDevOpsPlugin extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = JvmPlugin && DynVerPlugin && GitPlugin

  val scalafmtVersion = "3.0.4"

  object autoImport {
    val sdQA = taskKey[Unit]("SanDinh QA (Quality Assurance)")
  }
  import autoImport._

  override lazy val buildSettings: Seq[Setting[_]] = Seq(
    organization := "com.sandinh",
    homepage := scmInfo.value.map(_.browseUrl),
  ) ++ Impl.buildSettings

  object env {
    def unapply(key: String): Option[String] = sys.env.get(key)
    def apply(keys: String*): Option[String] = keys.collectFirst {
      case env(v) => v
    }
  }

  override lazy val globalSettings: Seq[Setting[_]] = Seq(
    sdQA := {
      // <task>.value macro causing spurious “a pure expression does nothing” warning
      // This `val _ =` is not need if we set `pluginCrossBuild` to a newer sbt version
      val _ = validates.value
      val __ = scalafmtCheckAll.all(ScopeFilter(inAnyProject)).value
    },
  ) ++ Impl.globalSettings

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
  ) ++ Impl.projectSettings

  lazy val validates: Initialize[Task[Unit]] = Def.task {
    val baseDir = (ThisBuild / baseDirectory).value
    validateScalafmtConf(baseDir)
    validateGithubCI(baseDir)
  }

  private def isSdQAStep(line: String) =
    line.trim.startsWith("- run: sbt ") && line.endsWith(" sdQA")

  def validateGithubCI(baseDir: File): Unit = {
    for {
      name <- Seq("test.yml", "release.yml")
      f = baseDir / ".github" / "workflows" / name
    } orBoom(f.isFile, s"$f: File not found!")

    val testYml = baseDir / ".github" / "workflows" / "test.yml"
    orBoom(
      IO.readLines(testYml).exists(isSdQAStep),
      s"$testYml must define step:\n- run: sbt <optional params> sdQA"
    )
  }

  def validateScalafmtConf(baseDir: File): Unit = {
    val f = baseDir / ".scalafmt.conf"
    orBoom(f.isFile, s"$f: File not found!")

    val c = ConfigFactory.parseFile(f)
    orBoom(c.hasPath("version"), s"$f: `version` config not found!")

    orBoom(
      c.getString("version") == scalafmtVersion,
      s"$f: You must update `version` to $scalafmtVersion"
    )
  }

  def orBoom(check: => Boolean, msg: String): Unit = {
    if (!check) throw new MessageOnlyException(msg)
  }

//  def releaseTag: String = env("GITHUB_REF").getOrElse("<unknown>")
//
//  def currentBranch: String = releaseTag
//
//  def isTag: Boolean = env("GITHUB_REF").exists(_.startsWith("refs/tags"))
}
