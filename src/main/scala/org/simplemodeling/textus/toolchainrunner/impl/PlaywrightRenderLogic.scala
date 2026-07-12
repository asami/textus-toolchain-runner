/*
 * @since   Jul. 12, 2026
 * @version Jul. 12, 2026
 * @author  ASAMI, Tomoharu
 */
package org.simplemodeling.textus.toolchainrunner.impl

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.util.Try
import io.circe.Json
import io.circe.parser.parse

final case class PlaywrightRenderRequest(
  url: String,
  waitUntil: Option[String],
  waitForSelector: Option[String],
  timeoutSeconds: Option[Int],
  userAgent: Option[String] = None,
  browserProfile: Option[String] = None
)

final case class PlaywrightRenderResponse(
  requestedUrl: String,
  finalUrl: String,
  status: Option[Int],
  title: Option[String],
  html: String,
  renderedAt: String,
  engine: String,
  browser: String
)

final case class PreparedPlaywrightRender(
  work: Path,
  responseFile: Path,
  command: Vector[String]
)

object PlaywrightRenderLogic {
  private val _supported_wait_modes: Set[String] = Set("load", "domcontentloaded", "networkidle")
  private val _supported_browser_profiles: Set[String] = Set("standard", "reviewed_site_compatibility")

  def prepare(
    request: PlaywrightRenderRequest,
    dockerimage: String,
    dockercommand: String,
    work: Path
  ): Either[String, PreparedPlaywrightRender] =
    for {
      normalized <- validate(request)
      prepared <- Try {
        Files.createDirectories(work)
        val requestfile = work.resolve("render-request.json")
        val responsefile = work.resolve("render-response.json")
        Files.writeString(requestfile, _request_json(normalized).noSpaces, StandardCharsets.UTF_8)
        PreparedPlaywrightRender(
          work,
          responsefile,
          Vector(
            dockercommand,
            "run",
            "--rm",
            "-v",
            s"${work.toAbsolutePath.normalize.toString}:/work",
            "-w",
            "/work",
            dockerimage,
            "render-web-page",
            "--request",
            requestfile.getFileName.toString,
            "--response",
            responsefile.getFileName.toString
          )
        )
      }.toEither.left.map(e => s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}")
    } yield prepared

  def validate(request: PlaywrightRenderRequest): Either[String, PlaywrightRenderRequest] =
    if (!request.url.startsWith("http://") && !request.url.startsWith("https://"))
      Left("url must use http or https")
    else if (request.waitUntil.exists(x => !_supported_wait_modes.contains(x)))
      Left(s"unsupported waitUntil: ${request.waitUntil.get}")
    else if (request.timeoutSeconds.exists(x => x < 1 || x > 120))
      Left("timeoutSeconds must be between 1 and 120")
    else if (request.userAgent.exists(x => x.length > 512 || x.exists(_.isControl)))
      Left("userAgent must be at most 512 characters and contain no control characters")
    else if (request.browserProfile.exists(x => !_supported_browser_profiles.contains(x)))
      Left(s"unsupported browserProfile: ${request.browserProfile.get}")
    else if (request.browserProfile.contains("reviewed_site_compatibility") && request.userAgent.forall(x => !x.matches(".*Chrome/[0-9]+.*")))
      Left("reviewed_site_compatibility requires a Google Chrome userAgent")
    else
      Right(request.copy(
        waitUntil = request.waitUntil.orElse(Some("load")),
        waitForSelector = request.waitForSelector.map(_.trim).filter(_.nonEmpty),
        timeoutSeconds = request.timeoutSeconds.orElse(Some(30)),
        userAgent = request.userAgent.map(_.trim).filter(_.nonEmpty),
        browserProfile = request.browserProfile.map(_.trim).filter(_.nonEmpty).orElse(Some("standard"))
      ))

  def cleanup(prepared: PreparedPlaywrightRender, removework: Boolean): Unit = {
    Try(Files.deleteIfExists(prepared.responseFile))
    Try(Files.deleteIfExists(prepared.work.resolve("render-request.json")))
    if (removework)
      Try(Files.deleteIfExists(prepared.work))
  }

  def parseResponse(path: Path): Either[String, PlaywrightRenderResponse] =
    for {
      text <- Try(Files.readString(path, StandardCharsets.UTF_8)).toEither.left.map(_ => "render response file is missing or unreadable")
      json <- parse(text).left.map(_ => "render response is not valid JSON")
      cursor = json.hcursor
      requestedurl <- cursor.get[String]("requestedUrl").left.map(_ => "render response requestedUrl is missing")
      finalurl <- cursor.get[String]("finalUrl").left.map(_ => "render response finalUrl is missing")
      html <- cursor.get[String]("html").left.map(_ => "render response html is missing")
      renderedat <- cursor.get[String]("renderedAt").left.map(_ => "render response renderedAt is missing")
      engine <- cursor.get[String]("engine").left.map(_ => "render response engine is missing")
      browser <- cursor.get[String]("browser").left.map(_ => "render response browser is missing")
    } yield PlaywrightRenderResponse(
      requestedurl,
      finalurl,
      cursor.get[Int]("status").toOption,
      cursor.get[String]("title").toOption.filter(_.nonEmpty),
      html,
      renderedat,
      engine,
      browser
    )

  private def _request_json(request: PlaywrightRenderRequest): Json =
    Json.obj(
      "url" -> Json.fromString(request.url),
      "waitUntil" -> request.waitUntil.map(Json.fromString).getOrElse(Json.Null),
      "waitForSelector" -> request.waitForSelector.map(Json.fromString).getOrElse(Json.Null),
      "timeoutSeconds" -> request.timeoutSeconds.map(Json.fromInt).getOrElse(Json.Null),
      "userAgent" -> request.userAgent.map(Json.fromString).getOrElse(Json.Null),
      "browserProfile" -> request.browserProfile.map(Json.fromString).getOrElse(Json.Null)
    )
}
