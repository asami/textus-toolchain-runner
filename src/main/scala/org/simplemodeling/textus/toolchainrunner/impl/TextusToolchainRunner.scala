/*
 * @since   Jul.  4, 2026
 * @version Jul. 12, 2026
 * @author  ASAMI, Tomoharu
 */
package org.simplemodeling.textus.toolchainrunner.impl

import org.goldenport.Consequence
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.spi.{SpiContract, SpiProvider, SpiSelection}
import org.goldenport.cncf.spi.toolchain.runner.*
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.simplemodeling.textus.toolchainrunner.ToolchainRunnerComponent

/*
 * CNCF ToolchainRunner SPI adapter backed by existing Textus ToolchainRunner
 * component operations.
 *
 * @since   Jul.  3, 2026
 * @version Jul. 12, 2026
 * @author  ASAMI, Tomoharu
 */
final class TextusToolchainRunner(
  component: Component
) extends ToolchainRunner {
  def convertSvgToPdf(req: ConvertSvgToPdfRequest)(using ExecutionContext): Consequence[ToolchainArtifactResponse] =
    _operation_record(
      "convertSvgToPdf",
      _properties(
        "svg" -> Some(req.svg),
        "out" -> req.out,
        "dockerImage" -> req.dockerImage,
        "dockerCommand" -> req.dockerCommand,
        "workDir" -> req.workDir
      )
    ).map(_to_response)

  def convertSvgPagesToPdf(req: ConvertSvgPagesToPdfRequest)(using ExecutionContext): Consequence[ToolchainArtifactResponse] =
    _operation_record(
      "convertSvgPagesToPdf",
      _properties(
        "svgFiles" -> Some(req.svgFiles.mkString(",")),
        "out" -> req.out,
        "dockerImage" -> req.dockerImage,
        "dockerCommand" -> req.dockerCommand,
        "workDir" -> req.workDir
      )
    ).map(_to_response)

  override def renderWebPage(req: RenderWebPageRequest)(using ExecutionContext): Consequence[RenderedWebPageResponse] =
    _operation_record(
      "renderWebPage",
      _properties(
        "url" -> Some(req.url),
        "waitUntil" -> req.waitUntil,
        "waitForSelector" -> req.waitForSelector,
        "timeoutSeconds" -> req.timeoutSeconds.map(_.toString),
        "userAgent" -> req.userAgent,
        "browserProfile" -> req.browserProfile
      )
    ).flatMap(_to_rendered_web_page_response)

  private def _operation_record(
    operation: String,
    properties: Seq[Property]
  )(using ctx: ExecutionContext): Consequence[Record] = {
    val request = Request.of(
      component = ToolchainRunnerComponent.name,
      service = "Toolchain",
      operation = operation,
      properties = properties.toList
    )
    for {
      action <- component.logic.makeOperationRequest(request).map(_.asInstanceOf[Action])
      response <- component.logic.executeAction(action, ctx)
      record <- response match {
        case OperationResponse.RecordResponse(record) => Consequence.success(record)
        case other => Consequence.operationInvalid(s"ToolchainRunner SPI expected record response but got $other")
      }
    } yield record
  }

  private def _properties(
    values: (String, Option[String])*
  ): Seq[Property] =
    values.toVector.flatMap { case (key, value) =>
      value.filter(_.nonEmpty).map(v => Property(key, v, None))
    }

  private def _to_response(
    record: Record
  ): ToolchainArtifactResponse =
    ToolchainArtifactResponse(
      valid = record.getBoolean("valid").getOrElse(false),
      errorCount = record.getInt("errorCount").getOrElse(0),
      warningCount = record.getInt("warningCount").getOrElse(0),
      message = record.getString("message").getOrElse(""),
      out = record.getString("out").filter(_.nonEmpty),
      pageCount = record.getInt("pageCount").getOrElse(0),
      dockerImage = record.getString("dockerImage").filter(_.nonEmpty)
    )

  private def _to_rendered_web_page_response(record: Record): Consequence[RenderedWebPageResponse] =
    for {
      requestedurl <- record.getString("requestedUrl").toRight("renderWebPage response requestedUrl is missing").fold(Consequence.operationInvalid, Consequence.success)
      finalurl <- record.getString("finalUrl").toRight("renderWebPage response finalUrl is missing").fold(Consequence.operationInvalid, Consequence.success)
      html <- record.getString("html").toRight("renderWebPage response html is missing").fold(Consequence.operationInvalid, Consequence.success)
      renderedat <- record.getString("renderedAt").toRight("renderWebPage response renderedAt is missing").fold(Consequence.operationInvalid, Consequence.success)
      engine <- record.getString("engine").toRight("renderWebPage response engine is missing").fold(Consequence.operationInvalid, Consequence.success)
      browser <- record.getString("browser").toRight("renderWebPage response browser is missing").fold(Consequence.operationInvalid, Consequence.success)
    } yield RenderedWebPageResponse(
      requestedurl,
      finalurl,
      record.getInt("status"),
      record.getString("title").filter(_.nonEmpty),
      html,
      renderedat,
      engine,
      browser
    )
}

final class TextusToolchainRunnerProvider(
  component: Component
) extends SpiProvider[ToolchainRunner] {
  def supports(
    contract: SpiContract[ToolchainRunner],
    selection: SpiSelection
  )(using ExecutionContext): Boolean =
    contract.name == "toolchain-runner" &&
      contract.runtimeClass == classOf[ToolchainRunner]

  def provide(
    contract: SpiContract[ToolchainRunner],
    selection: SpiSelection
  )(using ExecutionContext): Consequence[ToolchainRunner] =
    Consequence.success(new TextusToolchainRunner(component))
}
