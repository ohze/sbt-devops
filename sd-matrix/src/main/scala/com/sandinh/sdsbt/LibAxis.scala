package com.sandinh.sdsbt

import sbt.*

object LibAxis {
  private def binVersion(version: String): String = version match {
    case VersionNumber(Seq(m, n, _), _, _) => s"_${m}_$n"
    case _ => sys.error(s"invalid play version $version")
  }

  def apply(version: String): LibAxis = LibAxis(version, binVersion(version))
}

case class LibAxis(version: String, suffix: String)
    extends VirtualAxis.WeakAxis {
  import LibAxis.*

  def directorySuffix: String = binVersion(version)
  def idSuffix: String = binVersion(version)
}
