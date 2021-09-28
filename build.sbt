import com.sandinh.devops.Utils.currentBranch

lazy val pluginSettings = Seq(
  pluginCrossBuild / sbtVersion := "1.3.13", // minimum sbt version
  scriptedLaunchOpts ++= Seq(
    "-Xmx1024M",
    "-Ddevops.branch=" + currentBranch.get,
  ),
  scripted := scripted.dependsOn(scriptedPrepare).evaluated,
)

def scriptedPrepare = Def.task {
  for {
    prjDir <- (
      PathFinder(sbtTestDirectory.value) * DirectoryFilter * DirectoryFilter
    ).get()
  } IO.write(
    prjDir / "project/plugins.sbt",
    s"""addSbtPlugin("${organization.value}" % "${moduleName.value}" % "${version.value}")
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
    Compile / unmanagedSourceDirectories += (Compile / scalaSource).value.getParentFile / "nexus",
  )

lazy val devopsOss = Project("sbt-devops-oss", file("devops-oss"))
  .enablePlugins(SbtPlugin)
  .settings(pluginSettings ++ commonDeps)
  .settings(
    addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.10"),
    addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.1.2"),
    Compile / unmanagedSourceDirectories += (devops / Compile / scalaSource).value,
    scripted := scripted
      .dependsOn(Def.task {
        IO.copyDirectory(
          (devops / sbtTestDirectory).value,
          target.value / "sbt-test"
        )
      })
      .evaluated,
    sbtTestDirectory := target.value / "sbt-test",
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

// for sandinh only
def sandinhPrj(id: String) = Project(id, file("sd"))
  .enablePlugins(SbtPlugin)
  .settings(
    pluginCrossBuild / sbtVersion := "1.5.5",
    dynverTagPrefix := "sd",
    addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.8.0"),
    target := target.value / id,
  )

lazy val sd = sandinhPrj("sd-devops").dependsOn(devops)

lazy val sdOss = sandinhPrj("sd-devops-oss").dependsOn(devopsOss)

lazy val `sbt-devops-root` = project
  .in(file("."))
  .settings(skipPublish)
  .aggregate(devops, devopsOss, sd, sdOss)
