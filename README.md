# sbt-devops

[![CI](https://github.com/ohze/sbt-devops/actions/workflows/sbt-devops.yml/badge.svg)](https://github.com/ohze/sbt-devops/actions/workflows/sbt-devops.yml)

#### devops automator for scala/sbt projects
This is a sbt AutoPlugin that do 4 things:

#### 1. `devopsSetup` task:
+ Setup scalafmt
+ Setup Github Action CI to
  - test
  - QA (Quality Assurance) check by running `sbt test devopsQA`
  - Auto release when you push code to github
    * Release to maven central (sonatype oss) if your project is open source
    * Release to your private nexus repository if your project is private
    * Release to `releases` maven if you push a git tag with [special prefix](#Tag-to-release)
    * Release to `snapshots` maven otherwise
  - notify to mattermost when the CI jobs completed
+ Setup other things such as adding a badge to README.md,..

#### 2. `devopsQA` task validate that
+ You have setup CI, scalafmt
+ Your code is formatted
+ You don't define version manually
+ You don't break binary compatibility with the previous stable version
+ ... see source code for more detail

#### 3. `devopsNotify` task
+ Notify your [Mattermost](https://mattermost.com/) or Slack webhook when CI jobs done
+ The message also contains jobs info such as status, published version, link to job,
  optional mentions like @channel, @here, @some_user

#### 4. Auto add some sbt settings such as
+ `version`: Auto get from git
+ `resolvers += your private repository` (unless using `sbt-devops`**-oss**)
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
See [releases](https://github.com/ohze/sbt-devops/releases) for available `<version>`s.
Release with tag `vM.N.P` => version `M.N.P`, ex `v3.0.0` => version `3.0.0`

## Usage
1. install (see above) -> run `sbt devopsSetup`
2. (optional) remove some sbt settings that have been defined by sbt-devops such as `publishTo`,.. see above.
3. run `sbt +devopsQA`
4. If you use private maven hosting with `sbt-devops` then add this to `build.sbt`:  
    `Global / devopsNexusHost := "<your repo, ex repo.example.com>"`
5. To auto release, you need manually setup secrets in your github repo setting:  
   `Your github repo -> Settings -> Secrets -> New repository secret`
+ sbt-devops (private maven repository)
  - `NEXUS_USER`, `NEXUS_PASS`: Your username/ password in `devopsNexusHost`
+ sbt-devops-oss
  - `SONATYPE_USERNAME, SONATYPE_PASSWORD`: Your username/ password in sonatype oss
  - `PGP_SECRET, PGP_PASSPHRASE`: See sbt-ci-release's [guide](https://github.com/olafurpg/sbt-ci-release#gpg)
6. secrets need to notify mattermost/ slack:
  - `MATTERMOST_WEBHOOK_URL`
  See [files/sbt-devops.yml](files/sbt-devops.yml) for details and how to customize message, icon, channel,..
7. (optional) customize `.scalafmt.conf, .github/workflows/sbt-devops.yml`
8. Commit changes, push -> auto publish -> notify.
+ <a id="Tag-to-release">Tag to release</a>: Push tag -> publish release version.  
  The tag format must be `<dynverTagPrefix><MajorNumber><remains>`  
  `dynverTagPrefix` default = `v`  
  Tag example: `v1.2.3-blabla` to release version `1.2.3-blabla`
+ Push commit has no matched tag -> publish `..-SNAPSHOT` version
9. Enjoy

## Thanks
This project use:
+ Depends on sbt plugins: [sbt-scalafmt](https://github.com/scalameta/sbt-scalafmt),
[sbt-dynver](https://github.com/dwijnand/sbt-dynver), [sbt-git](https://github.com/sbt/sbt-git),
[sbt-sonatype](https://github.com/xerial/sbt-sonatype), [sbt-pgp](https://github.com/sbt/sbt-pgp)
+ Copy some code from [sbt-ci-release](https://github.com/sbt/sbt-ci-release) plugins.
+ [sbt-version-policy](https://github.com/scalacenter/sbt-version-policy) and [MiMa](https://github.com/lightbend/mima)
+ Use lihaoyi's [requests](https://github.com/com-lihaoyi/requests-scala), [ujson](https://github.com/com-lihaoyi/upickle)
+ of course scala, sbt and the transitive dependencies.
Thanks you all!

## Licence
This software is licensed under the [Apache 2 license](http://www.apache.org/licenses/LICENSE-2.0)

Copyright 2021 Sân Đình (https://sandinh.com)

## CHANGES
see [CHANGELOG.md](CHANGELOG.md)

## For Sân Đình's projects only (private or oss)
Use `sd-devops` / `sd-devops-oss` instead of `devops` / `devops-oss`
It add some predefined settings for sandinh such as:
+ `organization := "com.sandinh"`
+ `scalacOptions`
+ `repo.bennuoc.com`
+ ... see [sbtsd code](sd/src/main/scala/com/sandinh/sbtsd) for more details.
