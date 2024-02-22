package build.wallet.integration.statemachine.recovery.socrec

import app.cash.turbine.ReceiveTurbine
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId.BEING_TRUSTED_CONTACT_INTRODUCTION
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.cloud.backup.SocRecV1BackupFeatures
import build.wallet.cloud.backup.socRecDataAvailable
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.integration.statemachine.create.beTrustedContactButton
import build.wallet.integration.statemachine.create.moreOptionsButton
import build.wallet.integration.statemachine.create.restoreButton
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachineTester
import build.wallet.statemachine.core.SuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.lite.card.WALLETS_YOURE_PROTECTING_MESSAGE
import build.wallet.statemachine.ui.awaitUntilScreenModelWithBody
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.clickMainContentListFooterButton
import build.wallet.statemachine.ui.clickMainContentListItemTrailingButtonAtIndex
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.findMainContentListItem
import build.wallet.statemachine.ui.getMainContentListItemAtIndex
import build.wallet.statemachine.ui.inputTextToMainContentTextInputItem
import build.wallet.testing.AppTester
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import com.github.michaelbull.result.getOrThrow
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Advances through Trusted Contact invite screens starting at the Trusted Contact Management screen.
 */
suspend fun ReceiveTurbine<ScreenModel>.advanceThroughTrustedContactInviteScreens(tcName: String) {
  awaitUntilScreenWithBody<FormBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_MANAGEMENT_SETTINGS_LIST
  )
    .clickMainContentListFooterButton()
  awaitUntilScreenWithBody<FormBodyModel>(SocialRecoveryEventTrackerScreenId.TC_ADD_TC_NAME) {
    inputTextToMainContentTextInputItem(tcName)
    clickPrimaryButton()
  }
  awaitUntilScreenWithBody<FormBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_ADD_TC_HARDWARE_CHECK
  )
    .clickPrimaryButton()
  awaitUntilScreenWithBody<FormBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_SHARE_SCREEN
  )
    .clickPrimaryButton()
}

/**
 * Advances through Lite Account onboarding, starting at Getting Started screen.
 * @param inviteCode the Trusted Contact invite code
 * @param cloudStoreAccount the cloud store account to sign in with
 */
suspend fun ReceiveTurbine<ScreenModel>.advanceThroughCreateLiteAccountScreens(
  inviteCode: String,
  cloudStoreAccount: CloudStoreAccount = CloudStoreAccountFake.CloudStoreAccount1Fake,
) {
  awaitUntilScreenWithBody<ChooseAccountAccessModel>()
    .moreOptionsButton.onClick()
  awaitUntilScreenWithBody<FormBodyModel>()
    .beTrustedContactButton.onClick.shouldNotBeNull().invoke()
  awaitUntilScreenWithBody<FormBodyModel>(BEING_TRUSTED_CONTACT_INTRODUCTION)
    .primaryButton.shouldNotBeNull().onClick()
  awaitUntilScreenWithBody<CloudSignInModelFake>(CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING)
    .signInSuccess(cloudStoreAccount)
  awaitUntilScreenWithBody<FormBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_ENTER_INVITE_CODE
  )
    .inputTextToMainContentTextInputItem(inviteCode)
  awaitUntilScreenWithBody<FormBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_ENTER_INVITE_CODE
  ) {
    primaryButton.shouldNotBeNull().onClick()
  }
  awaitUntilScreenWithBody<LoadingBodyModel>(
    CreateAccountEventTrackerScreenId.NEW_LITE_ACCOUNT_CREATION
  )
}

/**
 * Advances through trusted contact enrollment screen, starting at the invite code entry.
 */
suspend fun ReceiveTurbine<ScreenModel>.advanceThroughTrustedContactEnrollmentScreens(
  protectedCustomerName: String,
) {
  // Loading with f8e
  awaitUntilScreenWithBody<LoadingBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E
  )

  // Enter name
  awaitUntilScreenWithBody<FormBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME
  )
    .inputTextToMainContentTextInputItem(protectedCustomerName)
  awaitUntilScreenWithBody<FormBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME
  )
    .clickPrimaryButton()

  // Loading with f8e
  awaitUntilScreenWithBody<LoadingBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E
  )

  // Success
  awaitUntilScreenWithBody<SuccessBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_SUCCESS
  )
    .clickPrimaryButton()
}

/**
 * Advances to the cloud recovery screen, starting from the Money Home screen.
 */
suspend fun ReceiveTurbine<ScreenModel>.advanceToSocialChallengeTrustedContactList(
  cloudStoreAccount: CloudStoreAccount = CloudStoreAccountFake.ProtectedCustomerFake,
): FormBodyModel {
  awaitUntilScreenWithBody<ChooseAccountAccessModel>()
    .moreOptionsButton.onClick()
  awaitUntilScreenWithBody<FormBodyModel>()
    .restoreButton.onClick.shouldNotBeNull().invoke()
  awaitUntilScreenWithBody<CloudSignInModelFake>(CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING)
    .signInSuccess(cloudStoreAccount)
  awaitUntilScreenWithBody<FormBodyModel>(
    CloudEventTrackerScreenId.CLOUD_BACKUP_FOUND
  )
    .toolbar.shouldNotBeNull()
    .trailingAccessory.shouldNotBeNull()
    .shouldBeTypeOf<ToolbarAccessoryModel.ButtonAccessory>()
    .model
    .onClick()
  awaitUntilScreenWithBody<FormBodyModel>(CloudEventTrackerScreenId.SOCIAL_RECOVERY_EXPLANATION)
    .clickPrimaryButton()
  awaitUntilScreenWithBody<LoadingBodyModel>(
    SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_STARTING
  )
  awaitUntilScreenWithBody<FormBodyModel>(
    NotificationsEventTrackerScreenId.ENABLE_PUSH_NOTIFICATIONS
  )
    .clickPrimaryButton()
  return awaitUntilScreenWithBody<FormBodyModel>(
    SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_TRUSTED_CONTACTS_LIST
  )
}

/**
 * Starts a social challenge from the Trusted Contacts list in Social Recovery
 * @param formBodyModel the trusted contact list form
 * @return the challenge code
 */
suspend fun ReceiveTurbine<ScreenModel>.startSocialChallenge(formBodyModel: FormBodyModel): String {
  formBodyModel
    .shouldBeTypeOf<FormBodyModel>()
    .also {
      it.id.shouldBe(SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_TRUSTED_CONTACTS_LIST)
    }
    .clickMainContentListItemTrailingButtonAtIndex(0)
  val codeScreen =
    awaitUntilScreenWithBody<FormBodyModel>(
      SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_TC_VERIFICATION_CODE
    )
  return codeScreen
    .getMainContentListItemAtIndex(0)
    .title
    .replace("-", "")
}

/**
 * Advances the protected customer through restoring their app using the social challenge response,
 * starting at the TC list screen.
 * @param formBodyModel the trusted contact list form
 */
suspend fun ReceiveTurbine<ScreenModel>.advanceFromSocialRestoreToLostHardwareRecovery(
  formBodyModel: FormBodyModel,
) {
  formBodyModel
    .primaryButton
    .shouldNotBeNull()
    .also { it.isEnabled.shouldBeTrue() }
    .onClick()
  awaitUntilScreenWithBody<LoadingBodyModel>(
    SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_RESTORE_APP_KEY
  )
  awaitUntilScreenWithBody<FormBodyModel>(
    HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY
  ) {
  }
}

suspend fun ReceiveTurbine<ScreenModel>.advanceThroughSocialChallengeVerifyScreens(code: String) {
  awaitUntilScreenWithBody<MoneyHomeBodyModel>()
    .cardsModel
    .cards
    .find { it.title.string == WALLETS_YOURE_PROTECTING_MESSAGE }
    .shouldNotBeNull()
    .content.shouldBeTypeOf<CardModel.CardContent.DrillList>()
    .items.first()
    .onClick.shouldNotBeNull().invoke()
  awaitUntilScreenModelWithBody<MoneyHomeBodyModel>(
    expectedScreenModelMatch = { screenModel ->
      screenModel.bottomSheetModel != null
    }
  ).bottomSheetModel.shouldNotBeNull()
    .body.shouldBeTypeOf<FormBodyModel>()
    .clickPrimaryButton()
  awaitUntilScreenWithBody<FormBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_RECOVERY_GET_IN_TOUCH
  )
    .findMainContentListItem { it.title == "Phone Call" }
    .onClick.shouldNotBeNull().invoke()
  awaitUntilScreenWithBody<FormBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_RECOVERY_CONTACT_CONFIRMATION
  )
    .findMainContentListItem { it.title.contains("Yes") }
    .onClick.shouldNotBeNull().invoke()
  awaitUntilScreenWithBody<FormBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_RECOVERY_CODE_VERIFICATION
  )
    .inputTextToMainContentTextInputItem(code)
  awaitUntilScreenWithBody<FormBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_RECOVERY_CODE_VERIFICATION
  )
    .clickPrimaryButton()
  awaitUntilScreenWithBody<SuccessBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_RECOVERY_CODE_VERIFICATION_SUCCESS
  )
}

/**
 * Waits for a Full Account cloud backup to contain the given relationship ID. Must be called
 * while an app is running.
 */
suspend fun StateMachineTester<*, ScreenModel>.awaitCloudBackupRefreshed(
  appTester: AppTester,
  relationshipId: String,
) {
  var backupUpdated = false
  while (!backupUpdated) {
    val backup =
      appTester.app.cloudBackupRepository.readBackup(CloudStoreAccountFake.ProtectedCustomerFake)
        .getOrThrow()
        .shouldNotBeNull()
    backupUpdated = backup.socRecDataAvailable &&
      (backup as SocRecV1BackupFeatures)
        .fullAccountFields.shouldNotBeNull()
        .socRecEncryptionKeyCiphertextMap.containsKey(relationshipId)
    delay(100.milliseconds)
  }
}
