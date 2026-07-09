package build.wallet.statemachine.trustedcontact.remove

import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.statemachine.core.Icon
import build.wallet.ui.model.button.ButtonModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RemoveTrustedContactBodyModelTests : FunSpec({
  test("failed setup copy distinguishes setup removal from active contact removal") {
    val model = RemoveTrustedContactBodyModel(
      trustedContactAlias = TrustedContactAlias("Sam"),
      onRemove = {},
      onClosed = {},
      isBeneficiary = false,
      removalContext = RemovalContext.FailedSetup
    )

    model.header?.headline.shouldBe("Remove failed Recovery Contact setup for Sam?")
    model.header?.sublineModel?.string.shouldBe(
      "This removes the failed setup attempt. To add them later, invite them again. Security-sensitive changes require your Bitkey."
    )
    model.primaryButton?.text.shouldBe("Remove setup")
    model.primaryButton?.treatment.shouldBe(ButtonModel.Treatment.BitkeyInteraction)
    model.primaryButton?.leadingIcon.shouldBe(Icon.Bitkey)
  }

  test("expired invitation removal still does not require bitkey") {
    val model = RemoveTrustedContactBodyModel(
      trustedContactAlias = TrustedContactAlias("Sam"),
      onRemove = {},
      onClosed = {},
      isBeneficiary = false,
      removalContext = RemovalContext.ExpiredInvitation
    )

    model.header?.headline.shouldBe("Your invitation to Sam to be a Recovery Contact has expired.")
    model.header?.sublineModel.shouldBe(null)
    model.primaryButton?.text.shouldBe("Remove Recovery Contact")
    model.primaryButton?.treatment.shouldBe(ButtonModel.Treatment.Primary)
    model.primaryButton?.leadingIcon.shouldBe(null)
  }
})
