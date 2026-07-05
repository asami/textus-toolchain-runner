package org.simplemodeling.textus.toolchainrunner

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
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
          dockerImage = Some("fake/cozy-toolchain:latest")
        )
      ).toOption.get

      Then("the SPI delegates to the existing toolchain operation")
      response.valid shouldBe true
      response.pageCount shouldBe 1
      response.dockerImage shouldBe Some("fake/cozy-toolchain:latest")
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
        "dockerImage" -> "fake/cozy-toolchain:latest"
      )

      Then("a multi-page PDF artifact is reported")
      record.getBoolean("valid") shouldBe Some(true)
      record.getInt("pageCount") shouldBe Some(2)
      record.getString("dockerImage") shouldBe Some("fake/cozy-toolchain:latest")
      Files.readString(out, StandardCharsets.UTF_8) should include ("fake pdf")
      Files.readString(dir.resolve("fake-docker-args.txt"), StandardCharsets.UTF_8) should include ("svg-pages-to-pdf")
    }

    "use the Cozy toolchain release image by default" in {
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
      record.getString("dockerImage") shouldBe Some("ghcr.io/asami/cozy-toolchain:latest")
      Files.readString(dir.resolve("fake-docker-args.txt"), StandardCharsets.UTF_8) should include ("ghcr.io/asami/cozy-toolchain:latest")
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
