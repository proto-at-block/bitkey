package build.wallet.f8e

import build.wallet.f8e.F8eEnvironment.Custom
import build.wallet.f8e.F8eEnvironment.Production
import build.wallet.f8e.F8eEnvironment.Staging
import build.wallet.serialization.json.decodeFromStringResult
import build.wallet.serialization.json.encodeToStringResult
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
