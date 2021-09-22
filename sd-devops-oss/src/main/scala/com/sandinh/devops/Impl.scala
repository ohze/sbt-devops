package com.sandinh.devops

import sbt.Keys._
import sbt._

object Impl {
  lazy val buildSettings: Seq[Setting[_]] = Seq(
    licenses := List(
      "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
    ),
  )

  lazy val globalSettings: Seq[Setting[_]] = Nil

  lazy val projectSettings: Seq[Setting[_]] = Nil

  private[devops] val ciReleaseEnvs = Seq(
    "PGP_PASSPHRASE",
    "PGP_SECRET",
    "SONATYPE_PASSWORD",
    "SONATYPE_USERNAME"
  )
}
