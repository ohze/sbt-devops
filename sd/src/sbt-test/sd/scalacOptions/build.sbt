import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import sbt.Keys.{compile => compileKey}

lazy val prjA = (project in file("."))
  .settings(
    scriptedScalatestSpec := Some(new AnyFlatSpec with Matchers with ScriptedScalatestSuiteMixin {
      override val sbtState: State = state.value

      "scalaVersion" should "be set" in {
        val state = sbtState.appendWithSession(Seq(scalaVersion := "2.13.6"))
        val extracted = Project.extract(state)
        extracted.get(scalaVersion) mustBe "2.13.6"
      }

      "SdPlugin" should "set default scalacOptions" in {
        taskValue(scalacOptions) mustBe Seq( // must contain theSameElementsAs Seq(
          "-encoding", "UTF-8", "-deprecation", "-feature", "-Xsource:3"
        )
      }

      it should "set Compile / scalacOptions" in {
        val expected = Seq(
          "-encoding", "UTF-8", "-deprecation", "-feature", "-Xsource:3", "-Xfatal-warnings"
        )
        taskValue(Compile / scalacOptions) mustBe expected
        taskValue(Compile / compileKey / scalacOptions) mustBe expected
        taskValue(Compile / compileIncremental / scalacOptions) mustBe expected
      }

      it should "set Test / scalacOptions" in {
        taskValue(Test / scalacOptions) mustBe Seq(
          "-encoding", "UTF-8", "-deprecation", "-feature", "-Xsource:3"
        )
      }

      it should "set Compile / doc / scalacOptions" in {
        taskValue(Compile / doc / scalacOptions) mustBe Seq(
          "-encoding", "UTF-8", "-deprecation", "-feature", "-Xsource:3"
        )
      }

      it should "set scalacOptions for scala 2.11" in {
        val base = Seq("-encoding", "UTF-8", "-deprecation", "-feature", "-Ybackend:GenBCode", "-target:jvm-1.8")
        def check(k: TaskKey[Seq[String]], expected: Seq[String]): Unit = {
          val (a, b) = taskValue(k, scala211).partition(_.startsWith("-Xplugin:"))
          b mustBe expected
          a.size mustBe 1
          assert(a.head.endsWith("silencer-plugin_2.11.12-1.7.12.jar"))
        }

        taskValue(scalacOptions, scala211) mustBe base
        check(Compile / scalacOptions, base :+ "-Xfatal-warnings")
        check(Compile / compileKey / scalacOptions, base :+ "-Xfatal-warnings")
        check(Compile / compileIncremental / scalacOptions, base :+ "-Xfatal-warnings")
        check(Compile / doc / scalacOptions, base)
        check(Test / scalacOptions, base)
      }

      it should "set scalacOptions for scala 2.12" in {
        val base = Seq("-encoding", "UTF-8", "-deprecation", "-feature", "-target:jvm-1.8", "-Xsource:3")
        def v(k: TaskKey[Seq[String]]) = taskValue(k, scala212)

        v(scalacOptions) mustBe base
        v(Compile / scalacOptions) mustBe base :+ "-Xfatal-warnings"
        v(Compile / compileKey / scalacOptions) mustBe base :+ "-Xfatal-warnings"
        v(Compile / compileIncremental / scalacOptions) mustBe base :+ "-Xfatal-warnings"
        v(Compile / doc / scalacOptions) mustBe base
        v(Test / scalacOptions) mustBe base
      }

      it should "set scalacOptions for scala 3" in {
        val base = Seq("-encoding", "UTF-8", "-deprecation", "-feature")
        def v(k: TaskKey[Seq[String]]) = taskValue(k, scala3)

        v(scalacOptions) mustBe base
        v(Compile / scalacOptions) mustBe base :+ "-Xfatal-warnings"
        v(Compile / compileKey / scalacOptions) mustBe base :+ "-Xfatal-warnings"
        v(Compile / compileIncremental / scalacOptions) mustBe base :+ "-Xfatal-warnings"

        // == base ++ Seq("-project", "prjA") if sbtVersion >= 1.5.5
        // but == Seq("-project", "prjA") if sbtVersion == 1.5.0
        v(Compile / doc / scalacOptions) mustBe base ++ Seq("-project", "prjA")
        v(Test / scalacOptions) mustBe base
      }

      def taskValue[T](taskKey: ScopedKey[Task[T]], scalaV: String = "2.13.6"): T =
        taskValue(taskKey, Seq(scalaVersion := scalaV))

      def taskValue[T](taskKey: ScopedKey[Task[T]], settings: Seq[Def.Setting[_]]): T = {
        val state = sbtState.appendWithSession(settings)
        Project.runTask(taskKey, state).get._2.toEither.toOption.get
      }
    }),
  )
