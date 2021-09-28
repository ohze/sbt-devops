package com.sandinh.devops

import sbt.file
import requests.Response
import Utils.{EnvOps, boom, envKeys}
import scala.collection.immutable.Seq
import scala.sys.env

object Notify {
  private case class Job(name: String, result: String) {
    def emoji: String = result match {
      case "success"   => ":white_check_mark:"
      case "failure"   => ":x:"
      case "cancelled" => ":white_circle:"
      case "skipped" =>
        ":black_right_pointing_double_triangle_with_vertical_bar:"
      case _ => boom("invalid Job result")
    }

    def isFailure: Boolean = result == "failure"

    private def publishSuccess = name == "publish" && result == "success"

    def asAttachmentField(version: String): ujson.Obj = ujson.Obj(
      "short" -> true,
      "title" -> name,
      "value" -> (if (publishSuccess) s"$emoji $version" else emoji)
    )

    override def toString: String = s"$name: $result"
  }

  private def commitMsg = {
    import sys.process.*
    s"git show -s --format=%s ${env("GITHUB_SHA")}".!!.trim
  }

  // https://developers.mattermost.com/integrate/incoming-webhooks/#parameters
  /** @param versionMsg is only used as attachments/ fields / value message */
  def apply(versionMsg: String): Response = {
    val webhook = env
      .any("WEBHOOK_URL")
      .getOrElse(boom(s"None of ${envKeys("WEBHOOK_URL")} env is set"))
    val runId = env.getOrElse(
      "GITHUB_RUN_ID",
      boom("devopsNotify task must be run in Github Action")
    )
    val home = s"${env("GITHUB_SERVER_URL")}/${env("GITHUB_REPOSITORY")}"
    val link = s"$home/actions/runs/$runId"
    val jobs = for {
      v <- env.get("_DEVOPS_NEEDS").toSeq
      (jobName, job) <- ujson.read(v).obj
    } yield Job(jobName, job.obj("result").str)

    val text = env("GITHUB_EVENT_NAME") match {
      case "pull_request" =>
        val payloadFile = file(env("GITHUB_EVENT_PATH"))
        val pr = ujson.read(payloadFile).obj("number").num.toLong
        s"pull request [#$pr]($home/pull/$pr)"
      case _ => s"commit: $commitMsg"
    }
    val attachment = ujson.Obj(
      "fallback" -> jobs.mkString("CI jobs status: ", ", ", ""),
      "author_name" -> env("GITHUB_REPOSITORY"),
      "author_icon" -> "https://chat.ohze.net/api/v4/emoji/tu6nrabuftrk78rm78mapoq7to/image",
      "text" -> s"[CI jobs status]($link) for $text",
      "fields" -> jobs.map(_.asAttachmentField(versionMsg)),
    )
    env.any("PRETEXT").foreach(attachment("pretext") = _)
    if (jobs.exists(_.isFailure)) attachment("color") = "#FF0000"

    val data = ujson.Obj("attachments" -> ujson.Arr(attachment))

    val urlPattern = "https?://.*".r
    env.any("ICON") match {
      case Some(url @ urlPattern()) => data("icon_url") = url
      case Some(emoji)              => data("icon_emoji") = emoji
      case None => data("icon_emoji") = ":electric_plug:" // ":dizzy:"
    }

    for {
      (keySuffix, field) <- Seq(
        "CHANNEL" -> "channel",
        "USERNAME" -> "username",
      )
      value <- env.any(keySuffix)
    } data(field) = value

    requests.post(webhook, data = data)
  }
}
