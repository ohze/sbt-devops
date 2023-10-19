Compile / unmanagedSourceDirectories ++= {
  val base = (ThisBuild / baseDirectory).value.getParentFile
  Seq("devops", "devops-oss", "sd").map { d =>
    base / d / "src" / "main" / "scala"
  }
}

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")

addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.0.1")
addSbtPlugin("com.github.sbt" % "sbt-git" % "2.0.1")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.21")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.2.1")

addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.11.0")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.3")

addSbtPlugin("com.sandinh" % "sbt-scripted-scalatest" % "3.1.0")

addSbtPlugin("ch.epfl.scala" % "sbt-version-policy" % "2.1.1")
