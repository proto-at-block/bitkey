package build.wallet.f8e.client.plugins

import build.wallet.f8e.auth.ActionProof
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

class ActionProofPluginTests : FunSpec({
  test("adds Action-Proof header when action proof attribute is set") {
    var capturedHeader: String? = null

    val client = HttpClient(
      MockEngine { request ->
        capturedHeader = request.headers["Action-Proof"]
        respond("OK", HttpStatusCode.OK)
      }
    ) {
      install(ActionProofPlugin)
    }

    val actionProof = ActionProof(
      version = 1,
      signatures = listOf("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef00"),
      nonce = "test-nonce"
    )

    client.get("/test") {
      withActionProof(actionProof)
    }

    capturedHeader shouldNotBe null
    capturedHeader!! shouldEqualJson """
      {
        "version": 1,
        "signatures": ["0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef00"],
        "nonce": "test-nonce"
      }
    """.trimIndent()

    client.close()
  }

  test("does not add Action-Proof header when action proof attribute is not set") {
    var capturedHeader: String? = null

    val client = HttpClient(
      MockEngine { request ->
        capturedHeader = request.headers["Action-Proof"]
        respond("OK", HttpStatusCode.OK)
      }
    ) {
      install(ActionProofPlugin)
    }

    client.get("/test")

    capturedHeader shouldBe null

    client.close()
  }

  test("omits null nonce from JSON serialization") {
    var capturedHeader: String? = null

    val client = HttpClient(
      MockEngine { request ->
        capturedHeader = request.headers["Action-Proof"]
        respond("OK", HttpStatusCode.OK)
      }
    ) {
      install(ActionProofPlugin)
    }

    val actionProof = ActionProof(
      version = 1,
      signatures = listOf("signature1")
    )

    client.get("/test") {
      withActionProof(actionProof)
    }

    capturedHeader shouldNotBe null
    capturedHeader!! shouldEqualJson """
      {
        "version": 1,
        "signatures": ["signature1"]
      }
    """.trimIndent()

    client.close()
  }
})
