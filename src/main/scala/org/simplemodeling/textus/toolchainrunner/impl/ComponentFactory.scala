package org.simplemodeling.textus.toolchainrunner.impl

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import scala.jdk.CollectionConverters.*
import scala.util.Try
import org.goldenport.cncf.action.{ActionCall}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId}
import org.goldenport.cncf.config.{RuntimeConfig}
import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.simplemodeling.textus.toolchainrunner.ToolchainRunnerComponent

final class ComponentFactory extends Component.BundleFactory {
  def primaryFactory: Component.PrimaryComponentFactory =
    ToolchainRunnerPrimaryFactory

  override def componentletFactories: Vector[Component.ComponentletFactory] =
    Vector.empty
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
  override protected def create_Component(params: ComponentCreate): Component =
    new ToolchainRunnerPrimaryComponent()

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
    pageCount: Int,
    dockerImage: String
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

  private final case class ConvertSvgToPdfActionCallImpl(
    core: ActionCall.Core,
    override val action: ConvertSvgToPdfQuery
  ) extends ConvertSvgToPdfActionCall {
    protected def build_Program: ExecUowM[OperationResponse] = {
      val svg = action.record.getString("svg").getOrElse("")
      val out = action.record.getString("out").filter(_.nonEmpty).
        getOrElse(_default_pdf_out(svg, "output.pdf"))
      val dockerimage = _docker_image(core, action.record.getString("dockerImage"))
      val dockercommand = action.record.getString("dockerCommand").filter(_.nonEmpty).getOrElse("docker")
      val workdir = action.record.getString("workDir").filter(_.nonEmpty).map(Paths.get(_))
      val result = _convert_svg_pages_to_pdf(
        Vector(svg),
        Paths.get(out),
        dockerimage,
        dockercommand,
        workdir
      )
      exec_pure(_to_response(result, out))
    }
  }

  private final case class ConvertSvgPagesToPdfActionCallImpl(
    core: ActionCall.Core,
    override val action: ConvertSvgPagesToPdfQuery
  ) extends ConvertSvgPagesToPdfActionCall {
    protected def build_Program: ExecUowM[OperationResponse] = {
      val svgfiles = _split_svg_files(action.record.getString("svgFiles").getOrElse(""))
      val out = action.record.getString("out").filter(_.nonEmpty).getOrElse("print.pdf")
      val dockerimage = _docker_image(core, action.record.getString("dockerImage"))
      val dockercommand = action.record.getString("dockerCommand").filter(_.nonEmpty).getOrElse("docker")
      val workdir = action.record.getString("workDir").filter(_.nonEmpty).map(Paths.get(_))
      val result = _convert_svg_pages_to_pdf(
        svgfiles,
        Paths.get(out),
        dockerimage,
        dockercommand,
        workdir
      )
      exec_pure(_to_response(result, out))
    }
  }

  private def _convert_svg_pages_to_pdf(
    svgfiles: Vector[String],
    out: Path,
    dockerimage: String,
    dockercommand: String,
    workdir: Option[Path]
  ): Either[String, ToolchainRunResult] = {
    val inputs = svgfiles.map(s => Paths.get(s).toAbsolutePath.normalize)
    val validation: Either[String, Vector[Path]] =
      if (inputs.isEmpty)
        Left("svgFiles must contain at least one SVG file")
      else
        inputs.find(path => !Files.isRegularFile(path)).
          map(path => Left(s"SVG file not found: ${path.toString}")).
          getOrElse(Right(inputs))
    validation.flatMap { checkedInputs =>
      Try {
        val work = workdir.map(_.toAbsolutePath.normalize).
          getOrElse(Files.createTempDirectory("textus-toolchain-runner-"))
        Files.createDirectories(work)
        val pages = checkedInputs.zipWithIndex.map { case (input, index) =>
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
        _run(command, work).flatMap { _ =>
          if (!Files.isRegularFile(workout))
            Left("toolchain did not create output.pdf")
          else {
            val outabs = out.toAbsolutePath.normalize
            val parent = outabs.getParent
            if (parent != null)
              Files.createDirectories(parent)
            Files.copy(workout, outabs, StandardCopyOption.REPLACE_EXISTING)
            Right(ToolchainRunResult(outabs, pages.length, dockerimage))
          }
        }
      }.toEither.left.map(e => s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}").flatten
    }
  }

  private def _run(command: Vector[String], cwd: Path): Either[String, String] = {
    val process = ProcessBuilder(command.asJava).
      directory(cwd.toFile).
      redirectErrorStream(true).
      start()
    val output = String(process.getInputStream.readAllBytes(), StandardCharsets.UTF_8)
    val exit = process.waitFor()
    if (exit == 0)
      Right(output)
    else
      Left(s"toolchain command failed with exit $exit: ${command.mkString(" ")}\n$output")
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

  private def _docker_image(core: ActionCall.Core, explicit: Option[String]): String =
    explicit.filter(_.nonEmpty).
      orElse(core.component.flatMap(_.subsystem).map(_.configuration).flatMap(_docker_image)).
      orElse(sys.env.get("TEXTUS_TOOLCHAIN_DOCKER_IMAGE").filter(_.nonEmpty)).
      orElse(sys.env.get("COZY_DOCKER_IMAGE").filter(_.nonEmpty)).
      getOrElse("ghcr.io/asami/cozy-toolchain:0.2.24-SNAPSHOT")

  private def _docker_image(conf: ResolvedConfiguration): Option[String] =
    Vector(
      "textus.toolchain-runner.docker.image",
      "textus.toolchainRunner.docker.image",
      "textus.toolchain.docker.image",
      "cozy.toolchain.docker.image",
      "cozy.docker-image",
      "cozy.docker.image"
    ).flatMap(key => RuntimeConfig.getString(conf, key)).map(_.trim).find(_.nonEmpty)

  private def _to_response(result: Either[String, ToolchainRunResult], out: String): OperationResponse =
    result match {
      case Right(value) =>
        OperationResponse.RecordResponse(Record.data(
          "valid" -> true,
          "errorCount" -> 0,
          "warningCount" -> 0,
          "message" -> s"created PDF: ${value.out.toString}",
          "out" -> value.out.toString,
          "pageCount" -> value.pageCount,
          "dockerImage" -> value.dockerImage
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
}

object DefaultToolchainServiceFactory {
  def apply(): DefaultToolchainServiceFactory = new DefaultToolchainServiceFactory()
}
