
TaskKey[Unit]("check") := {
  val base = (ThisBuild / baseDirectory).value

  val readme = base / "README.md"
  assert(readme.isFile, "README.md not created")
}
