package build.wallet.database.adapters.bitkey

import bitkey.serialization.json.decodeFromStringResult
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingKeysetMock
import build.wallet.bitkey.f8e.F8eSpendingKeysetPrivateWalletMock
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class F8eSpendingKeysetColumnAdapterTests : FunSpec({

  val json = Json { explicitNulls = false }

  test("encode produces JSON and decodes private wallet keyset") {
    val encoded = F8eSpendingKeysetColumnAdapter.encode(F8eSpendingKeysetPrivateWalletMock)

    json.decodeFromStringResult<F8eSpendingKeyset>(encoded)
      .getOrThrow()
      .shouldBe(F8eSpendingKeysetPrivateWalletMock)

    F8eSpendingKeysetColumnAdapter.decode(encoded).shouldBe(F8eSpendingKeysetPrivateWalletMock)
  }

  test("encode produces JSON and decodes legacy wallet keyset") {
    val encoded = F8eSpendingKeysetColumnAdapter.encode(F8eSpendingKeysetMock)

    json.decodeFromStringResult<F8eSpendingKeyset>(encoded)
      .getOrThrow()
      .shouldBe(F8eSpendingKeysetMock)

    F8eSpendingKeysetColumnAdapter.decode(encoded).shouldBe(F8eSpendingKeysetMock)
  }
})
