package com.sandinh.devops

import sbt.{Plugins, Setting}

import scala.collection.immutable.Seq

trait ImplTrait {
  def isOss: Boolean
  def requiresImpl: Plugins
  def buildSettingsImpl: Seq[Setting[_]]
  def globalSettingsImpl: Seq[Setting[_]]
  def projectSettingsImpl: Seq[Setting[_]]
  private[devops] def ciReleaseSnapshotCmds: List[String]
  private[devops] def ciReleaseCmds: List[String]
}
