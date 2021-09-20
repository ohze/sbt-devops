lazy val `sd-devops` = project
  .enablePlugins(SbtPlugin)
  .settings(
    // https://www.scala-sbt.org/1.x/docs/Plugins.html#Creating+an+auto+plugin
    pluginCrossBuild / sbtVersion := {
      scalaBinaryVersion.value match {
        case "2.12" => "1.3.13" // set minimum sbt version
      }
    },
    // https://www.scala-sbt.org/1.x/docs/Testing-sbt-plugins.html
    scriptedLaunchOpts := { scriptedLaunchOpts.value ++
      Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
    scriptedBufferLog := false,
    addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1"),
    addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.1"),
    addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.3"),
  )

inThisBuild(
  List(
    organization := "com.sandinh",
    homepage := Some(url("https://github.com/ohze/sd-devops")),
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

publish/ skip := true // don't publish the root project
