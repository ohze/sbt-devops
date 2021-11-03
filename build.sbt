import sbtdocker.DockerKeys.dockerPush
import scala.sys.env
import scala.sys.process._

def currentBranch: String =
  env.get("GITHUB_HEAD_REF") match {
    case None | Some("") => "git rev-parse --abbrev-ref HEAD".!!.trim()
    case Some(ref)       => ref
  }

def pluginSettings(minSbtVersion: String) = Seq(
  pluginCrossBuild / sbtVersion := minSbtVersion,
  scriptedLaunchOpts ++= Seq(
    "-Xmx1024M",
    s"-Ddevops.branch=$currentBranch",
  ),
  scriptedScalatestDependencies ++= Seq(
    "org.scalatest::scalatest-flatspec:3.2.10",
    "org.scalatest::scalatest-mustmatchers:3.2.10",
  ),
)

lazy val commonDeps = addSbtPlugins(
  "org.scalameta" % "sbt-scalafmt" % "2.4.3",
  "com.dwijnand" % "sbt-dynver" % "4.1.1",
  "com.typesafe.sbt" % "sbt-git" % "1.0.2",
)

lazy val devops = Project("sbt-devops", file("devops"))
  .enablePlugins(SbtPlugin)
  .settings(
    pluginSettings("1.3.13") ++ commonDeps,
    Compile / unmanagedSourceDirectories += (Compile / scalaSource).value.getParentFile / "nexus",
  )

lazy val devopsOss = Project("sbt-devops-oss", file("devops-oss"))
  .enablePlugins(SbtPlugin)
  .settings(
    pluginSettings("1.3.13") ++ commonDeps,
    addSbtPlugins(
      "org.xerial.sbt" % "sbt-sonatype" % "3.9.10",
      "com.github.sbt" % "sbt-pgp" % "2.1.2"
    ),
    Compile / unmanagedSourceDirectories += (devops / Compile / scalaSource).value,
    duplicateSbtTest(devops),
  )

def duplicateSbtTest(p: Project) = Seq(
  sbtTestDirectory := target.value / "sbt-test",
  scripted := scripted
    .dependsOn(Def.task {
      IO.copyDirectory((p / sbtTestDirectory).value, target.value / "sbt-test")
    })
    .evaluated,
)

lazy val `devops-notify` = project
  .enablePlugins(DockerPlugin)
  .settings(
    skipPublish,
    scalaVersion := scala213,
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client3" %% "upickle" % "3.3.14",
    ),
    assembly / mainClass := Some("com.sandinh.devops.Notify"),
    assembly / assemblyOutputPath := target.value / "notify.jar",
    docker := docker.dependsOn(assembly).value,
    docker / dockerfile := NativeDockerfile(baseDirectory.value / "Dockerfile"),
    docker / imageNames := {
      val tags =
        if (isSnapshot.value) Seq("edge")
        else Seq("edge", "latest", version.value)
      val name = moduleName.value
      tags.map(tag => ImageName(s"ohze/$name:$tag"))
    },
    dockerPush := dockerPush.dependsOn(dockerLogin).value
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
    pluginSettings("1.5.5"),
    target := target.value / id,
  )

lazy val sd = sandinhPrj("sd-devops").dependsOn(devops)

lazy val sdOss = sandinhPrj("sd-devops-oss")
  .dependsOn(devopsOss)
  .settings(duplicateSbtTest(sd))

lazy val `sd-matrix` = project
  .enablePlugins(SbtPlugin)
  .settings(
    pluginCrossBuild / sbtVersion := "1.5.5",
    addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.8.0"),
  )

lazy val `sbt-devops-root` = project
  .in(file("."))
  .settings(skipPublish)
  .aggregate(devops, devopsOss, sd, sdOss, `devops-notify`, `sd-matrix`)
