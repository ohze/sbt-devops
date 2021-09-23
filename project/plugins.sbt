Compile / unmanagedSourceDirectories ++= {
  val base = (ThisBuild / baseDirectory).value.getParentFile
  Seq("sd-devops", "sd-devops-oss").map { d =>
    base / d / "src" / "main" / "scala"
  }
}
libraryDependencies ++= Seq(
  "com.lihaoyi" %% "requests" % "0.6.9",
  "com.lihaoyi" %% "ujson" % "1.4.1",
)
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.3")

addSbtPlugin("com.geirsson" % "sbt-ci-release" % "1.5.7")
