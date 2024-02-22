@file:OptIn(ExperimentalStdlibApi::class)

package build.wallet.nfc

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers

class NfcDispatchersTests : FunSpec({
  test("NfcIO extension property uses the same dispatcher instance") {
    Dispatchers.NfcIO[CoroutineDispatcher.Key]
      .shouldBeSameInstanceAs(Dispatchers.NfcIO[CoroutineDispatcher.Key])
  }

  test("NfcIO sets CoroutineName for debugging") {
    Dispatchers.NfcIO[CoroutineName.Key].shouldBe(CoroutineName("NfcIO"))
  }
})
