import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

scriptedScalatestSpec := Some(
  new AnyFlatSpec with Matchers with ScriptedScalatestSuiteMixin {
    override val sbtState: State = state.value

    "devops" should "devopsSetup && !devopsQA" in {
      run(devopsSetup) mustBe Value(())
      run(devopsQA) mustBe a[Inc]
    }

    def run[T](taskKey: ScopedKey[Task[T]]): Result[T] =
      Project.runTask(taskKey, sbtState).get._2
  }
)

// After reload: set scriptedScalatestSpec := (Test / scriptedScalatestSpec).value
Test / scriptedScalatestSpec := Some(
  new AnyFlatSpec with Matchers with ScriptedScalatestSuiteMixin {
    override val sbtState: State = state.value
    val base = (ThisBuild / baseDirectory).value

    "devops" should "devopsQA after scalafmtSbt && reload" in {
      run(devopsQA) mustBe Value(())
    }

    it should "setup README.md" in {
      val readme = base / "README.md"
      readme.exists() mustBe true
      readme.delete()
      run(devopsQA) mustBe a[Inc]

      IO.write(readme, "# repo")
      run(devopsSetup) mustBe Value(())

      val ymlUrl = "https://github.com/user/repo1/actions/workflows/sbt-devops.yml"
      IO.read(readme) mustBe s"""# repo
                                |
                                |[![CI]($ymlUrl/badge.svg)]($ymlUrl)
                                |\n""".stripMargin

      IO.write (readme, """repo-2
                          |======
                          |[![CI](h
                          |""".stripMargin)

      run(devopsSetup) mustBe Value(())
      IO.read(readme) mustBe s"""repo-2
                                |======
                                |
                                |[![CI]($ymlUrl/badge.svg)]($ymlUrl)
                                |
                                |[![CI](h
                                |""".stripMargin
    }

    def run[T](taskKey: ScopedKey[Task[T]]): Result[T] =
      Project.runTask(taskKey, sbtState).get._2
  }
)
