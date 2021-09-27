package com.sandinh.sbtsd.matrix

import sbt.VirtualAxis

case class MatrixAxis(
    idSuffix: String,
    directorySuffix: String
) extends VirtualAxis.WeakAxis
