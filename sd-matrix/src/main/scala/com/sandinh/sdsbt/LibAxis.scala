package com.sandinh.sdsbt

import sbt.*

object LibAxis {
  private def binVersion(version: String): String = version match {
    case VersionNumber(Seq(m, n, _), _, _) => s"_${m}_$n"
    case _ => sys.error(s"invalid play version $version")
  }

  def apply(name: String, version: String): LibAxis =
    LibAxis(name, version, binVersion(version))

  /** LibAxis for versions.head will have `suffix` == "" */
  def apply(name: String, versions: Seq[String]): Seq[LibAxis] =
    LibAxis(name, versions.head, "") +: versions.tail.map(LibAxis(name, _))
}

case class LibAxis(name: String, version: String, suffix: String)
    extends VirtualAxis.WeakAxis {
  import LibAxis.*

  def directorySuffix: String = binVersion(version)
  def idSuffix: String = binVersion(version)
}
