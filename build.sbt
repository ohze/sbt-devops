lazy val pluginSettings = Seq(
  // https://www.scala-sbt.org/1.x/docs/Plugins.html#Creating+an+auto+plugin
  pluginCrossBuild / sbtVersion := {
    scalaBinaryVersion.value match {
      case "2.12" => "1.3.13" // set minimum sbt version
    }
  },
  // https://www.scala-sbt.org/1.x/docs/Testing-sbt-plugins.html
  scriptedLaunchOpts := {
    scriptedLaunchOpts.value ++
      Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
  },
  scriptedBufferLog := false,
)

lazy val commonDeps = Seq(
  addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.3"),
  libraryDependencies ++= Seq(
    "com.lihaoyi" %% "requests" % "0.6.9",
    "com.lihaoyi" %% "ujson" % "1.4.1",
  )
)

lazy val `sd-devops` = project
  .enablePlugins(SbtPlugin)
  .settings(pluginSettings ++ commonDeps)
  .settings(
    addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1"),
    addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.1"),
    Compile / unmanagedSourceDirectories += (Compile / scalaSource).value.getParentFile / "bennuoc",
  )

lazy val `sd-devops-oss` = project
  .enablePlugins(SbtPlugin)
  .settings(pluginSettings ++ commonDeps)
  .settings(
    addSbtPlugin("com.geirsson" % "sbt-ci-release" % "1.5.7"),
    Compile / unmanagedSourceDirectories += (`sd-devops` / Compile / scalaSource).value,
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

lazy val `sd-devops-root` = project
  .in(file("."))
  .settings(
    publish / skip := true
  )
  .aggregate(`sd-devops`, `sd-devops-oss`)
