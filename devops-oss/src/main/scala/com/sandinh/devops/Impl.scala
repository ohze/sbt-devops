package com.sandinh.devops

import com.jsuereth.sbtpgp.SbtPgp
import com.typesafe.sbt.GitPlugin

import scala.collection.immutable.Seq
import sbt.*
import sbt.Keys.*
import sbtdynver.DynVerPlugin
import xerial.sbt.Sonatype
import xerial.sbt.Sonatype.autoImport.sonatypePublishToBundle

import scala.sys.env

object Impl extends ImplTrait {
  val isOss = true

  def requiresImpl: Plugins = SbtPgp && DynVerPlugin && GitPlugin && Sonatype

  lazy val buildSettingsImpl: Seq[Setting[?]] = Seq(
    licenses := List(
      "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
    ),
  )

  lazy val globalSettingsImpl: Seq[Setting[?]] = Nil

  lazy val projectSettingsImpl: Seq[Setting[?]] = Seq(
    publishTo := sonatypePublishToBundle.value,
  )

  private lazy val reloadKeyFiles: List[String] = {
    setupGpg()
    List(
      "set pgpSecretRing := pgpSecretRing.value",
      "set pgpPublicRing := pgpPublicRing.value",
    )
  }

  private[devops] lazy val ciReleaseSnapshotCmds = reloadKeyFiles ++ Seq(
    env.getOrElse("CI_SNAPSHOT_RELEASE", "+publish"),
  )

  private[devops] lazy val ciReleaseCmds = reloadKeyFiles ++ Seq(
    "versionCheck",
    sys.env.getOrElse("CI_CLEAN", "; clean ; sonatypeBundleClean"),
    sys.env.getOrElse("CI_RELEASE", "+publishSigned"),
    sys.env.getOrElse("CI_SONATYPE_RELEASE", "sonatypeBundleRelease"),
  )

  def setupGpg(): Unit = {
    import scala.sys.process.*

    val versionLine = List("gpg", "--version").!!.linesIterator.toList.head
    println(versionLine)
    val TaggedVersion = """(\d{1,14})([\.\d{1,14}]*)((?:-\w+)*)""".r
    val gpgVersion: Long = versionLine.split(" ").last match {
      case TaggedVersion(m, _, _) => m.toLong
      case _                      => 0L
    }
    // https://dev.gnupg.org/T2313
    val importCommand =
      if (gpgVersion < 2L) "--import"
      else "--batch --import"
    val secret = sys.env("PGP_SECRET")
    (s"echo $secret" #| "base64 --decode" #| s"gpg $importCommand").!
  }
}
