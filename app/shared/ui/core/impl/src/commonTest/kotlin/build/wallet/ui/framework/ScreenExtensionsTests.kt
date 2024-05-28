package build.wallet.ui.framework

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ScreenExtensionsTests : FunSpec({
  test("Screen.key is based on the key's class qualified name") {
    val screenA = ScreenFake(id = "A")
    val screenB = ScreenFake(id = "B")

    screenA.key.shouldBe("build.wallet.ui.framework.ScreenFake")
    screenB.key.shouldBe("build.wallet.ui.framework.ScreenFake")
  }
})
