package org.simplemodeling.textus.toolchainrunner.impl

import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import scala.util.Try
import cats.syntax.all.*
import org.goldenport.cncf.action.{ActionCall, ActionCallShellCommandPart}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId}
import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.process.{ShellCommand, ShellCommandResult}
import org.goldenport.record.Record
import org.simplemodeling.textus.toolchainrunner.ToolchainRunnerComponent

final class ComponentFactory extends ToolchainRunnerParticipantFactoryBase with Component.PrimaryComponentFactory {
  override protected def create_Component(params: ComponentCreate): Component =
    ToolchainRunnerPrimaryFactory.configureSpi(new ToolchainRunnerPrimaryComponent())

  override protected def create_Core(
    params: ComponentCreate,
    comp: Component
  ): Component.Core =
    component_core(ToolchainRunnerComponent.name, ToolchainRunnerComponent.componentId)
}

abstract class ToolchainRunnerParticipantFactoryBase extends ToolchainRunnerComponent.Factory {
  protected final val shared_services =
    Vector(
      ToolchainRunnerComponent.ToolchainService
    )

  protected final def component_core(
    name: String,
    componentid: ComponentId
  ): Component.Core =
    spec_create(name, componentid, shared_services)

  override val Toolchain: ToolchainRunnerComponent.ToolchainServiceFactory =
    DefaultToolchainServiceFactory()
}

final class ToolchainRunnerPrimaryComponent extends ToolchainRunnerComponent

object ToolchainRunnerPrimaryFactory extends ToolchainRunnerParticipantFactoryBase with Component.PrimaryComponentFactory {
  private[impl] def configureSpi(
    component: ToolchainRunnerPrimaryComponent
  ): Component =
    component.withPort(
      Component.Port
        .of(new TextusToolchainRunnerProvider(component))
        .orElse(component.port)
    )

  override protected def create_Component(params: ComponentCreate): Component =
    configureSpi(new ToolchainRunnerPrimaryComponent())

  override protected def create_Core(
    params: ComponentCreate,
    comp: Component
  ): Component.Core =
    component_core(ToolchainRunnerComponent.name, ToolchainRunnerComponent.componentId)
}

final class DefaultToolchainServiceFactory extends ToolchainRunnerComponent.ToolchainServiceFactory {
  import ToolchainRunnerComponent.ToolchainService.*

  private final case class ToolchainRunResult(
    out: Path,
    pagecount: Int,
    dockerimage: String
  )

  override def createConvertSvgToPdfActionCall(
    core: ActionCall.Core,
    action: ConvertSvgToPdfQuery
  ): ConvertSvgToPdfActionCall =
    ConvertSvgToPdfActionCallImpl(core, action)

  override def createConvertSvgPagesToPdfActionCall(
    core: ActionCall.Core,
    action: ConvertSvgPagesToPdfQuery
  ): ConvertSvgPagesToPdfActionCall =
    ConvertSvgPagesToPdfActionCallImpl(core, action)

  override def createRenderWebPageActionCall(
    core: ActionCall.Core,
    action: RenderWebPageQuery
  ): RenderWebPageActionCall =
    RenderWebPageActionCallImpl(core, action)

  private final case class ConvertSvgToPdfActionCallImpl(
    core: ActionCall.Core,
    override val action: ConvertSvgToPdfQuery
  ) extends ConvertSvgToPdfActionCall with ActionCallShellCommandPart {
    protected def build_Program: ExecUowM[OperationResponse] = {
      val svg = action.record.getString("svg").getOrElse("")
      val out = action.record.getString("out").filter(_.nonEmpty).
        getOrElse(_default_pdf_out(svg, "output.pdf"))
      val dockerimage = _docker_image(action.record.getString("dockerImage"), config_string)
      val dockercommand = action.record.getString("dockerCommand").filter(_.nonEmpty).getOrElse("docker")
      val workdir = action.record.getString("workDir").filter(_.nonEmpty).map(Paths.get(_))
      _convert_svg_pages_to_pdf(
        Vector(svg),
        Paths.get(out),
        dockerimage,
        dockercommand,
        workdir,
        exec_pure,
        (command, work) =>
          shell_exec(ShellCommand(command, Some(work))).map(_shell_result(command, _))
      ).map(result => _to_response(result, out))
    }
  }

  private final case class ConvertSvgPagesToPdfActionCallImpl(
    core: ActionCall.Core,
    override val action: ConvertSvgPagesToPdfQuery
  ) extends ConvertSvgPagesToPdfActionCall with ActionCallShellCommandPart {
    protected def build_Program: ExecUowM[OperationResponse] = {
      val svgfiles = _split_svg_files(action.record.getString("svgFiles").getOrElse(""))
      val out = action.record.getString("out").filter(_.nonEmpty).getOrElse("print.pdf")
      val dockerimage = _docker_image(action.record.getString("dockerImage"), config_string)
      val dockercommand = action.record.getString("dockerCommand").filter(_.nonEmpty).getOrElse("docker")
      val workdir = action.record.getString("workDir").filter(_.nonEmpty).map(Paths.get(_))
      _convert_svg_pages_to_pdf(
        svgfiles,
        Paths.get(out),
        dockerimage,
        dockercommand,
        workdir,
        exec_pure,
        (command, work) =>
          shell_exec(ShellCommand(command, Some(work))).map(_shell_result(command, _))
      ).map(result => _to_response(result, out))
    }
  }

  private final case class RenderWebPageActionCallImpl(
    core: ActionCall.Core,
    override val action: RenderWebPageQuery
  ) extends RenderWebPageActionCall with ActionCallShellCommandPart {
    protected def build_Program: ExecUowM[OperationResponse] = {
      val request = PlaywrightRenderRequest(
        action.record.getString("url").getOrElse(""),
        action.record.getString("waitUntil").filter(_.nonEmpty),
        action.record.getString("waitForSelector").filter(_.nonEmpty),
        action.record.getInt("timeoutSeconds")
      )
      val dockerimage = _docker_image(action.record.getString("dockerImage"), config_string)
      val dockercommand = action.record.getString("dockerCommand").filter(_.nonEmpty).getOrElse("docker")
      val configuredwork = action.record.getString("workDir").filter(_.nonEmpty).map(Paths.get(_))
      val work = configuredwork.getOrElse(Files.createTempDirectory("textus-toolchain-render-"))
      PlaywrightRenderLogic.prepare(request, dockerimage, dockercommand, work) match {
        case Left(message) =>
          exec_from(org.goldenport.Consequence.resourceInvalid(message))
        case Right(prepared) =>
          shell_exec(ShellCommand(prepared.command, Some(prepared.work))).flatMap { result =>
            val response =
              if (result.exitCode != 0)
                org.goldenport.Consequence.serviceUnavailable(s"Playwright render command failed with exit ${result.exitCode}")
              else
                PlaywrightRenderLogic.parseResponse(prepared.responseFile) match {
                  case Right(value) => org.goldenport.Consequence.success(_render_response(value))
                  case Left(message) => org.goldenport.Consequence.operationInvalid(message)
                }
            PlaywrightRenderLogic.cleanup(prepared, configuredwork.isEmpty)
            exec_from(response)
          }
      }
    }
  }

  private def _convert_svg_pages_to_pdf(
    svgfiles: Vector[String],
    out: Path,
    dockerimage: String,
    dockercommand: String,
    workdir: Option[Path],
    pure: Either[String, ToolchainRunResult] => ExecUowM[Either[String, ToolchainRunResult]],
    runshell: (Vector[String], Path) => ExecUowM[Either[String, String]]
  ): ExecUowM[Either[String, ToolchainRunResult]] = {
    val inputs = svgfiles.map(s => Paths.get(s).toAbsolutePath.normalize)
    val validation: Either[String, Vector[Path]] =
      if (inputs.isEmpty)
        Left("svgFiles must contain at least one SVG file")
      else
        inputs.find(path => !Files.isRegularFile(path)).
          map(path => Left(s"SVG file not found: ${path.toString}")).
          getOrElse(Right(inputs))
    validation.flatMap { checkedinputs =>
      Try {
        val work = workdir.map(_.toAbsolutePath.normalize).
          getOrElse(Files.createTempDirectory("textus-toolchain-runner-"))
        Files.createDirectories(work)
        val pages = checkedinputs.zipWithIndex.map { case (input, index) =>
          val target = work.resolve(f"page-${index + 1}%03d.svg")
          Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
          target.getFileName.toString
        }
        val workout = work.resolve("output.pdf")
        val command = Vector(
          dockercommand,
          "run",
          "--rm",
          "-v",
          s"${work.toString}:/work",
          "-w",
          "/work",
          dockerimage,
          "svg-pages-to-pdf",
          "--out",
          workout.getFileName.toString
        ) ++ pages
        Right((command, work, workout, pages.length))
      }.toEither.left.map(e => s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}").flatten
    } match {
      case Left(message) => pure(Left(message))
      case Right((command, work, workout, pagecount)) =>
        runshell(command, work).map { result =>
          result.flatMap { _ =>
            if (!Files.isRegularFile(workout))
              Left("toolchain did not create output.pdf")
            else {
              val outabs = out.toAbsolutePath.normalize
              val parent = outabs.getParent
              if (parent != null)
                Files.createDirectories(parent)
              Files.copy(workout, outabs, StandardCopyOption.REPLACE_EXISTING)
              Right(ToolchainRunResult(outabs, pagecount, dockerimage))
            }
          }
        }
    }
  }

  private def _shell_result(
    command: Vector[String],
    result: ShellCommandResult
  ): Either[String, String] =
    if (result.exitCode == 0)
      Right(result.stdout.asStringUnsafe())
    else {
      val output = Vector(
        result.stdout.asStringUnsafe(),
        result.stderr.asStringUnsafe()
      ).filter(_.nonEmpty).mkString("\n")
      Left(s"toolchain command failed with exit ${result.exitCode}: ${command.mkString(" ")}\n$output")
    }

  private def _split_svg_files(value: String): Vector[String] =
    value.split("[\n,]").toVector.map(_.trim).filter(_.nonEmpty)

  private def _default_pdf_out(svg: String, fallback: String): String = {
    val trimmed = svg.trim
    if (trimmed.isEmpty)
      fallback
    else {
      val path = Paths.get(trimmed)
      val name = path.getFileName.toString
      val stem = if (name.toLowerCase.endsWith(".svg")) name.substring(0, name.length - 4) else name
      val sibling = Option(path.getParent).map(_.resolve(stem + ".pdf")).getOrElse(Paths.get(stem + ".pdf"))
      sibling.toString
    }
  }

  private def _docker_image(
    explicit: Option[String],
    config: String => Option[String]
  ): String =
    explicit.filter(_.nonEmpty).
      orElse(_docker_image_config(config)).
      orElse(sys.env.get("TEXTUS_TOOLCHAIN_DOCKER_IMAGE").filter(_.nonEmpty)).
      orElse(sys.env.get("COZY_DOCKER_IMAGE").filter(_.nonEmpty)).
      getOrElse("ghcr.io/asami/textus-toolchain:latest")

  private def _docker_image_config(config: String => Option[String]): Option[String] =
    Vector(
      "textus.toolchain-runner.docker.image",
      "textus.toolchainRunner.docker.image",
      "textus.toolchain.docker.image",
      "cozy.toolchain.docker.image",
      "cozy.docker-image",
      "cozy.docker.image"
    ).flatMap(config).map(_.trim).find(_.nonEmpty)

  private def _to_response(result: Either[String, ToolchainRunResult], out: String): OperationResponse =
    result match {
      case Right(value) =>
        OperationResponse.RecordResponse(Record.data(
          "valid" -> true,
          "errorCount" -> 0,
          "warningCount" -> 0,
          "message" -> s"created PDF: ${value.out.toString}",
          "out" -> value.out.toString,
          "pageCount" -> value.pagecount,
          "dockerImage" -> value.dockerimage
        ))
      case Left(message) =>
        OperationResponse.RecordResponse(Record.data(
          "valid" -> false,
          "errorCount" -> 1,
          "warningCount" -> 0,
          "message" -> message,
          "out" -> out,
          "pageCount" -> 0,
          "dockerImage" -> ""
        ))
    }

  private def _render_response(response: PlaywrightRenderResponse): OperationResponse =
    OperationResponse.RecordResponse(Record.data(
      "requestedUrl" -> response.requestedUrl,
      "finalUrl" -> response.finalUrl,
      "status" -> response.status,
      "title" -> response.title,
      "html" -> response.html,
      "renderedAt" -> response.renderedAt,
      "engine" -> response.engine,
      "browser" -> response.browser
    ))
}

object DefaultToolchainServiceFactory {
  def apply(): DefaultToolchainServiceFactory = new DefaultToolchainServiceFactory()
}
