# sd-devops

[![CI](https://github.com/ohze/sd-devops/actions/workflows/test.yml/badge.svg)](https://github.com/ohze/sd-devops/actions/workflows/test.yml)

Sân Đình devops automator for scala projects

This is a sbt AutoPlugin that do 3 things:

#### 1. `sdSetup` task:
+ Setup scalafmt
+ Setup Github Action CI to test & do some QA (Quality Assurance) check by running `sbt test sdQA`
+ Setup CI to auto release when you push code to github
    - Release to maven central (sonatype oss) if your project is open source
    - Release to bennuoc if your project is private
    - Release to `releases` maven if you push a git tag
    - Release to `snapshots` maven otherwise
+ Setup other things such as add a badge to README.md,..

#### 2. `sdQA` task validate that
+ You have setup CI, scalafmt
+ Your code is formatted
+ You don't define version manually
+ ...

#### 3. Auto add some sbt settings such as
+ `version`: Auto get from git
+ `resolvers += bennuoc`
+ `organization := "com.sandinh"`
+ `publishMavenStyle := true`
+ `scmInfo`, `homepage`, `publishTo`, `publishMavenStyle`, `credentials`,..
  See source code for more detail.
+ Of course, you can override those settings in your project

## Install
Add to `project/plugins.sbt`
+ For private projects that will be published to repo.bennuoc.com
```sbt
addSbtPlugin("com.sandinh" % "sd-devops" % "<version>")
```
+ For oss projects that will be publish to sonatype oss
```sbt
addSbtPlugin("com.sandinh" % "sd-devops-oss" % "<version>")
```

## Usage
You need manually setup secrets in your github repo setting:

`Your github repo -> Settings -> Secrets -> New repository secret`
#### sd-devops (private repo)
+ `BENNUOC_USER`, `BENNUOC_PASS`: Your username/ password in bennuoc

  You can also use different secret names: `NEXUS_USER, NEXUS_PASS, SONATYPE_USERNAME, SONATYPE_PASSWORD`
#### sd-devops-oss
+ `SONATYPE_USERNAME, SONATYPE_PASSWORD`: Your username/ password in sonatype oss
+ `PGP_SECRET, PGP_PASSPHRASE`: See sbt-ci-release's [guide](https://github.com/olafurpg/sbt-ci-release#gpg)
