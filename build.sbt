import scala.sys.env
import scala.sys.process._

def currentBranch: String =
  env.get("GITHUB_HEAD_REF") match {
    case None | Some("") => "git rev-parse --abbrev-ref HEAD".!!.trim()
    case Some(ref)       => ref
  }

lazy val pluginSettings = Seq(
  pluginCrossBuild / sbtVersion := "1.3.13", // minimum sbt version
  scriptedLaunchOpts ++= Seq(
    "-Xmx1024M",
    s"-Ddevops.branch=$currentBranch",
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

import scala.scalanative.build._
lazy val `devops-notify` = project
  .enablePlugins(ScalaNativePlugin)
  .settings(
    scalaVersion := scala213,
    // Set to false or remove if you want to show stubs as linking errors
    nativeLinkStubs := true,
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client3" %%% "upickle" % "3.3.14",
    ),
    nativeConfig ~= {
      _.withLTO(LTO.thin)
        .withMode(Mode.releaseFast)
        .withGC(GC.commix)
    }
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
    target := target.value / id,
  )

lazy val sd = sandinhPrj("sd-devops").dependsOn(devops)

lazy val sdOss = sandinhPrj("sd-devops-oss").dependsOn(devopsOss)

lazy val `sbt-devops-root` = project
  .in(file("."))
  .settings(skipPublish)
  .aggregate(devops, devopsOss, sd, sdOss, `devops-notify`)
