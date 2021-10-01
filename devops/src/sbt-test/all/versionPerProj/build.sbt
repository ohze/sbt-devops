import com.sandinh.devops.DevopsPlugin.qaVersionTask
TaskKey[Unit]("qaVersion") := qaVersionTask.all(ScopeFilter(inAnyProject)).value

lazy val prjA = project
lazy val prjB = project

import munit.Assertions._

TaskKey[Unit]("check1") := {
  assertEquals((prjA / version).value, "1.1")
  assertEquals((prjB / version).value, "1.1")
}

def gitVer(num: Int): String = {
  import scala.sys.process._
  val TagPattern = "v[0-9]*"
  // result example: v1.1-1-g1feec7e3
  val process = Process(s"git describe --long --tags --abbrev=8 --match $TagPattern")
  val short = process.!!.trim.stripPrefix(s"v1.1-$num-g")
  s"1.1+$num-$short"
}

TaskKey[Unit]("check2") := {
  val v = gitVer(0)
  val vA = (prjA / version).value
  val vB = (prjB / version).value
  assert(vA.startsWith(v) && vA.endsWith("-SNAPSHOT"), s"\n$vA ~ $v")
  assert(vB.startsWith(v) && vB.endsWith("-SNAPSHOT"), s"\n$vA ~ $v")
}
