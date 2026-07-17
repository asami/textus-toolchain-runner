/*
 * @since   Jul.  2, 2026
 * @version Jul. 18, 2026
 * @author  ASAMI, Tomoharu
 */
package org.simplemodeling.textus.toolchainrunner

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.spi.{SpiContract, SpiSelection}
import org.goldenport.cncf.spi.toolchain.runner.{ConvertSvgPagesToPdfRequest, ToolchainRunner}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.toolchainrunner.impl

class ComponentFactorySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "ToolchainRunner component" should {
    "publish ToolchainRunner as a CNCF SPI provider" in {
      Given("a ToolchainRunner component and a fake docker command")
      val component = _component_under_test()
      given ExecutionContext = component.logic.executionContext()
      val dir = Files.createTempDirectory("textus-toolchain-runner-spi-spec")
      val svg = dir.resolve("main.svg")
      val out = dir.resolve("print.pdf")
      val fakedocker = _fake_docker(dir)
      Files.writeString(svg, """<svg xmlns="http://www.w3.org/2000/svg" width="100" height="50"/>""", StandardCharsets.UTF_8)

      When("the ToolchainRunner SPI provider is resolved from the component port")
      val provider = component.port.entries.collectFirst {
        case x: impl.TextusToolchainRunnerProvider => x
      }.get
      val spi = provider
        .provide(SpiContract("toolchain-runner", classOf[ToolchainRunner]), SpiSelection())
        .toOption
        .get
      val response = spi.convertSvgPagesToPdf(
        ConvertSvgPagesToPdfRequest(
          svgFiles = Vector(svg.toString),
          out = Some(out.toString),
          dockerCommand = Some(fakedocker.toString),
          dockerImage = Some("fake/textus-toolchain:latest")
        )
      ).toOption.get

      Then("the SPI delegates to the existing toolchain operation")
      response.valid shouldBe true
      response.pageCount shouldBe 1
      response.dockerImage shouldBe Some("fake/textus-toolchain:latest")
      Files.readString(out, StandardCharsets.UTF_8) should include ("fake pdf")
    }

    "convert SVG pages to a PDF through the configured Docker command" in {
      Given("a ToolchainRunner component and a fake docker command")
      val component = _component_under_test()
      given ExecutionContext = component.logic.executionContext()
      val dir = Files.createTempDirectory("textus-toolchain-runner-spec")
      val svg1 = dir.resolve("main.svg")
      val svg2 = dir.resolve("detail.svg")
      val out = dir.resolve("print.pdf")
      val fakedocker = _fake_docker(dir)
      Files.writeString(svg1, """<svg xmlns="http://www.w3.org/2000/svg" width="100" height="50"/>""", StandardCharsets.UTF_8)
      Files.writeString(svg2, """<svg xmlns="http://www.w3.org/2000/svg" width="100" height="50"/>""", StandardCharsets.UTF_8)

      When("convertSvgPagesToPdf is executed")
      val record = _operation_record(
        component,
        "convertSvgPagesToPdf",
        "svgFiles" -> s"${svg1.toString},${svg2.toString}",
        "out" -> out.toString,
        "dockerCommand" -> fakedocker.toString,
        "dockerImage" -> "fake/textus-toolchain:latest"
      )

      Then("a multi-page PDF artifact is reported")
      record.getBoolean("valid") shouldBe Some(true)
      record.getInt("pageCount") shouldBe Some(2)
      record.getString("dockerImage") shouldBe Some("fake/textus-toolchain:latest")
      Files.readString(out, StandardCharsets.UTF_8) should include ("fake pdf")
      Files.readString(dir.resolve("fake-docker-args.txt"), StandardCharsets.UTF_8) should include ("svg-pages-to-pdf")
    }

    "use the Textus toolchain release image by default" in {
      Given("a ToolchainRunner component and a fake docker command")
      val component = _component_under_test()
      given ExecutionContext = component.logic.executionContext()
      val dir = Files.createTempDirectory("textus-toolchain-runner-default-image-spec")
      val svg = dir.resolve("page.svg")
      val out = dir.resolve("print.pdf")
      val fakedocker = _fake_docker(dir)
      Files.writeString(svg, """<svg xmlns="http://www.w3.org/2000/svg" width="100" height="50"/>""", StandardCharsets.UTF_8)

      When("convertSvgToPdf is executed without dockerImage")
      val record = _operation_record(
        component,
        "convertSvgToPdf",
        "svg" -> svg.toString,
        "out" -> out.toString,
        "dockerCommand" -> fakedocker.toString
      )

      Then("the operation uses the toolchain image version that provides svg-pages-to-pdf")
      record.getBoolean("valid") shouldBe Some(true)
      record.getString("dockerImage") shouldBe Some("ghcr.io/asami/textus-toolchain:latest")
      Files.readString(dir.resolve("fake-docker-args.txt"), StandardCharsets.UTF_8) should include ("ghcr.io/asami/textus-toolchain:latest")
    }

    "own the Textus toolchain Docker assets" in {
      Given("the Textus toolchain Dockerfile, wrapper script, and packaged component developer manual")
      val root = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
      val dockerfile = Files.readString(root.resolve("docker/textus-toolchain/Dockerfile"), StandardCharsets.UTF_8)
      val script = Files.readString(root.resolve("docker/textus-toolchain/textus-toolchain"), StandardCharsets.UTF_8)
      val manual = Files.readString(root.resolve("src/main/web/docs/user-guide.html"), StandardCharsets.UTF_8)
      val manualmarkdown = Files.readString(root.resolve("src/main/web/docs/user-guide.md"), StandardCharsets.UTF_8)

      Then("the Dockerfile includes the BoK, PDF, and video runtime dependencies")
      dockerfile should include("asciidoctor-pdf")
      dockerfile should include("ARG ANTORA_VERSION=3.1.15")
      dockerfile should include("ARG ASCIIDOCTOR_KROKI_VERSION=0.18.1")
      dockerfile should include("ARG PLAYWRIGHT_VERSION=1.61.1")
      dockerfile should include("ARG REMOTION_VERSION=4.0.490")
      dockerfile should include("@antora/cli")
      dockerfile should include("ffmpeg")
      dockerfile should include("playwright")
      dockerfile should include("@remotion/cli")
      dockerfile should include("@remotion/renderer")
      dockerfile should include("remotion versions")
      dockerfile should include("python3-pil")
      dockerfile should include("whisper.cpp")
      dockerfile should include("ggml-base.bin")
      dockerfile should include("NODE_PATH")
      dockerfile should include("FROM ${KROKI_IMAGE}")

      And("the wrapper exposes deterministic toolchain checks and command pass-through")
      script should include("check_bok")
      script should include("check_pdf")
      script should include("check_kroki")
      script should include("check_video")
      script should include("require_command remotion")
      script should include("require.resolve('@remotion/cli')")
      script should include("remotion versions")
      script should include("svg_pages_to_pdf")
      script should include("svg-pages-to-pdf")
      script should include("render_web_page")
      script should include("render-web-page")
      script should include("request JSON is invalid")
      script should include("browser rendering failed")
      script should include("browserContextOptions")
      script should include("reviewedCompatibilityContext")
      script should include("sec-ch-ua-platform")
      script should include("AutomationControlled")
      script should include("Usage: svg-pages-to-pdf --out output.pdf")
      script should include("SVG file not found")
      script should include("preferCSSPageSize")
      script should include("node_with_global_modules")
      script should include("npm root -g")
      script should include("fs.existsSync(path)")
      script should include("kroki-server")
      script should include("SMARTDOX_KROKI_PORT=\"${SMARTDOX_KROKI_PORT:-9609}\"")
      script should include("textus-toolchain check ${target}: ok")
      script should include("exec \"$@\"")

      And("the CAR manual is the component developer source for direct Docker usage")
      manual should include("Textus Toolchain Runner")
      manual should include("Textus Component Developer Guide")
      manual should include("src/main/cozy/textus-toolchain-runner.cml")
      manual should include("/man/{component}/user-guide.html")
      manual should include("/man/{component}/user-guide.md")
      manual should include("/help/{component}")
      manual should include("Direct Docker Image Usage")
      manual should include("docker run --rm ghcr.io/asami/textus-toolchain:latest textus-toolchain check all")
      manual should include("svg-pages-to-pdf --out print.pdf")
      manualmarkdown should include("# Textus Toolchain Runner Component Developer Guide")
      manualmarkdown should include("/man/{component}/user-guide.md")
    }
  }

  private def _component_under_test(): impl.ToolchainRunnerPrimaryComponent = {
    val bundlefactory = new impl.ComponentFactory()
    val subsystem = new Subsystem(
      name = "toolchain-runner-spec",
      configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
    )
    val bundle = bundlefactory.create(ComponentCreate(subsystem, ComponentOrigin.Main))
    subsystem.add(bundle.participants)
    bundle.primary.asInstanceOf[impl.ToolchainRunnerPrimaryComponent]
  }

  private def _operation_record(
    component: impl.ToolchainRunnerPrimaryComponent,
    operation: String,
    properties: (String, String)*
  )(using ctx: ExecutionContext): Record = {
    val request = Request.of(
      component = ToolchainRunnerComponent.name,
      service = "Toolchain",
      operation = operation,
      properties = properties.map { case (k, v) => Property(k, v, None) }.toList
    )
    val action = component.logic.makeOperationRequest(request).TAKE
    component.logic.executeAction(action.asInstanceOf[org.goldenport.cncf.action.Action], ctx).TAKE match {
      case OperationResponse.RecordResponse(record) => record
      case other => fail(s"expected record response but got $other")
    }
  }

  private def _fake_docker(dir: Path): Path = {
    val path = dir.resolve("fake-docker.sh")
    val script =
      s"""#!/bin/sh
         |printf '%s\n' "$$@" > '${dir.resolve("fake-docker-args.txt").toString}'
         |out=''
         |prev=''
         |for arg in "$$@"; do
         |  if [ "$$prev" = '--out' ]; then out="$$arg"; fi
         |  prev="$$arg"
         |done
         |if [ -z "$$out" ]; then out='output.pdf'; fi
         |printf 'fake pdf\n' > "$$out"
         |exit 0
         |""".stripMargin
    Files.writeString(path, script, StandardCharsets.UTF_8)
    path.toFile.setExecutable(true)
    path
  }
}
