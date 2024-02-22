package build.wallet.integration.statemachine.create

import app.cash.turbine.ReceiveTurbine
import build.wallet.cloud.store.CloudStoreAccountFake.Companion.CloudStoreAccount1Fake
import build.wallet.integration.statemachine.recovery.socrec.advanceThroughCreateLiteAccountScreens
import build.wallet.integration.statemachine.recovery.socrec.advanceThroughTrustedContactEnrollmentScreens
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.testing.launchNewApp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class CreateAndOnboardLiteAccountFunctionalTests : FunSpec({

  test("happy path through create lite account and enroll as trusted contact") {
    // Set up a protected customer with a full account and create a trusted contact invite
    val fullAccountApp = launchNewApp()
    val fullAccount = fullAccountApp.onboardFullAccountWithFakeHardware()
    val tcInvitation =
      fullAccountApp.createTcInvite(
        fullAccount,
        tcName = "Test Trusted Contact Name"
      )

    // Going through onboarding with the lite account, becoming a trusted contact
    // and then remove the trusted contact relationship
    val liteAccountApp = launchNewApp()
    liteAccountApp.app.cloudBackupRepository.clear(CloudStoreAccount1Fake)
    liteAccountApp.app.appUiStateMachine.test(Unit) {
      advanceThroughCreateLiteAccountScreens(
        inviteCode = tcInvitation.token
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
  awaitUntilScreenWithBody<MoneyHomeBodyModel>(
    expectedBodyContentMatch = { body ->
      // Wait until the "Wallets you're Protecting" card shows a protected customer
      body.walletsYoureProtectingCount == 1
    }
  ) {
    // Showing Money Home, tap on first row (first protected customer)
    // of "Wallets you're Protecting" card (which is the first card)
    cardsModel.cards.count()
      .shouldBe(2)
    cardsModel.cards.first()
      .content.shouldNotBeNull()
      .shouldBeTypeOf<CardModel.CardContent.DrillList>()
      .items.first().onClick.shouldNotBeNull().invoke()
  }
  // Showing Money Home with a bottom sheet (the PC info sheet)
  // Tap the secondary button to remove the relationship
  awaitItem()
    .bottomSheetModel.shouldNotBeNull()
    .body.shouldBeTypeOf<FormBodyModel>()
    .secondaryButton.shouldNotBeNull()
    .onClick()

  awaitItem()
    .alertModel.shouldNotBeNull()
    .let {
      it.title.shouldBe("Are you sure you want to remove yourself as a Trusted Contact?")
      it.subline.shouldBe(
        "If Test Protected Customer Name needs help recovering in the future, you won’t be able to assist them."
      )
      it.primaryButtonText.shouldBe("Remove")
      it.secondaryButtonText.shouldBe("Cancel")
      it.onPrimaryButtonClick.invoke()
    }

  // Back to Money Home, the card should be removed
  awaitUntilScreenWithBody<MoneyHomeBodyModel>(
    expectedBodyContentMatch = { body ->
      // Wait until there is 1 card showing
      body.walletsYoureProtectingCount == 0
    }
  )
}

private val MoneyHomeBodyModel.walletsYoureProtectingCount: Int
  get() {
    val drillList = cardsModel.cards.first().content as CardModel.CardContent.DrillList
    return drillList.items.size - 1
  }
