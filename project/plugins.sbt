Compile / unmanagedSourceDirectories ++= {
  val base = (ThisBuild / baseDirectory).value.getParentFile
  Seq("sd-devops", "sd-devops-oss").map { d =>
    base / d / "src" / "main" / "scala"
  }
}

addSbtPlugin("com.geirsson" % "sbt-ci-release" % "1.5.7")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.3")
