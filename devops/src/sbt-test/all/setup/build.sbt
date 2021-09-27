import munit.Assertions._

TaskKey[Unit]("readme1") := {
  IO.write((ThisBuild / baseDirectory).value / "README.md", "# repo")
}

TaskKey[Unit]("check1Fail") := {
  assertNoDiff(IO.read((ThisBuild / baseDirectory).value / "README.md"), "# repo-diff")
}

TaskKey[Unit]("check1") := {
  assertNoDiff(IO.read((ThisBuild / baseDirectory).value / "README.md"),
    """# repo
      |
      |[![CI](https://github.com/user/repo1/actions/workflows/sd-devops.yml/badge.svg)](https://github.com/user/repo1/actions/workflows/sd-devops.yml)
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
    """repo-2
      |======
      |
      |[![CI](https://github.com/user/repo1/actions/workflows/sd-devops.yml/badge.svg)](https://github.com/user/repo1/actions/workflows/sd-devops.yml)
      |
      |[![CI](h
      |""".stripMargin, "Invalid README.md")
}

