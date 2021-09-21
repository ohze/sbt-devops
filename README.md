# sd-devops
Sân Đình devops automator for scala projects

## Install
Add to `project/plugins.sbt`
```sbt
// for private projects that will be published to repo.bennuoc.com
addSbtPlugin("com.sandinh" % "sd-devops" % "<version>")
// for oss projects that will be publish to sonatype oss
addSbtPlugin("com.sandinh" % "sd-devops-oss" % "<version>")
```

## What it does?
Both `sd-devops` and `sd-devops-oss` defines `SdDevOpsPlugin` sbt AutoPlugin that:
+ Auto add `sbt-dynver`, `sbt-git`, `sbt-scalafmt` plugins
+ `sd-devops-oss` also add `sbt-ci-release` which transitively add `sbt-sonatype`, `sbt-pgp` plugins
+ Auto define the following settings:
  - `organization := "com.sandinh"`
  - `scmInfo := gitHubScmInfo`
  - `homepage := s"https://github.com/$user/$repo"`
  - `publishMavenStyle := true`
  - `version`: handled by sbt-dynver
  - `publishTo`: = bennuoc for `sd-devops` or handled by sbt-ci-release for `sd-devops-os`
  - `credentials`: handled by `sd-devops` or by sbt-sonatype for `sd-devops-os`
+ Define `sdQA` sbt task: SanDinh QA (Quality Assurance), which verify that:
  1. `.scalafmt.conf` file exists
  2. `.scalafmt.conf` have `version = <The version defined in sd-devops>`
  3. `scalafmtCheckAll` pass
  4. `.github/workflows/{test.yml, release.yml}` files exists
  5. `test.yml` must define step: `- run: sbt <optional params> sdQA`.
      Here, `<optional params>` maybe `++$${{ matrix.scala }}`