package build.wallet.statemachine.walletmigration

import bitkey.account.HardwareType
import bitkey.privilegedactions.ActionProofServiceFake
import build.wallet.account.AccountServiceFake
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.EventTrackerContext
import build.wallet.analytics.events.screen.EventTrackerCountInfo
import build.wallet.analytics.events.screen.EventTrackerFingerprintScanStatsInfo
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.WalletMigrationEventTrackerScreenId
import build.wallet.analytics.v1.Action
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.transactions.TransactionsDataMock
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.UtxoMaxConsolidationCountFeatureFlag
import build.wallet.bitkey.auth.AppAuthPublicKeysMock
import build.wallet.bitkey.auth.HwAuthSecp256k1PublicKeyMock
import build.wallet.bitkey.f8e.F8eSpendingKeysetMock
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.keybox.AppKeyBundleMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.keybox.withNewSpendingKeyset
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.chaincode.delegation.ChaincodeExtractorFake
import build.wallet.cloud.backup.csek.SsekDaoFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.encrypt.WsmVerifierMock
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.recovery.SignedKeysetVerificationResponseMock
import build.wallet.firmware.FirmwareDeviceInfoDaoFake
import build.wallet.keybox.keys.AppKeysGeneratorMock
import build.wallet.nfc.NfcException
import build.wallet.nfc.platform.UpgradeRotateAppAuthKeysResult
import build.wallet.nfc.platform.UpgradeAuthorizeW3Result
import build.wallet.relationships.RelationshipsCryptoFake
import build.wallet.relationships.RelationshipsKeysDaoFake
import build.wallet.relationships.RelationshipsKeysRepository
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareProps
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachine
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupProps
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupUiStateMachine
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcConfirmableSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcConfirmableSessionUiStateMachineMock
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.recovery.sweep.SweepUiProps
import build.wallet.statemachine.recovery.sweep.SweepUiStateMachine
import build.wallet.statemachine.utxo.UtxoConsolidationProps
import build.wallet.statemachine.utxo.UtxoConsolidationUiStateMachine
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilBodyMock
import build.wallet.wallet.migration.MigrationProgress
import build.wallet.wallet.migration.MigrationServiceFake
import build.wallet.wallet.migration.MigrationType
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import okio.ByteString.Companion.encodeUtf8

/**
 * Tests for hardware type enforcement in W3UpgradeUiStateMachineImpl.
 *
 * Verifies that:
 * - Old hardware (W1) tap: skip paired-hardware verification, shows wrong hardware
 *   error if W3 is tapped instead
 * - New hardware (W3) taps: hardwareTypeOverride = W3, shows wrong hardware error
 *   if W1 is tapped instead
 * - Wrong hardware errors show a retry button that returns to the correct state
 */
class W3UpgradeUiStateMachineImplHardwareTypeTests : FunSpec({

  val migrationService = MigrationServiceFake()
  val appKeysGenerator = AppKeysGeneratorMock()
  val actionProofService = ActionProofServiceFake()
  val firmwareDeviceInfoDao = FirmwareDeviceInfoDaoFake()
  val ssekDao = SsekDaoFake()
  val accountService = AccountServiceFake()

  val chaincodeExtractor = ChaincodeExtractorFake()
  val bitcoinWalletService = BitcoinWalletServiceFake()
  val utxoConsolidationUiStateMachine =
    object : UtxoConsolidationUiStateMachine,
      ScreenStateMachineMock<UtxoConsolidationProps>("utxo-consolidation") {}
  val utxoMaxConsolidationCountFeatureFlag = UtxoMaxConsolidationCountFeatureFlag(
    featureFlagDao = FeatureFlagDaoFake()
  )

  val relationshipsKeysRepository = RelationshipsKeysRepository(
    RelationshipsCryptoFake(),
    RelationshipsKeysDaoFake()
  )

  val pairNewHardwareUiStateMachine =
    object : PairNewHardwareUiStateMachine,
      ScreenStateMachineMock<PairNewHardwareProps>("pair-new-hardware") {}

  val proofOfPossessionNfcStateMachine =
    object : ProofOfPossessionNfcStateMachine,
      ScreenStateMachineMock<ProofOfPossessionNfcProps>("hw-proof-of-possession") {}

  val sweepUiStateMachine =
    object : SweepUiStateMachine,
      ScreenStateMachineMock<SweepUiProps>("sweep") {}

  val fullAccountCloudSignInAndBackupUiStateMachine =
    object : FullAccountCloudSignInAndBackupUiStateMachine,
      ScreenStateMachineMock<FullAccountCloudSignInAndBackupProps>("cloud-backup") {}

  val nfcSessionUIStateMachine =
    object : NfcSessionUIStateMachine,
      ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>("nfc-session") {}

  val nfcConfirmableSessionUiStateMachine =
    NfcConfirmableSessionUiStateMachineMock(id = "nfc-confirmable-session")

  val authKeyRotationProgress = MigrationProgress.AuthKeyRotation(
    type = MigrationType.W3Upgrade,
    currentKeybox = KeyboxMock,
    newKeyset = SpendingKeysetMock
  )

  fun w3UpgradeKeyset() =
    SpendingKeysetMock.copy(
      localId = "uuid-0",
      appKey = AppKeyBundleMock.spendingKey,
      hardwareKey = build.wallet.bitkey.keybox.HwKeyBundleMock.spendingKey,
      f8eSpendingKeyset = F8eSpendingKeysetMock
    )

  val rotationResult = UpgradeRotateAppAuthKeysResult(
    hwSignedAccountId = "signed-account-id",
    appGlobalAuthKeyHwSignature = "hw-app-auth-signature",
    hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock
  )

  fun rotatedKeybox() =
    FullAccountMock.keybox
      .withNewSpendingKeyset(w3UpgradeKeyset())
      .copy(
        config = FullAccountMock.keybox.config.copy(hardwareType = HardwareType.W3),
        activeAppKeyBundle = FullAccountMock.keybox.activeAppKeyBundle.copy(
          authKey = AppAuthPublicKeysMock.appGlobalAuthPublicKey,
          recoveryAuthKey = AppAuthPublicKeysMock.appRecoveryAuthPublicKey
        ),
        appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature(
          rotationResult.appGlobalAuthKeyHwSignature
        )
      )

  fun createStateMachine() =
    W3UpgradeUiStateMachineImpl(
      pairNewHardwareUiStateMachine = pairNewHardwareUiStateMachine,
      proofOfPossessionNfcStateMachine = proofOfPossessionNfcStateMachine,
      sweepUiStateMachine = sweepUiStateMachine,
      migrationService = migrationService,
      fullAccountCloudSignInAndBackupUiStateMachine = fullAccountCloudSignInAndBackupUiStateMachine,
      ssekDao = ssekDao,
      accountService = accountService,
      firmwareDeviceInfoDao = firmwareDeviceInfoDao,
      nfcSessionUIStateMachine = nfcSessionUIStateMachine,
      nfcConfirmableSessionUiStateMachine = nfcConfirmableSessionUiStateMachine,
      actionProofService = actionProofService,
      relationshipsKeysRepository = relationshipsKeysRepository,
      appKeysGenerator = appKeysGenerator,
      chaincodeExtractor = chaincodeExtractor,
      bitcoinWalletService = bitcoinWalletService,
      wsmVerifier = WsmVerifierMock(),
      utxoConsolidationUiStateMachine = utxoConsolidationUiStateMachine,
      utxoMaxConsolidationCountFeatureFlag = utxoMaxConsolidationCountFeatureFlag,
      eventTracker = noopEventTracker
    )

  val onUpgradeCompleteCalls = turbines.create<Unit>("on upgrade complete calls")
  val onExitCalls = turbines.create<Unit>("on exit calls")

  val props = W3UpgradeUiProps(
    account = FullAccountMock,
    onUpgradeComplete = { onUpgradeCompleteCalls.add(Unit) },
    onExit = { onExitCalls.add(Unit) }
  )

  beforeTest {
    migrationService.reset()
    appKeysGenerator.reset()
    actionProofService.reset()
    firmwareDeviceInfoDao.reset()
    ssekDao.reset()
    bitcoinWalletService.reset()
    bitcoinWalletService.transactionsData.value = TransactionsDataMock
  }

  test("TappingOldHardwareForPoP skips paired-hardware verification") {
    // Resume with an in-progress AuthKeyRotation so the state machine shows instructions first
    migrationService.resumeResult = Ok(authKeyRotationProgress)

    val stateMachine = createStateMachine()
    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel> {
        onContinue()
      }

      // Now at TappingOldHardwareForPoP — verify the old hardware path skips pairing checks.
      awaitUntilBodyMock<ProofOfPossessionNfcProps>(
        id = proofOfPossessionNfcStateMachine.id
      ) {
        hardwareVerification.shouldBe(
          NfcSessionUIStateMachineProps.HardwareVerification.NotRequired
        )
      }
    }
  }

  test("TappingOldHardwareForPoP wrong hardware shows error with retry") {
    migrationService.resumeResult = Ok(authKeyRotationProgress)

    val stateMachine = createStateMachine()
    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel> {
        onContinue()
      }

      // Trigger wrong hardware error — W3 tapped when W1 was expected
      val popProps = awaitUntilBodyMock<ProofOfPossessionNfcProps>(
        id = proofOfPossessionNfcStateMachine.id
      ) {}
      val handled = popProps.onError(
        NfcException.WrongHardwareType(expected = HardwareType.W1, actual = HardwareType.W3)
      )
      handled.shouldBe(true)

      // Should show "Wrong Bitkey tapped" with retry
      awaitUntilBody<FormBodyModel> {
        header?.headline.shouldBe("Wrong Bitkey tapped")
        header?.sublineModel?.string.shouldNotBeNull().shouldContain("Please tap your old Bitkey device to continue.")
        primaryButton.shouldNotBeNull().text.shouldBe("Retry")
        eventTrackerScreenInfo?.eventTrackerScreenId
          .shouldBe(WalletMigrationEventTrackerScreenId.W3_UPGRADE_WRONG_HARDWARE_ERROR)
        // Retry goes back to the NFC tap state
        primaryButton.shouldNotBeNull().onClick()
      }

      // Should return to NFC tap
      awaitUntilBodyMock<ProofOfPossessionNfcProps>(
        id = proofOfPossessionNfcStateMachine.id
      ) {
        requiredHardwareType.shouldBe(HardwareType.W1)
      }
    }
  }

  test("TappingNewHardwareForRotation wrong hardware shows error with retry") {
    migrationService.resumeResult = Ok(authKeyRotationProgress)
    val descriptorBackupProgress = MigrationProgress.DescriptorBackup(
      type = MigrationType.W3Upgrade,
      currentKeybox = rotatedKeybox(),
      newKeyset = w3UpgradeKeyset()
    )
    migrationService.proceedResult = Ok(descriptorBackupProgress)

    val stateMachine = createStateMachine()
    stateMachine.test(props) {
      // Navigate through old hardware auth rotation
      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel> {
        onContinue()
      }
      val popProps = awaitUntilBodyMock<ProofOfPossessionNfcProps>(
        id = proofOfPossessionNfcStateMachine.id
      ) {}
      (popProps.request as build.wallet.statemachine.auth.Request.HwKeyProof)
        .onSuccess(HwFactorProofOfPossession("w1-proof"))

      // Reach the new hardware rotation tap
      awaitUntilBody<W3UpgradeNewHardwareAuthRotationInstructionsBodyModel> {
        onContinue()
      }

      // Trigger wrong hardware error — W1 tapped when W3 was expected
      val confirmProps =
        awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeRotateAppAuthKeysResult>>(
          id = nfcConfirmableSessionUiStateMachine.id
        ) {}
      val handled = confirmProps.onError(
        NfcException.WrongHardwareType(expected = HardwareType.W3, actual = HardwareType.W1)
      )
      handled.shouldBe(true)

      // Should show "Wrong Bitkey tapped" with retry
      awaitUntilBody<FormBodyModel> {
        header?.headline.shouldBe("Wrong Bitkey tapped")
        header?.sublineModel?.string.shouldNotBeNull().shouldContain("Please tap your new Bitkey device to continue.")
        primaryButton.shouldNotBeNull().text.shouldBe("Retry")
        primaryButton.shouldNotBeNull().onClick()
      }

      // Should return to NFC tap
      awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeRotateAppAuthKeysResult>>(
        id = nfcConfirmableSessionUiStateMachine.id
      ) {
        hardwareVerification.shouldBe(
          NfcSessionUIStateMachineProps.HardwareVerification.NotRequired
        )
      }
    }
  }

  test("AuthorizingW3Upgrade wrong hardware shows error with retry") {
    // Resume with DdkBackup progress — routes to PreparingUpgradeAuthorization → AuthorizingW3Upgrade
    val ddkBackupProgress = MigrationProgress.DdkBackup(
      type = MigrationType.W3Upgrade,
      currentKeybox = KeyboxMock,
      newKeyset = SpendingKeysetMock
    )
    migrationService.resumeResult = Ok(ddkBackupProgress)

    val stateMachine = createStateMachine()
    stateMachine.test(props) {
      // Reach the AuthorizingW3Upgrade tap
      val upgradeProps =
        awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeAuthorizeW3Result>>(
          id = nfcConfirmableSessionUiStateMachine.id
        ) {}

      // Trigger wrong hardware error — W1 tapped when W3 was expected
      val handled = upgradeProps.onError(
        NfcException.WrongHardwareType(expected = HardwareType.W3, actual = HardwareType.W1)
      )
      handled.shouldBe(true)

      // Should show "Wrong Bitkey tapped" with retry
      awaitUntilBody<FormBodyModel> {
        header?.headline.shouldBe("Wrong Bitkey tapped")
        header?.sublineModel?.string.shouldNotBeNull().shouldContain("Please tap your new Bitkey device to continue.")
        primaryButton.shouldNotBeNull().text.shouldBe("Retry")
        primaryButton.shouldNotBeNull().onClick()
      }

      // Should return to NFC tap
      awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeAuthorizeW3Result>>(
        id = nfcConfirmableSessionUiStateMachine.id
      ) {}
    }
  }

  test("AuthorizingW3Upgrade NFC session skips hardware pairing verification") {
    // Resume with DdkBackup progress — now routes to PreparingUpgradeAuthorization → AuthorizingW3Upgrade
    val ddkBackupProgress = MigrationProgress.DdkBackup(
      type = MigrationType.W3Upgrade,
      currentKeybox = KeyboxMock,
      newKeyset = SpendingKeysetMock
    )
    migrationService.resumeResult = Ok(ddkBackupProgress)

    val stateMachine = createStateMachine()
    stateMachine.test(props) {
      // AuthorizingW3Upgrade uses confirmable NFC — verify hardware verification is not required
      awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<*>>(
        id = nfcConfirmableSessionUiStateMachine.id
      ) {
        hardwareVerification.shouldBe(
          NfcSessionUIStateMachineProps.HardwareVerification.NotRequired
        )
      }
    }
  }

  test("ProvisioningHardwareDescriptor wrong hardware shows error with retry") {
    // Resume to get to ProvisioningHardwareDescriptor
    val authRotation = MigrationProgress.AuthKeyRotation(
      type = MigrationType.W3Upgrade,
      currentKeybox = FullAccountMock.keybox,
      newKeyset = w3UpgradeKeyset()
    )
    val descriptorBackup = MigrationProgress.DescriptorBackup(
      type = MigrationType.W3Upgrade,
      currentKeybox = rotatedKeybox(),
      newKeyset = w3UpgradeKeyset()
    )
    migrationService.resumeResult = Ok(authRotation)
    migrationService.proceedResults.addAll(
      listOf(
        // proceed(AuthKeyRotation) → DescriptorBackup
        Ok(descriptorBackup),
        // proceed(DescriptorBackup) → ServerKeysetActivation
        Ok(
          MigrationProgress.ServerKeysetActivation(
            type = MigrationType.W3Upgrade,
            currentKeybox = rotatedKeybox(),
            newKeyset = w3UpgradeKeyset()
          )
        ),
        // proceed(ServerKeysetActivation) → HardwareDescriptorProvisioning
        Ok(
          MigrationProgress.HardwareDescriptorProvisioning(
            type = MigrationType.W3Upgrade,
            currentKeybox = rotatedKeybox(),
            newKeyset = w3UpgradeKeyset(),
            signedKeysResponse = SignedKeysetVerificationResponseMock
          )
        ),
        // proceed(DdkBackup) → CloudBackup (DDK upload)
        Ok(
          MigrationProgress.CloudBackup(
            type = MigrationType.W3Upgrade,
            currentKeybox = rotatedKeybox(),
            newKeyset = w3UpgradeKeyset()
          )
        )
      )
    )

    val stateMachine = createStateMachine()
    stateMachine.test(props) {
      // Navigate through auth rotation
      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel> {
        onContinue()
      }
      val popProps = awaitUntilBodyMock<ProofOfPossessionNfcProps>(
        id = proofOfPossessionNfcStateMachine.id
      ) {}
      (popProps.request as build.wallet.statemachine.auth.Request.HwKeyProof)
        .onSuccess(HwFactorProofOfPossession("w1-proof"))

      awaitUntilBody<W3UpgradeNewHardwareAuthRotationInstructionsBodyModel> {
        onContinue()
      }
      val confirmProps =
        awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeRotateAppAuthKeysResult>>(
          id = nfcConfirmableSessionUiStateMachine.id
        ) {}
      confirmProps.onSuccess(rotationResult)

      // AuthorizingW3Upgrade
      val upgradeProps =
        awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeAuthorizeW3Result>>(
          id = nfcConfirmableSessionUiStateMachine.id
        ) {}
      upgradeProps.onSuccess(
        UpgradeAuthorizeW3Result(
          descriptorBackupsSignature = "descriptor-hw-sig",
          activateKeysetSignature = "activate-hw-sig",
          sealedDdkData = "sealed-ddk".encodeUtf8()
        )
      )

      // Reach ProvisioningHardwareDescriptor NFC tap
      val provisioningProps =
        awaitUntilBodyMock<NfcSessionUIStateMachineProps<*>>(id = nfcSessionUIStateMachine.id) {}

      // Trigger wrong hardware error — W1 tapped when W3 was expected
      val handled = provisioningProps.onError(
        NfcException.WrongHardwareType(expected = HardwareType.W3, actual = HardwareType.W1)
      )
      handled.shouldBe(true)

      // Should show "Wrong Bitkey tapped" with retry
      awaitUntilBody<FormBodyModel> {
        header?.headline.shouldBe("Wrong Bitkey tapped")
        header?.sublineModel?.string.shouldNotBeNull().shouldContain("Please tap your new Bitkey device to continue.")
        primaryButton.shouldNotBeNull().text.shouldBe("Retry")
        primaryButton.shouldNotBeNull().onClick()
      }

      // Should return to ProvisioningHardwareDescriptor NFC tap
      awaitUntilBodyMock<NfcSessionUIStateMachineProps<*>>(id = nfcSessionUIStateMachine.id) {
        config.hardwareVerification.shouldBe(
          NfcSessionUIStateMachineProps.HardwareVerification.Required()
        )
      }
    }
  }

  test("ProvisioningHardwareDescriptor unpaired hardware (W1 tapped) shows wrong hardware error") {
    // When hardwareVerification = Required(), the pairing interceptor checks the serial
    // BEFORE verifyHardwareType in the session lambda. If a W1 is tapped when W3 is expected,
    // the serial mismatch throws UnpairedHardwareError, not WrongHardwareType.
    // This test verifies that UnpairedHardwareError is intercepted and shown as a
    // "Wrong Bitkey tapped" error with retry.
    val authRotation = MigrationProgress.AuthKeyRotation(
      type = MigrationType.W3Upgrade,
      currentKeybox = FullAccountMock.keybox,
      newKeyset = w3UpgradeKeyset()
    )
    val descriptorBackup = MigrationProgress.DescriptorBackup(
      type = MigrationType.W3Upgrade,
      currentKeybox = rotatedKeybox(),
      newKeyset = w3UpgradeKeyset()
    )
    migrationService.resumeResult = Ok(authRotation)
    migrationService.proceedResults.addAll(
      listOf(
        Ok(descriptorBackup),
        Ok(
          MigrationProgress.ServerKeysetActivation(
            type = MigrationType.W3Upgrade,
            currentKeybox = rotatedKeybox(),
            newKeyset = w3UpgradeKeyset()
          )
        ),
        Ok(
          MigrationProgress.HardwareDescriptorProvisioning(
            type = MigrationType.W3Upgrade,
            currentKeybox = rotatedKeybox(),
            newKeyset = w3UpgradeKeyset(),
            signedKeysResponse = SignedKeysetVerificationResponseMock
          )
        ),
        Ok(
          MigrationProgress.CloudBackup(
            type = MigrationType.W3Upgrade,
            currentKeybox = rotatedKeybox(),
            newKeyset = w3UpgradeKeyset()
          )
        )
      )
    )

    val stateMachine = createStateMachine()
    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel> {
        onContinue()
      }
      val popProps = awaitUntilBodyMock<ProofOfPossessionNfcProps>(
        id = proofOfPossessionNfcStateMachine.id
      ) {}
      (popProps.request as build.wallet.statemachine.auth.Request.HwKeyProof)
        .onSuccess(HwFactorProofOfPossession("w1-proof"))

      awaitUntilBody<W3UpgradeNewHardwareAuthRotationInstructionsBodyModel> {
        onContinue()
      }
      val confirmProps =
        awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeRotateAppAuthKeysResult>>(
          id = nfcConfirmableSessionUiStateMachine.id
        ) {}
      confirmProps.onSuccess(rotationResult)

      val upgradeProps =
        awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeAuthorizeW3Result>>(
          id = nfcConfirmableSessionUiStateMachine.id
        ) {}
      upgradeProps.onSuccess(
        UpgradeAuthorizeW3Result(
          descriptorBackupsSignature = "descriptor-hw-sig",
          activateKeysetSignature = "activate-hw-sig",
          sealedDdkData = "sealed-ddk".encodeUtf8()
        )
      )

      // Reach ProvisioningHardwareDescriptor NFC tap
      val provisioningProps =
        awaitUntilBodyMock<NfcSessionUIStateMachineProps<*>>(id = nfcSessionUIStateMachine.id) {}

      // Simulate pairing interceptor throwing UnpairedHardwareError (W1 tapped, serial mismatch)
      val handled = provisioningProps.onError(NfcException.UnpairedHardwareError())
      handled.shouldBe(true)

      // Should show "Wrong Bitkey tapped" (not "Bitkey not recognized")
      awaitUntilBody<FormBodyModel> {
        header?.headline.shouldBe("Wrong Bitkey tapped")
        header?.sublineModel?.string.shouldNotBeNull().shouldContain("Please tap your new Bitkey device to continue.")
        primaryButton.shouldNotBeNull().text.shouldBe("Retry")
        primaryButton.shouldNotBeNull().onClick()
      }

      // Should return to ProvisioningHardwareDescriptor NFC tap
      awaitUntilBodyMock<NfcSessionUIStateMachineProps<*>>(id = nfcSessionUIStateMachine.id) {}
    }
  }

  test("non-WrongHardwareType errors fall through to default handling") {
    migrationService.resumeResult = Ok(authKeyRotationProgress)

    val stateMachine = createStateMachine()
    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel> {
        onContinue()
      }

      // Trigger a non-WrongHardwareType error
      val popProps = awaitUntilBodyMock<ProofOfPossessionNfcProps>(
        id = proofOfPossessionNfcStateMachine.id
      ) {}
      val handled = popProps.onError(NfcException.CommandError("generic error"))
      // Should NOT be handled — let the NFC session show its default error screen
      handled.shouldBe(false)
    }
  }
})

private val noopEventTracker = object : EventTracker {
  override fun track(action: Action, context: EventTrackerContext?) {}
  override fun track(eventTrackerCountInfo: EventTrackerCountInfo) {}
  override fun track(eventTrackerScreenInfo: EventTrackerScreenInfo) {}
  override fun track(eventTrackerFingerprintScanStatsInfo: EventTrackerFingerprintScanStatsInfo) {}
}
