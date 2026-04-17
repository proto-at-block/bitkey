@file:Suppress("TooManyFunctions")

package build.wallet.integration.statemachine.recovery.socrec

import app.cash.turbine.ReceiveTurbine
import bitkey.ui.screens.securityhub.SecurityHubBodyModel
import build.wallet.analytics.events.screen.id.*
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.LOADING_RESTORING_FROM_CLOUD_BACKUP
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_TRUSTED_CONTACTS_LIST
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.integration.statemachine.create.restoreButton
import build.wallet.integration.statemachine.create.walletsYoureProtectingCount
import build.wallet.statemachine.account.AccountAccessMoreOptionsFormBodyModel
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.account.create.full.hardware.CompleteTwoTapBodyModel
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.input.NameInputBodyModel
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.lite.LiteMoneyHomeBodyModel
import build.wallet.statemachine.nfc.NfcBodyModel
import build.wallet.statemachine.nfc.PromptSelectionFormBodyModel
import build.wallet.statemachine.platform.permissions.EnableNotificationsBodyModel
import build.wallet.statemachine.recovery.cloud.CloudBackupFoundModel
import build.wallet.statemachine.recovery.cloud.SocialRecoveryExplanationModel
import build.wallet.statemachine.recovery.socrec.add.ShareInviteBodyModel
import build.wallet.statemachine.recovery.hardware.initiating.HardwareReplacementInstructionsModel
import build.wallet.statemachine.recovery.hardware.initiating.NewDeviceReadyQuestionBodyModel
import build.wallet.statemachine.recovery.inprogress.DelayAndNotifyNewKeyReady
import build.wallet.statemachine.recovery.socrec.challenge.RecoveryChallengeCodeBodyModel
import build.wallet.statemachine.recovery.socrec.challenge.RecoveryChallengeContactListBodyModel
import build.wallet.statemachine.recovery.socrec.help.model.ConfirmingIdentityFormBodyModel
import build.wallet.statemachine.recovery.socrec.help.model.EnterRecoveryCodeFormBodyModel
import build.wallet.statemachine.recovery.socrec.help.model.VerifyingContactMethodFormBodyModel
import build.wallet.statemachine.recovery.socrec.list.full.TrustedContactsListBodyModel
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationScreenModel
import build.wallet.statemachine.settings.SettingsBodyModel
import build.wallet.statemachine.settings.full.device.DeviceSettingsFormBodyModel
import build.wallet.statemachine.trustedcontact.model.EnteringInviteCodeBodyModel
import build.wallet.statemachine.trustedcontact.model.EnteringProtectedCustomerNameBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.matchers.hasProtectedCustomers
import build.wallet.statemachine.ui.matchers.shouldHaveId
import build.wallet.statemachine.ui.matchers.shouldHaveMessage
import build.wallet.statemachine.ui.robots.*
import build.wallet.testing.ext.HardwareCoverageMode
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf

/**
 * Advances through Trusted Contact invite screens starting at the Trusted Contact Management screen.
 */
suspend fun ReceiveTurbine<ScreenModel>.advanceThroughTrustedContactInviteScreens(
  tcName: String,
  coverageMode: HardwareCoverageMode = HardwareCoverageMode.W1Baseline,
) {
  awaitUntilBody<TrustedContactsListBodyModel>()
    .onAddPressed()
  awaitUntilBody<NameInputBodyModel> {
    onValueChange(tcName)
  }
  awaitUntilBody<NameInputBodyModel>(
    matching = { it.primaryButton.isEnabled }
  ) {
    primaryButton.onClick.invoke()
  }
  awaitUntilBody<FormBodyModel>(
    matching = { it.primaryButton?.isEnabled == true }
  ) {
    clickPrimaryButton()
  }
  awaitUntilBody<NfcBodyModel>()
  when (coverageMode) {
    HardwareCoverageMode.W1Baseline -> {
      awaitUntilBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
    }
    HardwareCoverageMode.W3Private -> {
      awaitUntilScreenWithBody<NfcBodyModel>(
        matchingScreen = { it.bottomSheetModel?.body is PromptSelectionFormBodyModel }
      ).let { (checkNotNull(it.bottomSheetModel).body as PromptSelectionFormBodyModel).onApprove() }
      when (val nextBody = awaitItem().body) {
        is HardwareConfirmationScreenModel -> {
          nextBody.onConfirm()
          awaitUntilBody<LoadingSuccessBodyModel> {
            state.shouldBe(LoadingSuccessBodyModel.State.Loading)
          }
        }
        is LoadingSuccessBodyModel -> {
          nextBody.state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        }
        else -> error(
          "Unexpected body after W3 trusted contact approval: ${nextBody::class.simpleName}"
        )
      }
    }
  }
  awaitUntilBody<ShareInviteBodyModel> {
    clickPrimaryButton()
  }
  awaitUntilBody<SuccessBodyModel>()
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
  awaitUntilBody<ChooseAccountAccessModel>()
    .clickMoreOptionsButton()
  awaitUntilBody<AccountAccessMoreOptionsFormBodyModel>()
    .onBeTrustedContactClick()
  advanceUntilScreenWithBody<CloudSignInModelFake>(CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING)
    .signInSuccess(cloudStoreAccount)
  awaitUntilBody<EnteringInviteCodeBodyModel>()
    .onValueChange(inviteCode)
  advanceUntilScreenWithBody<LoadingSuccessBodyModel>(
    CreateAccountEventTrackerScreenId.NEW_LITE_ACCOUNT_CREATION
  ) {
    state.shouldBe(LoadingSuccessBodyModel.State.Loading)
  }
}

suspend fun ReceiveTurbine<ScreenModel>.advanceThroughFullAccountAcceptTCInviteScreens(
  inviteCode: String,
  protectedCustomerAlias: String,
) {
  awaitUntilBody<MoneyHomeBodyModel>().onSecurityHubTabClick()

  awaitUntilBody<SecurityHubBodyModel>().clickRecoveryContacts()

  awaitUntilBody<TrustedContactsListBodyModel> {
    contacts.shouldBeEmpty()
    invitations.shouldBeEmpty()
    protectedCustomers.shouldBeEmpty()
    onAcceptInvitePressed()
  }

  awaitUntilBody<EnteringInviteCodeBodyModel> {
    onValueChange(inviteCode)
  }

  awaitUntilBody<FormBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_ENTER_INVITE_CODE,
    matching = { it.primaryButton?.isEnabled == true }
  ) {
    clickPrimaryButton()
  }

  awaitUntilBody<EnteringProtectedCustomerNameBodyModel> {
    onValueChange(protectedCustomerAlias)
  }

  awaitUntilBody<FormBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME,
    matching = { it.primaryButton?.isEnabled == true }
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
  awaitUntilBody<LoadingSuccessBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E
  ) {
    state.shouldBe(LoadingSuccessBodyModel.State.Loading)
  }

  // Enter name
  awaitUntilBody<EnteringProtectedCustomerNameBodyModel>()
    .onValueChange(protectedCustomerName)
  awaitUntilBody<EnteringProtectedCustomerNameBodyModel>(
    matching = { it.primaryButton.isEnabled }
  )
    .clickPrimaryButton()

  // Loading with f8e
  awaitUntilBody<LoadingSuccessBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E
  ) {
    state.shouldBe(LoadingSuccessBodyModel.State.Loading)
  }

  // Success
  awaitUntilBody<FormBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_INVITE_ACCEPTED
  )
    .clickPrimaryButton()
  awaitUntilBody<LiteMoneyHomeBodyModel>(
    matching = { body ->
      // Wait until the "Wallets you're Protecting" card shows a protected customer
      body.walletsYoureProtectingCount == 1
    }
  ) {
    // Showing Money Home, tap on first row (first protected customer)
    // of "Wallets you're Protecting" card (which is the first card)
    cardsModel.cards.count()
      .shouldBe(3)
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
  awaitUntilBody<ChooseAccountAccessModel>()
    .clickMoreOptionsButton()
  awaitUntilBody<AccountAccessMoreOptionsFormBodyModel>()
    .onRestoreYourWalletClick()
  awaitUntilBody<CloudSignInModelFake>(CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING)
    .signInSuccess(cloudStoreAccount)
  return awaitUntilBody<CloudBackupFoundModel>()
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
  awaitUntilBody<SocialRecoveryExplanationModel>()
    .onContinue()
  awaitUntilBody<LoadingSuccessBodyModel>(
    SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_STARTING
  ) {
    state.shouldBe(LoadingSuccessBodyModel.State.Loading)
  }
  awaitUntilBody<EnableNotificationsBodyModel>()
    .onComplete()
  return awaitUntilBody<RecoveryChallengeContactListBodyModel>()
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
  val codeScreen = awaitUntilBody<RecoveryChallengeCodeBodyModel>()
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
  awaitUntilBody<LoadingSuccessBodyModel>(
    SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_RESTORE_APP_KEY
  )
  awaitLoadingScreen(LOADING_RESTORING_FROM_CLOUD_BACKUP)
    .shouldHaveMessage("Restoring from backup...")
  awaitUntilBody<FormBodyModel>(
    HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY
  ) {
  }
}

suspend fun ReceiveTurbine<ScreenModel>.advanceThroughSocialChallengeVerifyScreensAsLiteAccount(
  protectedCustomerName: String,
  code: String,
) {
  awaitUntilBody<LiteMoneyHomeBodyModel>(
    matching = { it.hasProtectedCustomers() }
  ) {
    selectProtectedCustomer(protectedCustomerName)
  }
  awaitUntilScreenWithBody<LiteMoneyHomeBodyModel>(
    matchingScreen = { screenModel ->
      screenModel.bottomSheetModel != null
    }
  ).bottomSheetModel.shouldNotBeNull()
    .body.shouldBeInstanceOf<FormBodyModel>()
    .clickPrimaryButton()

  awaitUntilBody<VerifyingContactMethodFormBodyModel>()
    .onVideoChatClick()
  awaitUntilBody<ConfirmingIdentityFormBodyModel>()
    .onVerifiedClick()
  awaitUntilBody<EnterRecoveryCodeFormBodyModel>()
    .onInputChange(code)
  awaitUntilBody<EnterRecoveryCodeFormBodyModel>(
    matching = { it.primaryButton.isEnabled }
  )
    .clickPrimaryButton()
  awaitUntilBody<LoadingSuccessBodyModel>(
    SocialRecoveryEventTrackerScreenId.TC_RECOVERY_CODE_VERIFICATION_SUCCESS
  )
  awaitUntilBody<LiteMoneyHomeBodyModel>()
}

suspend fun ReceiveTurbine<ScreenModel>.advanceThroughSocialChallengeVerifyScreensAsFullAccount(
  protectedCustomerName: String,
  code: String,
) {
  awaitUntilBody<MoneyHomeBodyModel>(
    MoneyHomeEventTrackerScreenId.MONEY_HOME
  ) {
    trailingToolbarAccessoryModel
      .shouldNotBeNull()
      .shouldBeTypeOf<ToolbarAccessoryModel.IconAccessory>()
      .model
      .onClick()
  }

  awaitUntilBody<SettingsBodyModel>(
    SettingsEventTrackerScreenId.SETTINGS
  ) {
    sectionModels
      .flatMap { it.rowModels }
      .firstOrNull { it.title == "Recovery Contacts" }
      .shouldNotBeNull()
      .onClick()
  }

  awaitUntilBody<FormBodyModel> {
    header?.headline.shouldBe("Recovery Contacts")
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

  awaitUntilScreenWithBody<FormBodyModel>(
    matchingScreen = { screenModel ->
      screenModel.bottomSheetModel != null
    }
  ).bottomSheetModel.shouldNotBeNull()
    .body.shouldBeInstanceOf<FormBodyModel>()
    .clickPrimaryButton()

  awaitUntilBody<VerifyingContactMethodFormBodyModel>()
    .onVideoChatClick()
  awaitUntilBody<ConfirmingIdentityFormBodyModel>()
    .onVerifiedClick()
  awaitUntilBody<EnterRecoveryCodeFormBodyModel>()
    .onInputChange(code)
  awaitUntilBody<EnterRecoveryCodeFormBodyModel>(
    matching = { it.primaryButton.isEnabled }
  )
    .clickPrimaryButton()
  awaitUntilBody<LoadingSuccessBodyModel>(
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
  awaitUntilBody<ChooseAccountAccessModel>()
    .moreOptionsButton.onClick()
  awaitUntilBody<AccountAccessMoreOptionsFormBodyModel>()
    .onRestoreYourWalletClick()
  awaitUntilBody<CloudSignInModelFake>(
    CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
  )
    .signInSuccess(cloudStoreAccount)
  awaitUntilBody<FormBodyModel>(
    CloudEventTrackerScreenId.CLOUD_BACKUP_NOT_FOUND
  )
    .restoreButton.onClick.shouldNotBeNull().invoke()
  awaitUntilBody<FormBodyModel>(
    DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_INITIATION_INSTRUCTIONS
  )
    .clickPrimaryButton()
  awaitUntilBody<FormBodyModel>(
    NotificationsEventTrackerScreenId.ENABLE_PUSH_NOTIFICATIONS
  )
    .clickPrimaryButton()
  awaitUntilBody<FormBodyModel>(
    DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_READY
  )
    .clickPrimaryButton()
  awaitUntilBody<LoadingSuccessBodyModel>(
    DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_ROTATING_AUTH_KEYS
  ) {
    state.shouldBe(LoadingSuccessBodyModel.State.Loading)
  }
  awaitUntilBody<FormBodyModel>(
    CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_INSTRUCTIONS
  )
    .clickPrimaryButton()
  awaitUntilBody<CloudSignInModelFake>(
    CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
  )
    .signInSuccess(cloudStoreAccount)
  awaitUntilBody<LoadingSuccessBodyModel>(
    DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS
  ) {
    state.shouldBe(LoadingSuccessBodyModel.State.Loading)
  }
  awaitUntilBody<FormBodyModel>(
    DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_ZERO_BALANCE
  )
    .clickPrimaryButton()
  awaitUntilBody<MoneyHomeBodyModel>(
    MoneyHomeEventTrackerScreenId.MONEY_HOME
  )
}

/**
 * Starting at the Money Home screen, advances through lost hardware and cloud recovery back to the
 * Money Home screen.
 *
 * @param isW3 when true, handles W3 confirmable NFC sessions (spending key creation + DDK upload)
 *   that occur between recovery completion and cloud backup.
 */
suspend fun ReceiveTurbine<ScreenModel>.advanceThroughLostHardwareAndCloudRecoveryToMoneyHome(
  cloudStoreAccount: CloudStoreAccount = CloudStoreAccountFake.ProtectedCustomerFake,
  isW3: Boolean = false,
) {
  awaitUntilBody<MoneyHomeBodyModel>()
    .onSecurityHubTabClick()

  awaitUntilBody<SecurityHubBodyModel>()
    .clickBitkeyDevice()

  awaitUntilBody<DeviceSettingsFormBodyModel>()
    .onReplaceDevice()
  awaitUntilBody<HardwareReplacementInstructionsModel>()
    .onContinue()
  awaitUntilBody<NewDeviceReadyQuestionBodyModel>()
    .clickPrimaryButton()
  advanceThroughReplacementHardwarePairing()
    .onCompleteRecovery()
  if (isW3) {
    // W3: confirmable NFC session for spending key creation
    awaitW3ConfirmableNfcSession()
  }
  awaitUntilBody<LoadingSuccessBodyModel>(
    HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS
  ) {
    state.shouldBe(LoadingSuccessBodyModel.State.Loading)
  }
  if (isW3) {
    // W3: confirmable NFC session for DDK upload
    awaitW3ConfirmableNfcSession()
  }
  awaitUntilBody<FormBodyModel>(
    CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_INSTRUCTIONS
  )
    .clickPrimaryButton()
  awaitUntilBody<CloudSignInModelFake>(
    CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
  )
    .signInSuccess(cloudStoreAccount)
  awaitUntilBody<LoadingSuccessBodyModel>(
    HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS
  ) {
    state.shouldBe(LoadingSuccessBodyModel.State.Loading)
  }
  awaitUntilBody<FormBodyModel>(
    HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_ZERO_BALANCE
  )
    .clickPrimaryButton()
  awaitUntilBody<SecurityHubBodyModel>()
    .onHomeTabClick()
  awaitUntilBody<MoneyHomeBodyModel>()
  cancelAndIgnoreRemainingEvents()
}

/**
 * Handles a W3 confirmable NFC session: approve the emulated prompt in the bottom sheet,
 * then confirm on the hardware confirmation screen.
 */
suspend fun ReceiveTurbine<ScreenModel>.awaitW3ConfirmableNfcSession() {
  awaitUntilScreenWithBody<BodyModel>(
    matchingScreen = { it.bottomSheetModel?.body is PromptSelectionFormBodyModel }
  ).let { (checkNotNull(it.bottomSheetModel).body as PromptSelectionFormBodyModel).onApprove() }
  awaitUntilBody<HardwareConfirmationScreenModel> { onConfirm() }
}

private suspend fun ReceiveTurbine<ScreenModel>.advanceThroughReplacementHardwarePairing():
  DelayAndNotifyNewKeyReady {
  // First screen: activation instructions (V1 or V2 depending on isW3Flow)
  awaitUntilBody<PairNewHardwareBodyModel>(matching = { !it.primaryButton.isLoading })
    .clickPrimaryButton()

  // After NFC, the detected hardware type determines the rest of the flow:
  // - W1 hardware → PairNewHardwareBodyModel (pair instructions) → fingerprint
  // - W3 hardware → CompleteTwoTapBodyModel
  val nextBody = awaitUntilScreenWithBody<BodyModel>(
    matchingBody = { it is PairNewHardwareBodyModel || it is CompleteTwoTapBodyModel }
  ).body

  when (nextBody) {
    is PairNewHardwareBodyModel -> {
      // W1 hardware detected after NFC: click pair instructions to continue
      nextBody.clickPrimaryButton()
    }
    is CompleteTwoTapBodyModel -> {
      // W3 hardware detected: complete two tap
      nextBody.clickPrimaryButton()
    }
  }

  return awaitUntilBody<DelayAndNotifyNewKeyReady>(HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_READY)
}
