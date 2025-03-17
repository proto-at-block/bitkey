package build.wallet.platform.clipboard

import app.cash.turbine.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ClipboardImplTests : FunSpec({
  val clipboard =
    ClipboardMock(
      plainTextItemToReturn = ClipItem.PlainText("hello")
    )

  test("clipboard refreshes") {
    clipboard.plainTextItem().test {
      awaitItem()

      clipboard.setItem(ClipItem.PlainText("world"))

      awaitItem().shouldBe(ClipItem.PlainText("world"))
    }
  }
})
