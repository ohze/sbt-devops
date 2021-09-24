
TaskKey[Unit]("check") := {
  val base = (ThisBuild / baseDirectory).value

  val readme = base / "README.md"
  orBoom(readme.isFile, "README.md not created")
}

def boom(msg: String) = throw new MessageOnlyException(msg)
def orBoom(check: => Boolean, msg: String): Unit = if (!check) boom(msg)
