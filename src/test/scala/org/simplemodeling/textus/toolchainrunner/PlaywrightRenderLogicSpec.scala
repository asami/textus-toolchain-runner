/*
 * @since   Jul. 12, 2026
 * @version Jul. 12, 2026
 * @author  ASAMI, Tomoharu
 */
package org.simplemodeling.textus.toolchainrunner

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.toolchainrunner.impl.{PlaywrightRenderLogic, PlaywrightRenderRequest}

final class PlaywrightRenderLogicSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "PlaywrightRenderLogic" should {
    "prepare a file-based Docker command without exposing the target URL" in {
      Given("a valid dynamic page rendering request")
      val work = Files.createTempDirectory("playwright-render-logic-spec")
      val request = PlaywrightRenderRequest(
        "https://museum.example/exhibitions?token=secret",
        Some("networkidle"),
        Some("#exhibitions"),
        Some(45)
      )

      When("the deterministic toolchain command is prepared")
      val prepared = PlaywrightRenderLogic.prepare(request, "textus-toolchain:test", "docker", work).toOption.get

      Then("the command carries only request and response file names")
      prepared.command should contain allOf ("render-web-page", "render-request.json", "render-response.json")
      prepared.command.mkString(" ") should not include "museum.example"
      prepared.command.mkString(" ") should not include "token=secret"

      And("the complete request is stored structurally in the private workspace")
      val requestjson = Files.readString(work.resolve("render-request.json"), StandardCharsets.UTF_8)
      requestjson should include("museum.example")
      requestjson should include("networkidle")
      requestjson should include("#exhibitions")

      And("the structured request workspace can be removed after execution")
      PlaywrightRenderLogic.cleanup(prepared, removework = true)
      Files.exists(work) shouldBe false
    }

    "parse one structured Playwright response" in {
      Given("a response file emitted by the browser command")
      val responsefile = Files.createTempFile("playwright-render-response", ".json")
      Files.writeString(responsefile,
        """{"requestedUrl":"https://museum.example/","finalUrl":"https://museum.example/current","status":200,"title":"Museum","html":"<main>Current</main>","renderedAt":"2026-07-12T00:00:00Z","engine":"playwright","browser":"chromium"}""",
        StandardCharsets.UTF_8
      )

      When("the response is decoded")
      val response = PlaywrightRenderLogic.parseResponse(responsefile).toOption.get

      Then("all provider-neutral fields remain available")
      response.finalUrl shouldBe "https://museum.example/current"
      response.status shouldBe Some(200)
      response.html should include("Current")
      response.engine shouldBe "playwright"
      response.browser shouldBe "chromium"
    }

    "reject unsafe or unsupported request controls before tool execution" in {
      Given("invalid URL, wait mode, and timeout requests")
      val requests = Vector(
        PlaywrightRenderRequest("file:///tmp/page.html", None, None, None),
        PlaywrightRenderRequest("https://museum.example/", Some("forever"), None, None),
        PlaywrightRenderRequest("https://museum.example/", None, None, Some(121))
      )

      When("the requests are validated")
      val results = requests.map(PlaywrightRenderLogic.validate)

      Then("each invalid browser request is rejected deterministically")
      results.forall(_.isLeft) shouldBe true
    }
  }
}
