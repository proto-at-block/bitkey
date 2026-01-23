package build.wallet.f8e.client.plugins

import build.wallet.f8e.client.OfflineOperationException
import build.wallet.platform.connectivity.InternetConnectionCheckerFake
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

class InternetConnectionPluginTests : FunSpec({
  val checker = InternetConnectionCheckerFake()

  beforeTest {
    checker.reset()
  }

  test("request proceeds when internet connection is available") {
    val client = HttpClient(MockEngine { respond("OK", HttpStatusCode.OK) }) {
      install(InternetConnectionPlugin) {
        internetConnectionChecker = checker
      }
    }

    checker.connected = true

    val response = client.get("/test")
    response.status shouldBe HttpStatusCode.OK

    client.close()
  }

  test("throws OfflineOperationException when internet connection is not available") {
    val client = HttpClient(MockEngine { respond("OK", HttpStatusCode.OK) }) {
      install(InternetConnectionPlugin) {
        internetConnectionChecker = checker
      }
    }

    checker.connected = false

    shouldThrow<OfflineOperationException> {
      client.get("/test")
    }

    client.close()
  }

  test("request proceeds when no checker is provided") {
    val client = HttpClient(MockEngine { respond("OK", HttpStatusCode.OK) }) {
      install(InternetConnectionPlugin) {
        // No checker provided
      }
    }

    val response = client.get("/test")
    response.status shouldBe HttpStatusCode.OK

    client.close()
  }
})
