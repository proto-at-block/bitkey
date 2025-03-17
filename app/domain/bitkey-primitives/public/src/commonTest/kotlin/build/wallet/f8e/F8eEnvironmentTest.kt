package build.wallet.f8e

import bitkey.serialization.json.decodeFromStringResult
import bitkey.serialization.json.encodeToStringResult
import build.wallet.f8e.F8eEnvironment.*
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.serialization.json.Json

class F8eEnvironmentTest : FunSpec({
  test("serialization") {
    val prod: F8eEnvironment = Production
    Json.encodeToStringResult(prod).getOrThrow().shouldBeEqual("\"Production\"")
    val custom: F8eEnvironment = Custom("http://localhost")
    Json.encodeToStringResult(custom).getOrThrow().shouldBeEqual("\"http://localhost\"")

    Json.decodeFromStringResult<F8eEnvironment>("\"Staging\"").getOrThrow().shouldBeEqual(Staging)
    Json.decodeFromStringResult<F8eEnvironment>("\"http://localhost\"")
      .getOrThrow()
      .shouldBeEqual(Custom("http://localhost"))
  }
})
