package com.sandinh.devops

import sbt.{Plugins, Setting}

import scala.collection.immutable.Seq

trait ImplTrait {
  def isOss: Boolean
  def requiresImpl: Plugins
  def buildSettingsImpl: Seq[Setting[?]]
  def globalSettingsImpl: Seq[Setting[?]]
  def projectSettingsImpl: Seq[Setting[?]]
  private[devops] def ciReleaseSnapshotCmds: List[String]
  private[devops] def ciReleaseCmds: List[String]
}
