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
  .settings(
    pluginSettings("1.3.13") ++ commonDeps,
    Compile / unmanagedSourceDirectories += (Compile / scalaSource).value.getParentFile / "nexus",
  )

lazy val devopsOss = Project("sbt-devops-oss", file("devops-oss"))
  .enablePlugins(SbtPlugin)
  .settings(
    pluginSettings("1.3.13") ++ commonDeps,
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

def dockerLogin = Def.task {
  val log = streams.value.log
  log.info("docker login ...")
  val pw = s"echo ${env("DOCKER_PASSWORD")}"
  val login = s"docker login -u ${env("DOCKER_USERNAME")} --password-stdin"
  log.info((pw #| login).!!)
}

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

lazy val sdOss = sandinhPrj("sd-devops-oss").dependsOn(devopsOss)

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
