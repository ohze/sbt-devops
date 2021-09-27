lazy val prjA = project
lazy val prjB = project
  .settings(
    dynverTagPrefix := "pB"
  )

import munit.Assertions._

TaskKey[Unit]("check1") := {
  assertEquals((prjA / version).value, "1.1")
  assert((prjB / version).value.endsWith("SNAPSHOT"))
}

TaskKey[Unit]("check2") := {
  assertEquals((prjA / version).value, "1.1")
  assertEquals((prjB / version).value, "1.2")
}
