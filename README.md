# sbt-devops

[![CI](https://github.com/ohze/sbt-devops/actions/workflows/sbt-devops.yml/badge.svg)](https://github.com/ohze/sbt-devops/actions/workflows/sbt-devops.yml)

#### devops automator for scala/sbt projects
This is a sbt AutoPlugin that do 4 things:

#### 1. `sdSetup` task:
+ Setup scalafmt
+ Setup Github Action CI to
  - test
  - QA (Quality Assurance) check by running `sbt test sdQA`
  - Auto release when you push code to github
    * Release to maven central (sonatype oss) if your project is open source
    * Release to bennuoc if your project is private
    * Release to `releases` maven if you push a git tag
    * Release to `snapshots` maven otherwise
  - notify to mattermost when the CI jobs completed
+ Setup other things such as add a badge to README.md,..

#### 2. `sdQA` task validate that
+ You have setup CI, scalafmt
+ Your code is formatted
+ You don't define version manually
+ ... see source code for more detail

#### 3. `sdMmNotify` task

#### 4. Auto add some sbt settings such as
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
addSbtPlugin("com.sandinh" % "sbt-devops" % "<version>")
```
+ For oss projects that will be publish to sonatype oss
```sbt
addSbtPlugin("com.sandinh" % "sbt-devops-oss" % "<version>")
```

## Usage
1. install (see above) -> run `sbt sdSetup`
2. (optional) remove some sbt settings that have been defined by sbt-devops such as `publishTo`,.. see above.
3. run `sbt +sdQA`
4. To auto release, you need manually setup secrets in your github repo setting:  
   `Your github repo -> Settings -> Secrets -> New repository secret`
+ sbt-devops (private repo)
  - `NEXUS_USER`, `NEXUS_PASS`: Your username/ password in bennuoc
+ sbt-devops-oss
  - `SONATYPE_USERNAME, SONATYPE_PASSWORD`: Your username/ password in sonatype oss
  - `PGP_SECRET, PGP_PASSPHRASE`: See sbt-ci-release's [guide](https://github.com/olafurpg/sbt-ci-release#gpg)
6. secrets need to notify mattermost:
  - `MATTERMOST_WEBHOOK_URL`
7. (optional) customize `.scalafmt.conf, .github/workflows/sbt-devops.yml`
8. Commit changes, push -> auto publish SNAPSHOT. Push tag -> auto publish release version.
9. Enjoy
