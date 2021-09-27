Compile / unmanagedSourceDirectories ++= {
  val base = (ThisBuild / baseDirectory).value.getParentFile
  Seq("devops", "devops-oss").map { d =>
    base / d / "src" / "main" / "scala"
  }
}
libraryDependencies ++= Seq(
  "com.lihaoyi" %% "requests" % "0.6.9",
  "com.lihaoyi" %% "ujson" % "1.4.1",
)
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.3")

addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.1")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.10")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.1.2")
