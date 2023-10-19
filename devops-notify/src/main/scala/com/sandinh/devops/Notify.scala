package com.sandinh.devops

import sttp.client4.*
import sttp.client4.upicklejson.default.*
import java.nio.file.Paths
import scala.collection.immutable.Seq
import scala.sys.env
import scala.util.control.NonFatal
import sys.process.*

private case class Job(
    name: String,
    result: String,
    info: Option[String],
    commitMsg: Option[String],
) {
  def emoji: String = result match {
    case "success"   => ":white_check_mark:"
    case "failure"   => ":x:"
    case "cancelled" => ":white_circle:"
    case "skipped" =>
      ":black_right_pointing_double_triangle_with_vertical_bar:"
    case _ => boom("invalid Job result")
  }

  def isFailure: Boolean = result == "failure"

  def asAttachmentField: ujson.Obj = ujson.Obj(
    "short" -> true,
    "title" -> name,
    "value" -> info.fold(emoji)(s => s"$emoji $s")
  )

  override def toString: String = s"$name: $result"
}

def commitMsg: String = {
  val sha = env("GITHUB_SHA")
  try s"git show -s --format=%s $sha".!!.trim
  catch { case NonFatal(_) => sha.substring(0, 8) }
}

// https://developers.mattermost.com/integrate/incoming-webhooks/#parameters
@main def Notify(): Unit = {
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
    outputs = job.obj("outputs").obj
  } yield Job(
    jobName,
    job.obj("result").str,
    outputs.get("info").map(_.str),
    outputs.get("commitMsg").map(_.str.trim),
  )

  def commit = jobs.flatMap(_.commitMsg).headOption.getOrElse(commitMsg)

  val text = env("GITHUB_EVENT_NAME") match {
    case "pull_request" =>
      val payloadPath = Paths.get(env("GITHUB_EVENT_PATH"))
      val pr = ujson.read(payloadPath).obj("number").num.toLong
      s"pull request [#$pr]($home/pull/$pr)"
    case _ => s"commit: [$commit]($home/commit/${env("GITHUB_SHA")})"
  }
  val attachment = ujson.Obj(
    "fallback" -> jobs.mkString("CI jobs status: ", ", ", ""),
    "author_name" -> env("GITHUB_REPOSITORY"),
    "author_icon" -> "https://chat.ohze.net/api/v4/emoji/tu6nrabuftrk78rm78mapoq7to/image",
    "text" -> s"[CI jobs status]($link) for $text",
    "fields" -> jobs.map(_.asAttachmentField),
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

  val backend = DefaultSyncBackend()

  emptyRequest
    .body(data)
    .post(uri"$webhook")
    .send(backend)
}

final class MessageOnlyException(override val toString: String)
    extends RuntimeException(toString)

def boom(msg: String) = throw new MessageOnlyException(msg)

def envKeys(suffix: String): Seq[String] =
  Seq("MATTERMOST_", "SLACK_", "DEVOPS_").map(_ + suffix)

extension (m: Map[String, String])
  def any(keySuffix: String): Option[String] =
    envKeys(keySuffix).flatMap(m.get).headOption
