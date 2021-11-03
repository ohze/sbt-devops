Compile / unmanagedSourceDirectories ++= {
  val base = (ThisBuild / baseDirectory).value.getParentFile
  Seq("devops", "devops-oss", "sd").map { d =>
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

addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.8.2")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "1.1.0")

addSbtPlugin("com.sandinh" % "sbt-scripted-scalatest" % "3.0.3")

addSbtPlugin("ch.epfl.scala" % "sbt-version-policy" % "2.0.1")
