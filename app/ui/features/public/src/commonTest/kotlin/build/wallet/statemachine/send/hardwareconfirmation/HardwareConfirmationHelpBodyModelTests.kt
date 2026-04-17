package build.wallet.statemachine.send.hardwareconfirmation

import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationHelpContent.Companion.TransactionReview
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class HardwareConfirmationHelpBodyModelTests : FunSpec({

  test("eventTrackerShouldTrack is false for TransactionReview content") {
    val model = HardwareConfirmationHelpBodyModel(
      onBack = {},
      content = TransactionReview
    )
    model.eventTrackerShouldTrack.shouldBe(false)
  }

  test("eventTrackerShouldTrack is true for non-TransactionReview content") {
    val otherContent = HardwareConfirmationHelpContent(
      headline = "Other help",
      statements = listOf(
        HardwareConfirmationHelpContent.Statement(title = "Step 1", body = "Do this"),
        HardwareConfirmationHelpContent.Statement(title = "Step 2", body = "Do that"),
        HardwareConfirmationHelpContent.Statement(title = "Step 3", body = "Done")
      )
    )
    val model = HardwareConfirmationHelpBodyModel(
      onBack = {},
      content = otherContent
    )
    model.eventTrackerShouldTrack.shouldBe(true)
  }
})
