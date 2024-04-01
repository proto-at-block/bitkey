package build.wallet.bdk.bindings

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers

class BdkDispatchersTests : FunSpec({
  test("BdkIO dispatcher getter returns the same instance") {
    Dispatchers.BdkIO[CoroutineDispatcher.Key]
      .shouldBeSameInstanceAs(Dispatchers.BdkIO[CoroutineDispatcher.Key])
  }

  test("BdkIO dispatcher has CoroutineName") {
    Dispatchers.BdkIO[CoroutineName.Key].shouldBe(CoroutineName("BdkIO"))
  }
})
