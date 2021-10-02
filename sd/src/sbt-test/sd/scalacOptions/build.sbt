import munit.Assertions._

lazy val prjA = project
  .settings(
    scalaVersion := "2.13.6",
    crossScalaVersions := Seq(scala211, scala212, scala213, scala3)
  )
TaskKey[Unit]("checkDefault") := {
  assertEquals(
    (prjA / scalacOptions).value,
    Seq("-encoding", "UTF-8", "-deprecation", "-feature", "-Xsource:3")
  )
}
TaskKey[Unit]("checkCompile") := {
  assertEquals(
    (prjA / Compile / scalacOptions).value,
    Seq("-encoding", "UTF-8", "-deprecation", "-feature", "-Xsource:3", "-Xfatal-warnings")
  )
  assertEquals(
    (prjA / Compile / compile / scalacOptions).value,
    Seq("-encoding", "UTF-8", "-deprecation", "-feature", "-Xsource:3", "-Xfatal-warnings")
  )
  assertEquals(
    (prjA / Compile / compileIncremental / scalacOptions).value,
    Seq("-encoding", "UTF-8", "-deprecation", "-feature", "-Xsource:3", "-Xfatal-warnings")
  )
}
TaskKey[Unit]("checkTest") := {
  assertEquals(
    (prjA / Test / scalacOptions).value,
    Seq("-encoding", "UTF-8", "-deprecation", "-feature", "-Xsource:3")
  )
}

TaskKey[Unit]("checkDoc") := {
  assertEquals(
    (prjA / Compile / doc / scalacOptions).value,
    Seq("-encoding", "UTF-8", "-deprecation", "-feature", "-Xsource:3")
  )
}

TaskKey[Unit]("check211") := {
  assertEquals((prjA / scalaVersion).value, "2.11.12")

  val base = Seq("-encoding", "UTF-8", "-deprecation", "-feature", "-Ybackend:GenBCode", "-target:jvm-1.8")
  def check(opts: Seq[String], expected: Seq[String]): Unit = {
    val (a, b) = opts.partition(_.startsWith("-Xplugin:"))
    assertEquals(b, expected)
    assertEquals(a.size, 1)
    assert(a.head.endsWith("silencer-plugin_2.11.12-1.7.6.jar"))
  }
  assertEquals((prjA / scalacOptions).value, base)
  check((prjA / Compile / scalacOptions).value, base :+ "-Xfatal-warnings")
  check((prjA / Compile / compile / scalacOptions).value, base :+ "-Xfatal-warnings")
  check((prjA / Compile / compileIncremental / scalacOptions).value, base :+ "-Xfatal-warnings")
  check((prjA / Compile / doc / scalacOptions).value, base)
  check((prjA / Test / scalacOptions).value, base)
}
TaskKey[Unit]("check212") := {
  assertEquals((prjA / scalaVersion).value, "2.12.15")

  val base = Seq("-encoding", "UTF-8", "-deprecation", "-feature", "-target:jvm-1.8", "-Xsource:3")
  assertEquals((prjA / scalacOptions).value, base)
  assertEquals((prjA / Compile / scalacOptions).value, base :+ "-Xfatal-warnings")
  assertEquals((prjA / Compile / compile / scalacOptions).value, base :+ "-Xfatal-warnings")
  assertEquals((prjA / Compile / compileIncremental / scalacOptions).value, base :+ "-Xfatal-warnings")
  assertEquals((prjA / Compile / doc / scalacOptions).value, base)
  assertEquals((prjA / Test / scalacOptions).value, base)
}

TaskKey[Unit]("check3") := {
  assertEquals((prjA / scalaVersion).value, "3.0.2")

  val base = Seq("-encoding", "UTF-8", "-deprecation", "-feature")
  assertEquals((prjA / scalacOptions).value, base)
  assertEquals((prjA / Compile / scalacOptions).value, base)
  assertEquals((prjA / Compile / compile / scalacOptions).value, base)
  assertEquals((prjA / Compile / compileIncremental / scalacOptions).value, base)
  assertEquals((prjA / Compile / doc / scalacOptions).value, base ++ Seq("-project", "prjA"))
  assertEquals((prjA / Test / scalacOptions).value, base)
}
