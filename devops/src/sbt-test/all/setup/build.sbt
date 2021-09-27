import munit.Assertions._

TaskKey[Unit]("readme1") := {
  IO.write((ThisBuild / baseDirectory).value / "README.md", "# repo")
}

TaskKey[Unit]("check1Fail") := {
  assertNoDiff(IO.read((ThisBuild / baseDirectory).value / "README.md"), "# repo-diff")
}

val ymlUrl = "https://github.com/user/repo1/actions/workflows/sbt-devops.yml"
TaskKey[Unit]("check1") := {
  assertNoDiff(IO.read((ThisBuild / baseDirectory).value / "README.md"),
    s"""# repo
      |
      |[![CI]($ymlUrl/badge.svg)]($ymlUrl)
      |""".stripMargin, "Invalid README.md")
}

TaskKey[Unit]("readme2") := {
  IO.write((ThisBuild / baseDirectory).value / "README.md",
    """repo-2
      |======
      |[![CI](h
      |""".stripMargin)
}

TaskKey[Unit]("check2") := {
  assertNoDiff(IO.read((ThisBuild / baseDirectory).value / "README.md"),
    s"""repo-2
      |======
      |
      |[![CI]($ymlUrl/badge.svg)]($ymlUrl)
      |
      |[![CI](h
      |""".stripMargin, "Invalid README.md")
}
