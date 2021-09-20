Compile / unmanagedSourceDirectories +=
  (ThisBuild / baseDirectory).value.getParentFile /
    "sd-devops" / "src" / "main" / "scala"

addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.1")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.3")
