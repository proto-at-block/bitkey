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
import build.wallet.integration.statemachine.create.walletsYoureProtectingCount
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.input.NameInputBodyModel
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.lite.LiteMoneyHomeBodyModel
import build.wallet.statemachine.recovery.cloud.CloudBackupFoundModel
import build.wallet.statemachine.recovery.socrec.challenge.RecoveryChallengeCodeBodyModel
import build.wallet.statemachine.recovery.socrec.challenge.RecoveryChallengeContactListBodyModel
import build.wallet.statemachine.recovery.socrec.help.model.ConfirmingIdentityFormBodyModel
import build.wallet.statemachine.recovery.socrec.help.model.EnterRecoveryCodeFormBodyModel
import build.wallet.statemachine.recovery.socrec.help.model.VerifyingContactMethodFormBodyModel
import build.wallet.statemachine.recovery.socrec.list.full.TrustedContactsListBodyModel
import build.wallet.statemachine.settings.SettingsBodyModel
import build.wallet.statemachine.trustedcontact.model.EnteringInviteCodeBodyModel
import build.wallet.statemachine.trustedcontact.model.EnteringProtectedCustomerNameBodyModel
import build.wallet.statemachine.ui.awaitUntilScreenModelWithBody
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.clickPrimaryButton
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
  awaitUntilScreenWithBody<TrustedContactsListBodyModel>()
    .onAddPressed()
  awaitUntilScreenWithBody<NameInputBodyModel> {
    onValueChange(tcName)
  }
  awaitUntilScreenWithBody<NameInputBodyModel>(
    expectedBodyContentMatch = { it.primaryButton.isEnabled }
  ) {
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
  awaitUntilScreenWithBody<EnteringInviteCodeBodyModel>()
    .onValueChange(inviteCode)
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

  awaitUntilScreenWithBody<EnteringInviteCodeBodyModel> {
    onValueChange(inviteCode)
  }

  awaitUntilScreenWithBody<FormBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_ENTER_INVITE_CODE,
    expectedBodyContentMatch = { it.primaryButton?.isEnabled == true }
  ) {
    clickPrimaryButton()
  }

  awaitUntilScreenWithBody<EnteringProtectedCustomerNameBodyModel> {
    onValueChange(protectedCustomerAlias)
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
  awaitUntilScreenWithBody<EnteringProtectedCustomerNameBodyModel>()
    .onValueChange(protectedCustomerName)
  awaitUntilScreenWithBody<EnteringProtectedCustomerNameBodyModel>(
    expectedBodyContentMatch = { it.primaryButton.isEnabled }
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
  awaitUntilScreenWithBody<LiteMoneyHomeBodyModel>(
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
}

/**
 * Advances to the cloud recovery screen, starting from the Money Home screen.
 * @return the cloud recovery [FormBodyModel]
 */
suspend fun ReceiveTurbine<ScreenModel>.advanceToCloudRecovery(
  cloudStoreAccount: CloudStoreAccount = CloudStoreAccountFake.ProtectedCustomerFake,
): CloudBackupFoundModel {
  awaitUntilScreenWithBody<ChooseAccountAccessModel>()
    .clickMoreOptionsButton()
  awaitUntilScreenWithBody<FormBodyModel>()
    .restoreButton.onClick.shouldNotBeNull().invoke()
  awaitUntilScreenWithBody<CloudSignInModelFake>(CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING)
    .signInSuccess(cloudStoreAccount)
  return awaitUntilScreenWithBody<CloudBackupFoundModel>()
}

/**
 * Advances to the social recovery challenge screen, starting from the Money Home screen
 */
suspend fun ReceiveTurbine<ScreenModel>.advanceToSocialChallengeTrustedContactList(
  cloudStoreAccount: CloudStoreAccount = CloudStoreAccountFake.ProtectedCustomerFake,
): RecoveryChallengeContactListBodyModel {
  advanceToCloudRecovery(cloudStoreAccount)
    .also { it.showSocRecButton.shouldBeTrue() }
    .onLostBitkeyClick()
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
  return awaitUntilScreenWithBody<RecoveryChallengeContactListBodyModel>()
}

/**
 * Starts a social challenge from the Trusted Contacts list in Social Recovery
 * @param formBodyModel the trusted contact list form
 * @return the challenge code
 */
suspend fun ReceiveTurbine<ScreenModel>.startSocialChallenge(
  formBodyModel: RecoveryChallengeContactListBodyModel,
): String {
  formBodyModel.onVerifyClick(formBodyModel.endorsedTrustedContacts[0])
  val codeScreen = awaitUntilScreenWithBody<RecoveryChallengeCodeBodyModel>()
  return codeScreen
    .recoveryChallengeCode
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
    .body.shouldBeInstanceOf<FormBodyModel>()
    .clickPrimaryButton()

  awaitUntilScreenWithBody<VerifyingContactMethodFormBodyModel>()
    .onVideoChatClick()
  awaitUntilScreenWithBody<ConfirmingIdentityFormBodyModel>()
    .onVerifiedClick()
  awaitUntilScreenWithBody<EnterRecoveryCodeFormBodyModel>()
    .onInputChange(code)
  awaitUntilScreenWithBody<EnterRecoveryCodeFormBodyModel>(
    expectedBodyContentMatch = { it.primaryButton.isEnabled }
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
    .body.shouldBeInstanceOf<FormBodyModel>()
    .clickPrimaryButton()

  awaitUntilScreenWithBody<VerifyingContactMethodFormBodyModel>()
    .onVideoChatClick()
  awaitUntilScreenWithBody<ConfirmingIdentityFormBodyModel>()
    .onVerifiedClick()
  awaitUntilScreenWithBody<EnterRecoveryCodeFormBodyModel>()
    .onInputChange(code)
  awaitUntilScreenWithBody<EnterRecoveryCodeFormBodyModel>(
    expectedBodyContentMatch = { it.primaryButton.isEnabled }
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
  app: AppTester,
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

  app.completeRecoveryDelayPeriodOnF8e()

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
