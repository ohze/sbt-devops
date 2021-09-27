lazy val pluginSettings = Seq(
  pluginCrossBuild / sbtVersion := "1.3.13", // minimum sbt version
  scriptedLaunchOpts += "-Xmx1024M",
  scripted := scripted.dependsOn(scriptedPrepare).evaluated,
)

def scriptedPrepare = Def.task {
  for {
    prjDir <- (
      PathFinder(sbtTestDirectory.value) * DirectoryFilter * DirectoryFilter
    ).get()
  } IO.write(
    prjDir / "project/plugins.sbt",
    s"""addSbtPlugin("${organization.value}" % "${name.value}" % "${version.value}")
       |libraryDependencies += "org.scalameta" %% "munit" % "0.7.29"
       |""".stripMargin
  )
}

lazy val commonDeps = Seq(
  addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.3"),
  addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1"),
  addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.1"),
  libraryDependencies ++= Seq(
    "com.lihaoyi" %% "requests" % "0.6.9",
    "com.lihaoyi" %% "ujson" % "1.4.1",
  )
)

lazy val devops = Project("sbt-devops", file("devops"))
  .enablePlugins(SbtPlugin)
  .settings(pluginSettings ++ commonDeps)
  .settings(
    Compile / unmanagedSourceDirectories += (Compile / scalaSource).value.getParentFile / "bennuoc",
  )

lazy val devopsOss = Project("sbt-devops-oss", file("devops-oss"))
  .enablePlugins(SbtPlugin)
  .settings(pluginSettings ++ commonDeps)
  .settings(
    addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.10"),
    addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.1.2"),
    Compile / unmanagedSourceDirectories += (devops / Compile / scalaSource).value,
  )

inThisBuild(
  Seq(
    versionScheme := Some("semver-spec"),
    developers := List(
      Developer(
        "thanhbv",
        "Bui Viet Thanh",
        "thanhbv@sandinh.net",
        url("https://sandinh.com")
      )
    )
  )
)

lazy val `sbt-devops-root` = project
  .in(file("."))
  .settings(
    publish / skip := true
  )
  .aggregate(devops, devopsOss)
