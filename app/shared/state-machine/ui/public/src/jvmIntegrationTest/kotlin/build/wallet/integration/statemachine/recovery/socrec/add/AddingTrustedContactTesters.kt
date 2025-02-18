package build.wallet.integration.statemachine.recovery.socrec.add

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachineTester
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.nfc.NfcBodyModel
import build.wallet.statemachine.recovery.socrec.add.AddingTrustedContactUiProps
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.clickPrimaryButton
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

/**
 * Fill in the initial name fields with names for testing.
 */
suspend fun StateMachineTester<AddingTrustedContactUiProps, ScreenModel>.proceedWithFakeNames(
  tcName: String = "tc-name",
) {
  awaitUntilBody<FormBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_ADD_TC_NAME
  ) {
    mainContentList.first().shouldBeTypeOf<FormMainContentModel.TextInput>()
      .fieldModel.onValueChange.invoke(tcName, tcName.indices)
  }
  awaitUntilBody<FormBodyModel>(
    matching = { it.primaryButton?.isEnabled == true }
  ) {
    clickPrimaryButton()
  }
}

/**
 * Proceed through the NFC screens.
 */
suspend fun StateMachineTester<AddingTrustedContactUiProps, ScreenModel>.proceedNfcScreens() {
  awaitUntilBody<LoadingSuccessBodyModel> {
    state.shouldBe(LoadingSuccessBodyModel.State.Loading)
  }
  awaitUntilBody<NfcBodyModel>()
  awaitUntilBody<NfcBodyModel>()
}
