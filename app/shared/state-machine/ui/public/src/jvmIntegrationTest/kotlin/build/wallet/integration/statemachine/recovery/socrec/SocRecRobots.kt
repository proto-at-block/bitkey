@file:Suppress("TooManyFunctions")

package build.wallet.integration.statemachine.recovery.socrec

import app.cash.turbine.ReceiveTurbine
import build.wallet.analytics.events.screen.id.*
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.LOADING_RESTORING_FROM_CLOUD_BACKUP
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId.BEING_TRUSTED_CONTACT_INTRODUCTION
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_TRUSTED_CONTACTS_LIST
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.integration.statemachine.create.beTrustedContactButton
import build.wallet.integration.statemachine.create.restoreButton
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.moneyhome.lite.LiteMoneyHomeBodyModel
import build.wallet.statemachine.settings.SettingsBodyModel
import build.wallet.statemachine.ui.*
import build.wallet.statemachine.ui.matchers.hasProtectedCustomers
import build.wallet.statemachine.ui.matchers.shouldHaveId
import build.wallet.statemachine.ui.matchers.shouldHaveMessage
import build.wallet.statemachine.ui.robots.awaitLoadingScreen
import build.wallet.statemachine.ui.robots.clickMoreOptionsButton
import build.wallet.statemachine.ui.robots.moreOptionsButton
import build.wallet.statemachine.ui.robots.selectProtectedCustomer
import build.wallet.testing.AppTester
import build.wallet.testing.ext.completeRecoveryDelayPeriodOnF8e
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf

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
  }
  awaitUntilScreenWithBody<FormBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_ADD_TC_NAME,
    expectedBodyContentMatch = { it.primaryButton?.isEnabled == true }
  ) {
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
    .clickMoreOptionsButton()
  awaitUntilScreenWithBody<FormBodyModel>()
    .beTrustedContactButton.onClick.shouldNotBeNull().invoke()
  awaitUntilScreenWithBody<FormBodyModel>(BEING_TRUSTED_CONTACT_INTRODUCTION)
    .clickPrimaryButton()
  awaitUntilScreenWithBody<CloudSignInModelFake>(CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING)
    .signInSuccess(cloudStoreAccount)
  awaitUntilScreenWithBody<FormBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_ENTER_INVITE_CODE
  )
    .inputTextToMainContentTextInputItem(inviteCode)
  awaitUntilScreenWithBody<FormBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_ENTER_INVITE_CODE,
    expectedBodyContentMatch = { it.primaryButton?.isEnabled == true }
  ) {
    clickPrimaryButton()
  }
  awaitUntilScreenWithBody<LoadingSuccessBodyModel>(
    CreateAccountEventTrackerScreenId.NEW_LITE_ACCOUNT_CREATION
  ) {
    state.shouldBe(LoadingSuccessBodyModel.State.Loading)
  }
}

suspend fun ReceiveTurbine<ScreenModel>.advanceThroughFullAccountAcceptTCInviteScreens(
  inviteCode: String,
  protectedCustomerAlias: String,
) {
  awaitUntilScreenWithBody<MoneyHomeBodyModel>(MoneyHomeEventTrackerScreenId.MONEY_HOME) {
    trailingToolbarAccessoryModel
      .shouldNotBeNull()
      .shouldBeTypeOf<ToolbarAccessoryModel.IconAccessory>()
      .model
      .onClick()
  }

  awaitUntilScreenWithBody<SettingsBodyModel>(SettingsEventTrackerScreenId.SETTINGS) {
    sectionModels
      .flatMap { it.rowModels }
      .firstOrNull { it.title == "Trusted Contacts" }
      .shouldNotBeNull()
      .onClick()
  }

  awaitUntilScreenWithBody<FormBodyModel> {
    header?.headline.shouldBe("Trusted Contacts")
    mainContentList.shouldHaveSize(2) // 1 list for TCs, 1 for protected customers
      .toList()[1]
      .shouldBeInstanceOf<FormMainContentModel.ListGroup>()
      .listGroupModel
      .footerButton
      .shouldNotBeNull()
      .onClick()
  }

  awaitUntilScreenWithBody<FormBodyModel>(SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_ENTER_INVITE_CODE) {
    inputTextToMainContentTextInputItem(inviteCode)
  }

  awaitUntilScreenWithBody<FormBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_ENTER_INVITE_CODE,
    expectedBodyContentMatch = { it.primaryButton?.isEnabled == true }
  ) {
    clickPrimaryButton()
  }

  awaitUntilScreenWithBody<FormBodyModel>(SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME) {
    inputTextToMainContentTextInputItem(protectedCustomerAlias)
  }

  awaitUntilScreenWithBody<FormBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME,
    expectedBodyContentMatch = { it.primaryButton?.isEnabled == true }
  ) {
    clickPrimaryButton()
  }
}

/**
 * Advances through trusted contact enrollment screen, starting at the invite code entry.
 */
suspend fun ReceiveTurbine<ScreenModel>.advanceThroughTrustedContactEnrollmentScreens(
  protectedCustomerName: String,
) {
  // Loading with f8e
  awaitUntilScreenWithBody<LoadingSuccessBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E
  ) {
    state.shouldBe(LoadingSuccessBodyModel.State.Loading)
  }

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
  awaitUntilScreenWithBody<LoadingSuccessBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E
  ) {
    state.shouldBe(LoadingSuccessBodyModel.State.Loading)
  }

  // Success
  awaitUntilScreenWithBody<FormBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_SUCCESS
  )
    .clickPrimaryButton()
}

/**
 * Advances to the cloud recovery screen, starting from the Money Home screen.
 * @return the cloud recovery [FormBodyModel]
 */
suspend fun ReceiveTurbine<ScreenModel>.advanceToCloudRecovery(
  cloudStoreAccount: CloudStoreAccount = CloudStoreAccountFake.ProtectedCustomerFake,
): FormBodyModel {
  awaitUntilScreenWithBody<ChooseAccountAccessModel>()
    .clickMoreOptionsButton()
  awaitUntilScreenWithBody<FormBodyModel>()
    .restoreButton.onClick.shouldNotBeNull().invoke()
  awaitUntilScreenWithBody<CloudSignInModelFake>(CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING)
    .signInSuccess(cloudStoreAccount)
  return awaitUntilScreenWithBody<FormBodyModel>(
    CloudEventTrackerScreenId.CLOUD_BACKUP_FOUND
  )
}

/**
 * Advances to the social recovery challenge screen, starting from the Money Home screen
 */
suspend fun ReceiveTurbine<ScreenModel>.advanceToSocialChallengeTrustedContactList(
  cloudStoreAccount: CloudStoreAccount = CloudStoreAccountFake.ProtectedCustomerFake,
): FormBodyModel {
  advanceToCloudRecovery(cloudStoreAccount)
    .toolbar.shouldNotBeNull()
    .trailingAccessory.shouldNotBeNull()
    .shouldBeTypeOf<ToolbarAccessoryModel.ButtonAccessory>()
    .model
    .onClick()
  awaitUntilScreenWithBody<FormBodyModel>(CloudEventTrackerScreenId.SOCIAL_RECOVERY_EXPLANATION)
    .clickPrimaryButton()
  awaitUntilScreenWithBody<LoadingSuccessBodyModel>(
    SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_STARTING
  ) {
    state.shouldBe(LoadingSuccessBodyModel.State.Loading)
  }
  awaitUntilScreenWithBody<FormBodyModel>(
    NotificationsEventTrackerScreenId.ENABLE_PUSH_NOTIFICATIONS
  )
    .clickPrimaryButton()
  return awaitUntilScreenWithBody<FormBodyModel>(
    id = RECOVERY_CHALLENGE_TRUSTED_CONTACTS_LIST
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
      it.id.shouldBe(RECOVERY_CHALLENGE_TRUSTED_CONTACTS_LIST)
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
    .shouldHaveId(RECOVERY_CHALLENGE_TRUSTED_CONTACTS_LIST)
    .primaryButton
    .shouldNotBeNull()
    .also { it.isEnabled.shouldBeTrue() }
    .onClick()
  awaitUntilScreenWithBody<LoadingSuccessBodyModel>(
    SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_RESTORE_APP_KEY
  )
  awaitLoadingScreen(LOADING_RESTORING_FROM_CLOUD_BACKUP)
    .shouldHaveMessage("Restoring from backup...")
  awaitUntilScreenWithBody<FormBodyModel>(
    HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY
  ) {
  }
}

suspend fun ReceiveTurbine<ScreenModel>.advanceThroughSocialChallengeVerifyScreensAsLiteAccount(
  protectedCustomerName: String,
  code: String,
) {
  awaitUntilScreenWithBody<LiteMoneyHomeBodyModel>(
    expectedBodyContentMatch = { it.hasProtectedCustomers() }
  ) {
    selectProtectedCustomer(protectedCustomerName)
  }
  awaitUntilScreenModelWithBody<LiteMoneyHomeBodyModel>(
    expectedScreenModelMatch = { screenModel ->
      screenModel.bottomSheetModel != null
    }
  ).bottomSheetModel.shouldNotBeNull()
    .body.shouldBeTypeOf<FormBodyModel>()
    .clickPrimaryButton()

  awaitUntilScreenWithBody<FormBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_RECOVERY_GET_IN_TOUCH
  )
    .findMainContentListItem { it.title == "Video Chat" }
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
    SocialRecoveryEventTrackerScreenId.TC_RECOVERY_CODE_VERIFICATION,
    expectedBodyContentMatch = {
      it.primaryButton?.isEnabled == true
    }
  )
    .clickPrimaryButton()
  awaitUntilScreenWithBody<LoadingSuccessBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_RECOVERY_CODE_VERIFICATION_SUCCESS
  )
}

suspend fun ReceiveTurbine<ScreenModel>.advanceThroughSocialChallengeVerifyScreensAsFullAccount(
  protectedCustomerName: String,
  code: String,
) {
  awaitUntilScreenWithBody<MoneyHomeBodyModel>(MoneyHomeEventTrackerScreenId.MONEY_HOME) {
    trailingToolbarAccessoryModel
      .shouldNotBeNull()
      .shouldBeTypeOf<ToolbarAccessoryModel.IconAccessory>()
      .model
      .onClick()
  }

  awaitUntilScreenWithBody<SettingsBodyModel>(SettingsEventTrackerScreenId.SETTINGS) {
    sectionModels
      .flatMap { it.rowModels }
      .firstOrNull { it.title == "Trusted Contacts" }
      .shouldNotBeNull()
      .onClick()
  }

  awaitUntilScreenWithBody<FormBodyModel> {
    header?.headline.shouldBe("Trusted Contacts")
    mainContentList.shouldHaveSize(2) // 1 list for TCs, 1 for protected customers
      .toList()[1]
      .shouldBeInstanceOf<FormMainContentModel.ListGroup>()
      .listGroupModel
      .items
      .single { it.title == protectedCustomerName }
      .onClick
      .shouldNotBeNull()
      .invoke()
  }

  awaitUntilScreenModelWithBody<FormBodyModel>(
    expectedScreenModelMatch = { screenModel ->
      screenModel.bottomSheetModel != null
    }
  ).bottomSheetModel.shouldNotBeNull()
    .body.shouldBeTypeOf<FormBodyModel>()
    .clickPrimaryButton()

  awaitUntilScreenWithBody<FormBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_RECOVERY_GET_IN_TOUCH
  )
    .findMainContentListItem { it.title == "Video Chat" }
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
  awaitUntilScreenWithBody<LoadingSuccessBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_RECOVERY_CODE_VERIFICATION_SUCCESS
  )
}

/**
 * Advances through full Account onboarding, starting at Getting Started screen (lost app + lost cloud)
 * @param cloudStoreAccount the cloud store account to sign in with
 */
suspend fun ReceiveTurbine<ScreenModel>.advanceThroughLostAppAndCloudRecoveryToMoneyHome(
  cloudStoreAccount: CloudStoreAccount,
) {
  awaitUntilScreenWithBody<ChooseAccountAccessModel>()
    .moreOptionsButton.onClick()
  awaitUntilScreenWithBody<FormBodyModel>()
    .restoreButton.onClick.shouldNotBeNull().invoke()
  awaitUntilScreenWithBody<CloudSignInModelFake>(CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING)
    .signInSuccess(cloudStoreAccount)
  awaitUntilScreenWithBody<FormBodyModel>(CloudEventTrackerScreenId.CLOUD_BACKUP_NOT_FOUND)
    .restoreButton.onClick.shouldNotBeNull().invoke()
  awaitUntilScreenWithBody<FormBodyModel>(DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_INITIATION_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitUntilScreenWithBody<FormBodyModel>(NotificationsEventTrackerScreenId.ENABLE_PUSH_NOTIFICATIONS)
    .clickPrimaryButton()
  awaitUntilScreenWithBody<FormBodyModel>(DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_READY)
    .clickPrimaryButton()
  awaitUntilScreenWithBody<LoadingSuccessBodyModel>(DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_ROTATING_AUTH_KEYS) {
    state.shouldBe(LoadingSuccessBodyModel.State.Loading)
  }
  awaitUntilScreenWithBody<FormBodyModel>(CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitUntilScreenWithBody<CloudSignInModelFake>(CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING)
    .signInSuccess(cloudStoreAccount)
  awaitUntilScreenWithBody<LoadingSuccessBodyModel>(DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS) {
    state.shouldBe(LoadingSuccessBodyModel.State.Loading)
  }
  awaitUntilScreenWithBody<FormBodyModel>(DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_ZERO_BALANCE)
    .clickPrimaryButton()
  awaitUntilScreenWithBody<MoneyHomeBodyModel>(MoneyHomeEventTrackerScreenId.MONEY_HOME)
}

/**
 * Starting at the Money Home screen, advances through lost hardware and cloud recovery back to the
 * Money Home screen.
 */
suspend fun ReceiveTurbine<ScreenModel>.advanceThroughLostHardwareAndCloudRecoveryToMoneyHome(
  appTester: AppTester,
  cloudStoreAccount: CloudStoreAccount = CloudStoreAccountFake.ProtectedCustomerFake,
) {
  awaitUntilScreenWithBody<MoneyHomeBodyModel>()
    .trailingToolbarAccessoryModel
    .shouldBeTypeOf<ToolbarAccessoryModel.IconAccessory>()
    .model.onClick.invoke()

  awaitUntilScreenWithBody<SettingsBodyModel>()
    .sectionModels.flatMap { it.rowModels }
    .find { it.title == "Bitkey Device" }
    .shouldNotBeNull()
    .onClick()
  awaitUntilScreenWithBody<FormBodyModel> {
    mainContentList
      .filterIsInstance<FormMainContentModel.Button>()
      .single { it.item.text == "Replace device" }
      .item.onClick()
  }

  awaitUntilScreenWithBody<FormBodyModel>(HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitUntilScreenWithBody<FormBodyModel>(HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY)
    .clickPrimaryButton()
  awaitUntilScreenWithBody<PairNewHardwareBodyModel>(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitUntilScreenWithBody<PairNewHardwareBodyModel>(PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitUntilScreenWithBody<PairNewHardwareBodyModel>(PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitUntilScreenWithBody<FormBodyModel>(HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_PENDING)

  appTester.completeRecoveryDelayPeriodOnF8e()

  awaitUntilScreenWithBody<FormBodyModel>(HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_READY)
    .clickPrimaryButton()
  awaitUntilScreenWithBody<LoadingSuccessBodyModel>(HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS) {
    state.shouldBe(LoadingSuccessBodyModel.State.Loading)
  }
  awaitUntilScreenWithBody<FormBodyModel>(CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitUntilScreenWithBody<CloudSignInModelFake>(CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING)
    .signInSuccess(cloudStoreAccount)
  awaitUntilScreenWithBody<LoadingSuccessBodyModel>(HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS) {
    state.shouldBe(LoadingSuccessBodyModel.State.Loading)
  }
  awaitUntilScreenWithBody<FormBodyModel>(HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_ZERO_BALANCE)
    .clickPrimaryButton()
  awaitUntilScreenModelWithBody<MoneyHomeBodyModel>()
  cancelAndIgnoreRemainingEvents()
}
