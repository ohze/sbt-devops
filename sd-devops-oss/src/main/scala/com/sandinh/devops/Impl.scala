package com.sandinh.devops

import scala.collection.immutable.Seq
import sbt._
import sbt.Keys._

object Impl {
  lazy val buildSettings: Seq[Setting[_]] = Seq(
    licenses := List(
      "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
    ),
  )

  lazy val globalSettings: Seq[Setting[_]] = Nil

  lazy val projectSettings: Seq[Setting[_]] = Nil

  val isOss = true
}
