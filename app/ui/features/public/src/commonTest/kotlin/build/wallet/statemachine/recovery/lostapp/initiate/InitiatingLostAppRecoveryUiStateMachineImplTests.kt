package build.wallet.statemachine.recovery.lostapp.initiate

import app.cash.turbine.plusAssign
import bitkey.account.AccountConfigServiceFake
import bitkey.backup.DescriptorBackup
import build.wallet.auth.AccountAuthTokensMock
import build.wallet.bitkey.auth.HwAuthSecp256k1PublicKeyMock
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keybox.AppKeyBundleMock
import build.wallet.bitkey.spending.HwSpendingPublicKeyMock
import build.wallet.cloud.backup.csek.SsekDaoFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.auth.AuthF8eClient.InitiateAuthenticationSuccess
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.nfc.NfcCommandsMock
import build.wallet.nfc.NfcSessionFake
import build.wallet.recovery.DescriptorBackupServiceFake
import build.wallet.recovery.DescriptorBackupServiceFake.Companion.HW_DESCRIPTOR_PUBKEY
import build.wallet.recovery.LostAppAndCloudRecoveryService.CompletedAuth
import build.wallet.statemachine.BodyStateMachineMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Root
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.*
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.platform.permissions.EnableNotificationsUiProps
import build.wallet.statemachine.platform.permissions.EnableNotificationsUiStateMachine
import build.wallet.statemachine.recovery.inprogress.RecoverYourAppKeyBodyModel
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiProps
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import okio.ByteString.Companion.encodeUtf8

class InitiatingLostAppRecoveryUiStateMachineImplTests : FunSpec({

  val nfcSessionUIStateMachine =
    object : NfcSessionUIStateMachine,
      ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>(
        id = "nfc-session"
      ) {}

  val enableNotificationsUiStateMachine =
    object : EnableNotificationsUiStateMachine,
      BodyStateMachineMock<EnableNotificationsUiProps>(
        id = "enable-notifications"
      ) {}

  val recoveryNotificationVerificationUiStateMachine =
    object : RecoveryNotificationVerificationUiStateMachine,
      ScreenStateMachineMock<RecoveryNotificationVerificationUiProps>(
        id = "recovery-notification-verification"
      ) {}

  val accountConfigService = AccountConfigServiceFake()
  val ssekDao = SsekDaoFake()
  val descriptorBackupService = DescriptorBackupServiceFake()
  val nfcCommandsMock = NfcCommandsMock(turbines::create)

  val stateMachine = InitiatingLostAppRecoveryUiStateMachineImpl(
    nfcSessionUIStateMachine = nfcSessionUIStateMachine,
    enableNotificationsUiStateMachine = enableNotificationsUiStateMachine,
    recoveryNotificationVerificationUiStateMachine = recoveryNotificationVerificationUiStateMachine,
    accountConfigService = accountConfigService,
    ssekDao = ssekDao,
    descriptorBackupService = descriptorBackupService
  )

  val rollbackCalls = turbines.create<Unit>("rollback calls")
  val retreatCalls = turbines.create<Unit>("retreat calls")
  val addHardwareAuthKeyCalls = turbines.create<Unit>("add hardware auth key calls")
  val addSignedChallengeCalls = turbines.create<Unit>("add signed challenge calls")
  val completeCalls = turbines.create<Unit>("complete calls")
  val onCompleteCalls = turbines.create<Unit>("onComplete calls")
  val onCancelRecoveryCalls = turbines.create<Unit>("cancel recovery calls")
  val onAcknowledgeCalls = turbines.create<Unit>("acknowledge calls")

  beforeTest {
    accountConfigService.reset()
    ssekDao.reset()
    descriptorBackupService.reset()
    nfcCommandsMock.reset()
  }

  test("shows recovery instructions when awaiting hardware keys") {
    val props = InitiatingLostAppRecoveryUiProps(
      initiatingLostAppRecoveryData = AwaitingHwKeysData(
        addHardwareAuthKey = { addHardwareAuthKeyCalls += Unit },
        rollback = { rollbackCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<RecoverYourAppKeyBodyModel> {
        onBack.shouldNotBeNull()
        onStartRecovery.shouldNotBeNull()
      }
    }
  }

  test("calls rollback when back button pressed on recovery instructions") {
    val props = InitiatingLostAppRecoveryUiProps(
      initiatingLostAppRecoveryData = AwaitingHwKeysData(
        addHardwareAuthKey = { addHardwareAuthKeyCalls += Unit },
        rollback = { rollbackCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<RecoverYourAppKeyBodyModel> {
        onBack.shouldNotBeNull().invoke()
      }
    }

    rollbackCalls.awaitItem()
  }

  test("shows NFC session when starting recovery from instructions") {
    val props = InitiatingLostAppRecoveryUiProps(
      initiatingLostAppRecoveryData = AwaitingHwKeysData(
        addHardwareAuthKey = { addHardwareAuthKeyCalls += Unit },
        rollback = { rollbackCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<RecoverYourAppKeyBodyModel> {
        onStartRecovery.shouldNotBeNull().invoke()
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(
        id = nfcSessionUIStateMachine.id
      ) {
        // Verify NFC session props
        screenPresentationStyle.shouldBe(Root)
        shouldLock.shouldBe(false)
      }
    }
  }

  test("shows enable notifications UI when awaiting push notification permission") {
    val props = InitiatingLostAppRecoveryUiProps(
      initiatingLostAppRecoveryData = AwaitingPushNotificationPermissionData(
        onComplete = { onCompleteCalls += Unit },
        onRetreat = { retreatCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBodyMock<EnableNotificationsUiProps>(
        id = enableNotificationsUiStateMachine.id
      )
    }
  }

  test("calls retreat when retreat action invoked on enable notifications UI") {
    val props = InitiatingLostAppRecoveryUiProps(
      initiatingLostAppRecoveryData = AwaitingPushNotificationPermissionData(
        onComplete = { onCompleteCalls += Unit },
        onRetreat = { retreatCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBodyMock<EnableNotificationsUiProps>(
        id = enableNotificationsUiStateMachine.id
      ) {
        retreat.onRetreat()
      }
    }

    retreatCalls.awaitItem()
  }

  test("shows loading screen when authenticating with F8E via app") {
    val props = InitiatingLostAppRecoveryUiProps(
      initiatingLostAppRecoveryData = AuthenticatingWithF8EViaAppData(
        rollback = { rollbackCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        message.shouldBe("Authenticating with hardware...")
        onBack.shouldNotBeNull()
      }
    }
  }

  test("calls rollback when back button pressed on authenticating screen") {
    val props = InitiatingLostAppRecoveryUiProps(
      initiatingLostAppRecoveryData = AuthenticatingWithF8EViaAppData(
        rollback = { rollbackCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        onBack.shouldNotBeNull().invoke()
      }
    }

    rollbackCalls.awaitItem()
  }

  test("shows error screen when authentication fails") {
    val error = RuntimeException("Authentication failed")
    val props = InitiatingLostAppRecoveryUiProps(
      initiatingLostAppRecoveryData = FailedToAuthenticateWithF8EViaAppData(
        error = error,
        retry = { completeCalls += Unit },
        rollback = { rollbackCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        header?.headline.shouldBe("We couldn’t initiate recovery process.")
        primaryButton?.text.shouldBe("OK")
      }
    }
  }

  test("calls rollback when OK button pressed on authentication error screen") {
    val error = RuntimeException("Authentication failed")
    val props = InitiatingLostAppRecoveryUiProps(
      initiatingLostAppRecoveryData = FailedToAuthenticateWithF8EViaAppData(
        error = error,
        retry = { completeCalls += Unit },
        rollback = { rollbackCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        primaryButton?.onClick?.invoke()
      }
    }

    rollbackCalls.awaitItem()
  }

  test("shows loading screen when initiating app auth with F8e") {
    val props = InitiatingLostAppRecoveryUiProps(
      initiatingLostAppRecoveryData = InitiatingAppAuthWithF8eData(
        rollback = { rollbackCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        message.shouldBe("Authenticating with server...")
        onBack.shouldNotBeNull()
      }
    }
  }

  test("calls rollback when back button pressed on app auth initiation screen") {
    val props = InitiatingLostAppRecoveryUiProps(
      initiatingLostAppRecoveryData = InitiatingAppAuthWithF8eData(
        rollback = { rollbackCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        onBack.shouldNotBeNull().invoke()
      }
    }

    rollbackCalls.awaitItem()
  }

  test("shows error screen when app auth initiation fails") {
    val props = InitiatingLostAppRecoveryUiProps(
      initiatingLostAppRecoveryData = FailedToInitiateAppAuthWithF8eData(
        error = Error(),
        retry = { completeCalls += Unit },
        rollback = { rollbackCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        header?.headline.shouldBe("We couldn’t initiate recovery process.")
        primaryButton?.text.shouldBe("OK")
      }
    }
  }

  test("calls rollback when OK button pressed on app auth initiation error screen") {
    val props = InitiatingLostAppRecoveryUiProps(
      initiatingLostAppRecoveryData = FailedToInitiateAppAuthWithF8eData(
        error = Error(),
        retry = { completeCalls += Unit },
        rollback = { rollbackCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        primaryButton?.onClick?.invoke()
      }
    }

    rollbackCalls.awaitItem()
  }

  test("shows NFC session when awaiting app signed auth challenge") {
    val challenge = InitiateAuthenticationSuccess(
      username = "test-username",
      session = "test-session",
      accountId = FullAccountIdMock.serverId,
      challenge = "test-challenge"
    )

    val props = InitiatingLostAppRecoveryUiProps(
      initiatingLostAppRecoveryData = AwaitingAppSignedAuthChallengeData(
        challenge = challenge,
        addSignedChallenge = { addSignedChallengeCalls += Unit },
        rollback = { rollbackCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(
        id = nfcSessionUIStateMachine.id
      ) {
        screenPresentationStyle.shouldBe(Root)
        shouldLock.shouldBe(false)
      }
    }
  }

  test("shows NFC session when awaiting hardware proof of possession with direct keys") {
    val completedAuth = CompletedAuth.WithDirectKeys(
      accountId = FullAccountIdMock,
      authTokens = AccountAuthTokensMock,
      hwAuthKey = HwAuthSecp256k1PublicKeyMock,
      destinationAppKeys = AppKeyBundleMock,
      existingHwSpendingKeys = listOf(HwSpendingPublicKeyMock)
    )

    val props = InitiatingLostAppRecoveryUiProps(
      initiatingLostAppRecoveryData = AwaitingHardwareProofOfPossessionAndKeysData(
        completedAuth = completedAuth,
        onComplete = { _, _, _ -> onCompleteCalls += Unit },
        rollback = { rollbackCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(
        id = nfcSessionUIStateMachine.id
      ) {
        screenPresentationStyle.shouldBe(Root)
      }
    }
  }

  val descriptorBackupScenarios: List<Pair<String, XCiphertext?>> =
    listOf(
      "without private wallet root xpub" to null,
      "with private wallet root xpub" to XCiphertext("fake-private-wallet-root-xpub")
    )

  descriptorBackupScenarios.forEach { (scenario, privateWalletRootXpub) ->
    test(
      "extracts hardware keys from descriptor backups when awaiting hardware proof of possession ($scenario)"
    ) {
      val descriptorBackup = DescriptorBackup(
        keysetId = "test-keyset-123",
        sealedDescriptor = XCiphertext("fake-sealed-descriptor"),
        privateWalletRootXpub = privateWalletRootXpub
      )
      val sealedSsek = "fake-sealed-ssek".encodeUtf8()

      val completedAuth = CompletedAuth.WithDescriptorBackups(
        accountId = FullAccountIdMock,
        authTokens = AccountAuthTokensMock,
        hwAuthKey = HwAuthSecp256k1PublicKeyMock,
        destinationAppKeys = AppKeyBundleMock,
        descriptorBackups = listOf(descriptorBackup),
        wrappedSsek = sealedSsek
      )

      val props = InitiatingLostAppRecoveryUiProps(
        initiatingLostAppRecoveryData = AwaitingHardwareProofOfPossessionAndKeysData(
          completedAuth = completedAuth,
          onComplete = { _, _, _ -> onCompleteCalls += Unit },
          rollback = { rollbackCalls += Unit }
        )
      )

      stateMachine.test(props) {
        awaitBodyMock<NfcSessionUIStateMachineProps<*>>(
          id = nfcSessionUIStateMachine.id
        ) {
          session(NfcSessionFake(), nfcCommandsMock)
        }
      }

      // Regardless of the descriptor backup provided, the descriptor service fake always
      // returns the same HW_DESCRIPTOR_PUBKEY. This simply verifies that we DID go through the descriptor
      // backup decryption flow, further validated by there being an unsealed ssek available in the ssekDao.
      nfcCommandsMock.getNextSpendingKeyCalls.awaitItem()
        .shouldBeInstanceOf<List<HwSpendingPublicKey>>()
        .shouldContainExactly(HwSpendingPublicKey(HW_DESCRIPTOR_PUBKEY))
      ssekDao.get(sealedSsek).shouldNotBeNull()
    }
  }

  test("shows loading screen when initiating recovery with F8e") {
    val props = InitiatingLostAppRecoveryUiProps(
      initiatingLostAppRecoveryData = InitiatingLostAppRecoveryWithF8eData(
        rollback = { rollbackCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        message.shouldBe("Initiating recovery...")
        onBack.shouldNotBeNull()
      }
    }
  }

  test("calls rollback when back button pressed on recovery initiation screen") {
    val props = InitiatingLostAppRecoveryUiProps(
      initiatingLostAppRecoveryData = InitiatingLostAppRecoveryWithF8eData(
        rollback = { rollbackCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        onBack.shouldNotBeNull().invoke()
      }
    }

    rollbackCalls.awaitItem()
  }

  test("shows error screen when F8e recovery initiation fails") {
    val props = InitiatingLostAppRecoveryUiProps(
      initiatingLostAppRecoveryData = FailedToInitiateLostAppWithF8eData(
        error = Error(),
        retry = { completeCalls += Unit },
        rollback = { rollbackCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        header?.headline.shouldBe("We couldn’t initiate recovery process.")
        primaryButton?.text.shouldBe("OK")
      }
    }
  }

  test("calls rollback when OK button pressed on F8e recovery initiation error screen") {
    val props = InitiatingLostAppRecoveryUiProps(
      initiatingLostAppRecoveryData = FailedToInitiateLostAppWithF8eData(
        error = Error(),
        retry = { completeCalls += Unit },
        rollback = { rollbackCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        primaryButton?.onClick?.invoke()
      }
    }

    rollbackCalls.awaitItem()
  }

  test("shows notification verification UI when verifying notification comms") {
    val props = InitiatingLostAppRecoveryUiProps(
      initiatingLostAppRecoveryData = VerifyingNotificationCommsData(
        fullAccountId = FullAccountIdMock,
        hwFactorProofOfPossession = HwFactorProofOfPossession("test-proof"),
        onRollback = { rollbackCalls += Unit },
        onComplete = { onCompleteCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBodyMock<RecoveryNotificationVerificationUiProps>(
        id = recoveryNotificationVerificationUiStateMachine.id
      ) {
        fullAccountId.shouldBe(FullAccountIdMock)
        hwFactorProofOfPossession?.hwSignedToken.shouldBe("test-proof")
      }
    }
  }

  test("shows recovery conflict model when displaying conflicting recovery") {
    val props = InitiatingLostAppRecoveryUiProps(
      initiatingLostAppRecoveryData = DisplayingConflictingRecoveryData(
        onCancelRecovery = { onCancelRecoveryCalls += Unit },
        onRetreat = { retreatCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<RecoveryConflictBodyModel> {
        onCancelRecovery.shouldNotBeNull()
      }
    }
  }

  test("calls onCancelRecovery when cancel recovery action invoked on conflict screen") {
    val props = InitiatingLostAppRecoveryUiProps(
      initiatingLostAppRecoveryData = DisplayingConflictingRecoveryData(
        onCancelRecovery = { onCancelRecoveryCalls += Unit },
        onRetreat = { retreatCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<RecoveryConflictBodyModel> {
        onCancelRecovery.shouldNotBeNull().invoke()
      }
    }

    onCancelRecoveryCalls.awaitItem()
  }

  test("shows loading screen when cancelling conflicting recovery") {
    val props = InitiatingLostAppRecoveryUiProps(
      initiatingLostAppRecoveryData = CancellingConflictingRecoveryData
    )

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        message.shouldBe("Cancelling Existing Recovery")
      }
    }
  }

  test("shows error screen when failing to cancel conflicting recovery") {
    val props = InitiatingLostAppRecoveryUiProps(
      initiatingLostAppRecoveryData = FailedToCancelConflictingRecoveryData(
        cause = Error(),
        onAcknowledge = { onAcknowledgeCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        header?.headline.shouldBe("We couldn’t cancel the existing recovery. Please try your recovery again.")
        primaryButton?.text.shouldBe("OK")
      }
    }
  }

  test("calls onAcknowledge when OK button pressed on cancel conflicting recovery error screen") {
    val props = InitiatingLostAppRecoveryUiProps(
      initiatingLostAppRecoveryData = FailedToCancelConflictingRecoveryData(
        cause = Error(),
        onAcknowledge = { onAcknowledgeCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        primaryButton?.onClick?.invoke()
      }
    }

    onAcknowledgeCalls.awaitItem()
  }
})
