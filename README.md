# sd-devops
Sân Đình devops automator for scala projects

This is a sbt plugin, that:
+ Define `sdQA` sbt task: SanDinh QA (Quality Assurance), which verify that:
  1. `.scalafmt.conf` file exists
  2. `.scalafmt.conf` have `version = <The version defined in sd-devops>`
  3. `scalafmtCheckAll` pass
  4. `.github/workflows/{test.yml, release.yml}` files exists
  5. `test.yml` must define step: `- run: sbt <optional params> sdQA`.
      Here, `<optional params>` maybe `++$${{ matrix.scala }}`