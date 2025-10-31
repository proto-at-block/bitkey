package build.wallet.integration.statemachine.create

import app.cash.turbine.ReceiveTurbine
import build.wallet.integration.statemachine.recovery.socrec.advanceThroughCreateLiteAccountScreens
import build.wallet.integration.statemachine.recovery.socrec.advanceThroughTrustedContactEnrollmentScreens
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.lite.LiteMoneyHomeBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.testing.ext.createTcInvite
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.testWithTwoApps
import build.wallet.ui.model.alert.ButtonAlertModel
import build.wallet.ui.model.list.ListItemAccessory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf

class CreateAndOnboardLiteAccountFunctionalTests : FunSpec({

  testWithTwoApps("happy path through create lite account and enroll as trusted contact") { fullAccountApp, liteAccountApp ->
    // Set up a protected customer with a full account and create a trusted contact invite
    fullAccountApp.onboardFullAccountWithFakeHardware()
    val (inviteCode, _) =
      fullAccountApp.createTcInvite(
        tcName = "Test Recovery Contact Name"
      )

    // Going through onboarding with the lite account, becoming a trusted contact
    // and then remove the trusted contact relationship
    liteAccountApp.appUiStateMachine.test(Unit) {
      advanceThroughCreateLiteAccountScreens(
        inviteCode = inviteCode
      )
      advanceThroughTrustedContactEnrollmentScreens(
        protectedCustomerName = "Test Protected Customer Name"
      )
      tapOnProtectedCustomerAndRemoveRelationship()
      cancelAndIgnoreRemainingEvents()
    }
  }
})

private suspend fun ReceiveTurbine<ScreenModel>.tapOnProtectedCustomerAndRemoveRelationship() {
  // Showing Money Home with a bottom sheet (the PC info sheet)
  // Tap the secondary button to remove the relationship
  awaitItem()
    .bottomSheetModel.shouldNotBeNull()
    .body.shouldBeInstanceOf<FormBodyModel>()
    .secondaryButton.shouldNotBeNull()
    .onClick()

  awaitItem()
    .alertModel.shouldBeTypeOf<ButtonAlertModel>()
    .let {
      it.title.shouldBe("Are you sure you want to remove yourself as a Recovery Contact?")
      it.subline.shouldBe(
        "If Test Protected Customer Name needs help recovering in the future, you won’t be able to assist them."
      )
      it.primaryButtonText.shouldBe("Remove")
      it.secondaryButtonText.shouldBe("Cancel")
      it.onPrimaryButtonClick.invoke()
    }

  // Back to Money Home, the card should be removed
  awaitUntilBody<LiteMoneyHomeBodyModel>(
    matching = { body ->
      // Wait until there is 1 card showing
      body.walletsYoureProtectingCount == 0
    }
  )
}

internal val LiteMoneyHomeBodyModel.walletsYoureProtectingCount: Int
  get() {
    val drillList = cardsModel.cards.first().content as CardModel.CardContent.DrillList
    return when (drillList.items.first().leadingAccessory) {
      // This is the Accept Invite button, so there are no protected customers.
      is ListItemAccessory.ButtonAccessory -> 0
      // This is the list of protected customers.
      else -> drillList.items.size
    }
  }
