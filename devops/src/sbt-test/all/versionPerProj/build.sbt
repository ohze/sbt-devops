import com.sandinh.devops.DevopsPlugin.qaVersionTask
TaskKey[Unit]("qaVersion") := qaVersionTask.all(ScopeFilter(inAnyProject)).value

versionPolicyCheck / skip := true
lazy val prjA = project.settings(versionPolicyCheck / skip := true)
lazy val prjB = project.settings(versionPolicyCheck / skip := true)

import org.scalatest.matchers.must.Matchers._

TaskKey[Unit]("check1") := {
  (prjA / version).value mustBe "1.1"
  (prjB / version).value mustBe "1.1"
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
