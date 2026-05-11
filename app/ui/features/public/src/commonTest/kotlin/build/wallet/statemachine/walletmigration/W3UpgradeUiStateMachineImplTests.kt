package build.wallet.statemachine.walletmigration

import app.cash.turbine.Turbine
import bitkey.account.HardwareType
import bitkey.auth.AuthTokenScope.Global
import bitkey.data.PrivateData
import build.wallet.account.AccountServiceFake
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.EventTrackerContext
import build.wallet.analytics.events.screen.EventTrackerCountInfo
import build.wallet.analytics.events.screen.EventTrackerFingerprintScanStatsInfo
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.WalletMigrationEventTrackerScreenId
import build.wallet.analytics.v1.Action
import build.wallet.auth.AccountAuthTokensMock
import build.wallet.auth.AuthTokensServiceFake
import build.wallet.bdk.bindings.BdkOutPoint
import build.wallet.bdk.bindings.BdkScriptMock
import build.wallet.bdk.bindings.BdkTxOut
import build.wallet.bdk.bindings.BdkUtxo
import build.wallet.bitcoin.balance.BitcoinBalanceFake
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.transactions.TransactionsData
import build.wallet.bitcoin.transactions.TransactionsDataMock
import build.wallet.bitcoin.utxo.UtxoConsolidationContext
import build.wallet.bitcoin.utxo.Utxos
import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.auth.AppAuthPublicKeysMock
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock2
import build.wallet.bitkey.auth.HwAuthSecp256k1PublicKeyMock
import build.wallet.bitkey.f8e.F8eSpendingKeysetMock
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.keybox.AppKeyBundleMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.HwKeyBundleMock
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.keybox.withNewSpendingKeyset
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.chaincode.delegation.ChaincodeExtractorFake
import build.wallet.cloud.backup.csek.SealedCsekFake
import build.wallet.cloud.backup.csek.SealedSsekFake
import build.wallet.cloud.backup.csek.SsekDaoFake
import build.wallet.cloud.backup.csek.SsekFake
import build.wallet.cloud.backup.health.AppKeyBackupStatus
import build.wallet.cloud.backup.health.CloudBackupHealthRepositoryMock
import build.wallet.cloud.backup.health.CloudBackupStatus
import build.wallet.cloud.backup.health.EekBackupStatus
import build.wallet.coroutines.turbine.turbines
import build.wallet.encrypt.WsmVerifierMock
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.f8e.recovery.SignedKeysetVerificationResponseMock
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.UtxoMaxConsolidationCountFeatureFlag
import build.wallet.firmware.FirmwareDeviceInfoDaoFake
import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.keybox.keys.AppKeysGeneratorMock
import build.wallet.money.FiatMoney
import build.wallet.money.currency.USD
import build.wallet.nfc.NfcSession
import build.wallet.nfc.NfcSessionFake
import build.wallet.nfc.W3NfcCommandsMock
import build.wallet.nfc.platform.RotateAppAuthKeysCompositeResult
import build.wallet.nfc.platform.UpgradeAuthorizeW3Result
import build.wallet.nfc.platform.UpgradeRotateAppAuthKeysResult
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintEnrolled
import build.wallet.relationships.RelationshipsCryptoError
import build.wallet.relationships.RelationshipsCryptoFake
import build.wallet.relationships.RelationshipsKeysDaoFake
import build.wallet.relationships.RelationshipsKeysRepository
import build.wallet.statemachine.BodyModelMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareProps
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachine
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupProps
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupUiStateMachine
import build.wallet.statemachine.cloud.health.RepairAppKeyBackupProps
import build.wallet.statemachine.cloud.health.RepairCloudBackupStateMachine
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcConfirmableSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcConfirmableSessionUiStateMachineMock
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.Required
import build.wallet.statemachine.recovery.sweep.SweepUiProps
import build.wallet.statemachine.recovery.sweep.SweepUiStateMachine
import build.wallet.statemachine.ui.*
import build.wallet.statemachine.utxo.UtxoConsolidationProps
import build.wallet.statemachine.utxo.UtxoConsolidationUiStateMachine
import build.wallet.ui.model.alert.ButtonAlertModel
import build.wallet.wallet.migration.MigrationError
import build.wallet.wallet.migration.MigrationProgress
import build.wallet.wallet.migration.MigrationServiceFake
import build.wallet.wallet.migration.MigrationType
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.collections.immutable.persistentListOf
import okio.ByteString.Companion.encodeUtf8

@OptIn(PrivateData::class)
class W3UpgradeUiStateMachineImplTests : FunSpec({
  val pairNewHardwareUiStateMachine =
    object : PairNewHardwareUiStateMachine,
      ScreenStateMachineMock<PairNewHardwareProps>("pair-new-hardware") {}

  val proofOfPossessionNfcStateMachine =
    object : ProofOfPossessionNfcStateMachine,
      ScreenStateMachineMock<ProofOfPossessionNfcProps>("proof-of-possession") {}

  val sweepUiStateMachine =
    object : SweepUiStateMachine,
      ScreenStateMachineMock<SweepUiProps>("sweep") {}

  val fullAccountCloudSignInAndBackupUiStateMachine =
    object : FullAccountCloudSignInAndBackupUiStateMachine,
      ScreenStateMachineMock<FullAccountCloudSignInAndBackupProps>("cloud-backup") {}

  val migrationService = MigrationServiceFake()
  val ssekDao = SsekDaoFake()
  val accountService = AccountServiceFake()
  val authTokensService = AuthTokensServiceFake()
  val firmwareDeviceInfoDao = FirmwareDeviceInfoDaoFake()
  val nfcSessionUIStateMachine =
    object : NfcSessionUIStateMachine,
      ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>("nfc-session") {}
  val nfcConfirmableSessionUiStateMachine =
    NfcConfirmableSessionUiStateMachineMock("nfc-confirmable")
  val actionProofService = bitkey.privilegedactions.ActionProofServiceFake()
  val relationshipsKeysDao = RelationshipsKeysDaoFake()
  val relationshipsCrypto = RelationshipsCryptoFake()
  val relationshipsKeysRepository =
    RelationshipsKeysRepository(relationshipsCrypto, relationshipsKeysDao)
  val appKeysGenerator = AppKeysGeneratorMock()
  val chaincodeExtractor = ChaincodeExtractorFake()
  val bitcoinWalletService = BitcoinWalletServiceFake()
  val utxoConsolidationUiStateMachine =
    object : UtxoConsolidationUiStateMachine,
      ScreenStateMachineMock<UtxoConsolidationProps>("utxo-consolidation") {}
  val utxoMaxConsolidationCountFeatureFlag = UtxoMaxConsolidationCountFeatureFlag(
    featureFlagDao = FeatureFlagDaoFake()
  )
  val cloudBackupHealthRepository = CloudBackupHealthRepositoryMock(turbines::create)

  fun createStateMachine(eventTracker: EventTracker = noopEventTracker) =
    W3UpgradeUiStateMachineImpl(
      pairNewHardwareUiStateMachine = pairNewHardwareUiStateMachine,
      proofOfPossessionNfcStateMachine = proofOfPossessionNfcStateMachine,
      sweepUiStateMachine = sweepUiStateMachine,
      migrationService = migrationService,
      fullAccountCloudSignInAndBackupUiStateMachine = fullAccountCloudSignInAndBackupUiStateMachine,
      ssekDao = ssekDao,
      accountService = accountService,
      authTokensService = authTokensService,
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
      cloudBackupHealthRepository = cloudBackupHealthRepository,
      repairCloudBackupStateMachine = object : RepairCloudBackupStateMachine,
        ScreenStateMachineMock<RepairAppKeyBackupProps>(
          "repair-cloud-backup"
        ) {},
      eventTracker = eventTracker
    )

  val stateMachine = createStateMachine()

  val onUpgradeCompleteCalls = turbines.create<Unit>("on upgrade complete calls")
  val onExitCalls = turbines.create<Unit>("on exit calls")

  val props = W3UpgradeUiProps(
    account = FullAccountMock,
    onUpgradeComplete = { onUpgradeCompleteCalls.add(Unit) },
    onExit = { onExitCalls.add(Unit) }
  )

  val rotationResult = UpgradeRotateAppAuthKeysResult(
    hwSignedAccountId = "signed-account-id",
    appGlobalAuthKeyHwSignature = "hw-app-auth-signature",
    hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock
  )

  val fingerprintEnrolled = FingerprintEnrolled(
    appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
    keyBundle = HwKeyBundleMock,
    sealedCsek = SealedCsekFake,
    sealedSsek = SealedSsekFake,
    serial = "new-device-serial",
    hardwareType = HardwareType.W3
  )

  fun w3UpgradeKeyset(): SpendingKeyset =
    SpendingKeysetMock.copy(
      localId = "uuid-0",
      appKey = AppKeyBundleMock.spendingKey,
      hardwareKey = HwKeyBundleMock.spendingKey,
      f8eSpendingKeyset = F8eSpendingKeysetMock
    )

  fun rotatedW3AuthKeys(): AppAuthPublicKeys =
    AppAuthPublicKeysMock.copy(
      appGlobalAuthPublicKey = FullAccountMock.keybox.activeAppKeyBundle.authKey,
      appRecoveryAuthPublicKey = AppRecoveryAuthPublicKeyMock2,
      appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature(
        rotationResult.appGlobalAuthKeyHwSignature
      )
    )

  fun rotatedKeyboxForAuthKeys(appAuthKeys: AppAuthPublicKeys): Keybox =
    FullAccountMock.keybox
      .withNewSpendingKeyset(w3UpgradeKeyset())
      .copy(
        config = FullAccountMock.keybox.config.copy(hardwareType = HardwareType.W3),
        activeAppKeyBundle = FullAccountMock.keybox.activeAppKeyBundle.copy(
          authKey = appAuthKeys.appGlobalAuthPublicKey,
          recoveryAuthKey = appAuthKeys.appRecoveryAuthPublicKey
        ),
        appGlobalAuthKeyHwSignature = appAuthKeys.appGlobalAuthKeyHwSignature
      )

  fun rotatedKeybox(): Keybox = rotatedKeyboxForAuthKeys(rotatedW3AuthKeys())

  fun provisionedKeybox(): Keybox =
    rotatedKeybox().copy(
      appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock
    )

  fun resumedAuthRotationProgress(): MigrationProgress.AuthKeyRotation =
    MigrationProgress.AuthKeyRotation(
      type = MigrationType.W3Upgrade,
      currentKeybox = FullAccountMock.keybox,
      newKeyset = w3UpgradeKeyset(),
      newAppAuthKeys = rotatedW3AuthKeys(),
      hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock,
      hwSignedAccountId = "signed-account-id"
    )

  fun resumedFromCloudBackupAuthRotationProgress(): MigrationProgress.AuthKeyRotation =
    MigrationProgress.AuthKeyRotation(
      type = MigrationType.W3Upgrade,
      currentKeybox = FullAccountMock.keybox,
      newKeyset = w3UpgradeKeyset(),
      resumedFromCloudBackup = true,
      newAppAuthKeys = rotatedW3AuthKeys()
    )

  fun generateConfirmedUtxos(count: Int): Set<BdkUtxo> {
    return (1..count).map { index ->
      BdkUtxo(
        outPoint = BdkOutPoint(
          txid = "utxo-txid-$index",
          vout = index.toUInt()
        ),
        txOut = BdkTxOut(
          value = 10000u,
          scriptPubkey = BdkScriptMock()
        ),
        isSpent = false
      )
    }.toSet()
  }

  fun setTransactionData(
    confirmed: Set<BdkUtxo>,
    unconfirmed: Set<BdkUtxo>,
  ) {
    bitcoinWalletService.transactionsData.value = TransactionsData(
      balance = BitcoinBalanceFake(),
      fiatBalance = FiatMoney.zero(USD),
      transactions = persistentListOf(),
      utxos = Utxos(confirmed = confirmed, unconfirmed = unconfirmed)
    )
  }

  beforeTest {
    migrationService.reset()
    ssekDao.reset()
    accountService.reset()
    authTokensService.reset()
    authTokensService.setTokens(
      accountId = FullAccountMock.accountId,
      tokens = AccountAuthTokensMock,
      scope = Global
    ).shouldBe(Ok(Unit))
    firmwareDeviceInfoDao.reset()
    actionProofService.reset()
    relationshipsKeysDao.clear()
    relationshipsCrypto.generateAsymmetricKeyResult = null
    appKeysGenerator.reset()
    appKeysGenerator.recoveryAuthKeyResult = Ok(AppRecoveryAuthPublicKeyMock2)
    chaincodeExtractor.reset()
    bitcoinWalletService.reset()
    // Default to no unconfirmed UTXOs so existing tests pass through pending tx check
    bitcoinWalletService.transactionsData.value = TransactionsDataMock
    utxoMaxConsolidationCountFeatureFlag.setFlagValue(FeatureFlagValue.DoubleFlag(150.0))
    cloudBackupHealthRepository.reset()
  }

  test("unhealthy cloud backup blocks W3 upgrade and back dismisses sheet") {
    migrationService.resumeResult = Ok(MigrationProgress.NotStarted(MigrationType.W3Upgrade))
    cloudBackupHealthRepository.syncResult = CloudBackupStatus(
      appKeyBackupStatus = AppKeyBackupStatus.ProblemWithBackup.NoCloudAccess,
      eekBackupStatus = EekBackupStatus.ProblemWithBackup.NoCloudAccess
    )

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeIntroBodyModel> {
        onContinue()
      }
      awaitUntilSheet<W3UpgradeCloudBackupUnhealthyWarningSheetModel> {
        cloudBackupHealthRepository.performSyncCalls.awaitItem()
        onBack()
      }
      awaitUntilBody<W3UpgradeIntroBodyModel>()
    }
  }

  test("unhealthy cloud backup blocks resumed-from-cloud-backup upgrade on Continue") {
    migrationService.resumeResult = Ok(
      MigrationProgress.NotStarted(
        type = MigrationType.W3Upgrade,
        resumedFromCloudBackup = true
      )
    )
    // Continue runs the backup health sync and blocks when unhealthy.
    cloudBackupHealthRepository.syncResult = CloudBackupStatus(
      appKeyBackupStatus = AppKeyBackupStatus.ProblemWithBackup.NoCloudAccess,
      eekBackupStatus = EekBackupStatus.ProblemWithBackup.NoCloudAccess
    )
    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeIntroBodyModel> {
        onContinue()
      }
      awaitUntilSheet<W3UpgradeCloudBackupUnhealthyWarningSheetModel> {
        cloudBackupHealthRepository.performSyncCalls.awaitItem() // onContinue
      }
    }
  }

  test("unhealthy cloud backup - Repair launches repair state machine") {
    migrationService.resumeResult = Ok(MigrationProgress.NotStarted(MigrationType.W3Upgrade))
    cloudBackupHealthRepository.syncResult = CloudBackupStatus(
      appKeyBackupStatus = AppKeyBackupStatus.ProblemWithBackup.NoCloudAccess,
      eekBackupStatus = EekBackupStatus.ProblemWithBackup.NoCloudAccess
    )

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeIntroBodyModel> {
        onContinue()
      }
      awaitUntilSheet<W3UpgradeCloudBackupUnhealthyWarningSheetModel> {
        cloudBackupHealthRepository.performSyncCalls.awaitItem()
        onRepair()
      }
      awaitUntilBody<BodyModelMock<*>> {
        id.shouldBe("repair-cloud-backup")
      }
    }
  }

  test("resume auth rotation uses W3 tap result to continue into composite upgrade authorization") {
    val authRotationProgress = MigrationProgress.AuthKeyRotation(
      type = MigrationType.W3Upgrade,
      currentKeybox = FullAccountMock.keybox,
      newKeyset = w3UpgradeKeyset()
    )
    val descriptorBackupProgress = MigrationProgress.DescriptorBackup(
      type = MigrationType.W3Upgrade,
      currentKeybox = rotatedKeybox(),
      newKeyset = w3UpgradeKeyset()
    )
    migrationService.resumeResult = Ok(authRotationProgress)
    migrationService.proceedResult = Ok(descriptorBackupProgress)

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel> {
        onContinue()
      }
      awaitUntilBodyMock<ProofOfPossessionNfcProps>(id = "proof-of-possession") {
        (request as Request.HwKeyProof)
          .onSuccess(HwFactorProofOfPossession("w1-proof"))
      }
      awaitUntilBody<W3UpgradeNewHardwareAuthRotationInstructionsBodyModel> {
        designSystemV2Model?.eyebrow.shouldBe("Step 3 of 4")
        onContinue()
      }
      awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeRotateAppAuthKeysResult>>(id = "nfc-confirmable") {
        onSuccess(rotationResult)
      }
      // After auth rotation, goes to PreparingUpgradeAuthorization → AuthorizingW3Upgrade (confirmable NFC)
      awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeAuthorizeW3Result>>(id = "nfc-confirmable") {
      }
    }

    val proceededState =
      migrationService.proceedCalls.single().shouldBeInstanceOf<MigrationProgress.AuthKeyRotation>()
    proceededState.hwAuthPublicKey.shouldBe(HwAuthSecp256k1PublicKeyMock)
    proceededState.hwSignedAccountId.shouldBe("signed-account-id")
    proceededState.proof.shouldBe(PrivilegedActionProof.HwKeyProof(HwFactorProofOfPossession("w1-proof")))
  }

  test("resume auth rotation with persisted rotation data retries proceed before asking for W1 proof") {
    val descriptorBackupProgress = MigrationProgress.DescriptorBackup(
      type = MigrationType.W3Upgrade,
      currentKeybox = rotatedKeybox(),
      newKeyset = w3UpgradeKeyset()
    )
    migrationService.resumeResult = Ok(resumedAuthRotationProgress())
    migrationService.proceedResult = Ok(descriptorBackupProgress)

    stateMachine.test(props) {
      awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeAuthorizeW3Result>>(
        id = "nfc-confirmable"
      ) {}
    }

    migrationService.resumeCalls.shouldBe(listOf(MigrationType.W3Upgrade))
    migrationService.proceedCalls.single().shouldBe(resumedAuthRotationProgress())
  }

  test("auth rotation confirmable tap persists W3 FirmwareDeviceInfo") {
    val w3DeviceInfo = FirmwareDeviceInfoMock.copy(hwRevision = "w3a-evt", serial = "w3-serial")
    // Raw turbines avoid auto-validation of getDeviceInfo calls triggered by verifyHardwareType
    val nfcCommandsMock = W3NfcCommandsMock { Turbine(name = it) }
    nfcCommandsMock.deviceInfoResult = w3DeviceInfo

    val authRotationProgress = MigrationProgress.AuthKeyRotation(
      type = MigrationType.W3Upgrade,
      currentKeybox = FullAccountMock.keybox,
      newKeyset = w3UpgradeKeyset()
    )
    val descriptorBackupProgress = MigrationProgress.DescriptorBackup(
      type = MigrationType.W3Upgrade,
      currentKeybox = rotatedKeybox(),
      newKeyset = w3UpgradeKeyset()
    )
    migrationService.resumeResult = Ok(authRotationProgress)
    migrationService.proceedResult = Ok(descriptorBackupProgress)

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel> {
        onContinue()
      }
      awaitUntilBodyMock<ProofOfPossessionNfcProps>(id = "proof-of-possession") {
        (request as Request.HwKeyProof)
          .onSuccess(HwFactorProofOfPossession("w1-proof"))
      }
      awaitUntilBody<W3UpgradeNewHardwareAuthRotationInstructionsBodyModel> {
        onContinue()
      }
      val confirmProps =
        awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeRotateAppAuthKeysResult>>(
          id = "nfc-confirmable"
        ) {}
      // Unlike other tests that call onSuccess directly, we must run the session
      // first so that the compose-scoped w3DeviceInfo state is populated before
      // onSuccess tries to persist it.
      confirmProps.session(w3NfcSessionFake(), nfcCommandsMock)
      confirmProps.onSuccess(rotationResult)

      awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeAuthorizeW3Result>>(
        id = "nfc-confirmable"
      ) {}
    }

    firmwareDeviceInfoDao.storedDeviceInfo.shouldBe(w3DeviceInfo)
  }

  test("resume descriptor backup goes to composite upgrade authorization tap") {
    val descriptorBackupProgress = MigrationProgress.DescriptorBackup(
      type = MigrationType.W3Upgrade,
      currentKeybox = rotatedKeybox(),
      newKeyset = w3UpgradeKeyset()
    )
    migrationService.resumeResult = Ok(descriptorBackupProgress)

    stateMachine.test(props) {
      // DescriptorBackup resumes to PreparingUpgradeAuthorization → AuthorizingW3Upgrade
      awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeAuthorizeW3Result>>(
        id = "nfc-confirmable"
      ) {}
    }

    authTokensService.refreshAccessTokenCalls.shouldContain(FullAccountMock.accountId to Global)
  }

  test("resume server keyset activation rewinds to composite upgrade authorization tap") {
    migrationService.resumeResult = Ok(
      MigrationProgress.ServerKeysetActivation(
        type = MigrationType.W3Upgrade,
        currentKeybox = rotatedKeybox(),
        newKeyset = w3UpgradeKeyset()
      )
    )

    stateMachine.test(props) {
      // ServerKeysetActivation resume rewinds to PreparingUpgradeAuthorization → AuthorizingW3Upgrade
      awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeAuthorizeW3Result>>(
        id = "nfc-confirmable"
      ) {}
    }
  }

  test("full happy path runs from intro through completion") {
    migrationService.resumeResult = Ok(MigrationProgress.NotStarted(MigrationType.W3Upgrade))
    migrationService.proceedResults.addAll(
      listOf(
        // 1. proceed(NotStarted) → CreateNewKeyset → AuthKeyRotation
        Ok(
          MigrationProgress.AuthKeyRotation(
            type = MigrationType.W3Upgrade,
            currentKeybox = FullAccountMock.keybox,
            newKeyset = w3UpgradeKeyset()
          )
        ),
        // 2. proceed(AuthKeyRotation) → DescriptorBackup
        Ok(
          MigrationProgress.DescriptorBackup(
            type = MigrationType.W3Upgrade,
            currentKeybox = rotatedKeybox(),
            newKeyset = w3UpgradeKeyset()
          )
        ),
        // 3. proceed(DescriptorBackup with proof) → ServerKeysetActivation (batched in RunningServerKeysetActivation)
        Ok(
          MigrationProgress.ServerKeysetActivation(
            type = MigrationType.W3Upgrade,
            currentKeybox = rotatedKeybox(),
            newKeyset = w3UpgradeKeyset()
          )
        ),
        // 4. proceed(ServerKeysetActivation with proof) → HardwareDescriptorProvisioning
        Ok(
          MigrationProgress.HardwareDescriptorProvisioning(
            type = MigrationType.W3Upgrade,
            currentKeybox = rotatedKeybox(),
            newKeyset = w3UpgradeKeyset(),
            signedKeysResponse = SignedKeysetVerificationResponseMock
          )
        ),
        // 5. proceed(DdkBackup with sealed data) → CloudBackup (DDK upload, batched)
        Ok(
          MigrationProgress.CloudBackup(
            type = MigrationType.W3Upgrade,
            currentKeybox = provisionedKeybox(),
            newKeyset = w3UpgradeKeyset(),
            sealedCsek = SealedCsekFake
          )
        ),
        // 6. proceed(HardwareDescriptorProvisioning with signature) → DdkBackup
        Ok(
          MigrationProgress.DdkBackup(
            type = MigrationType.W3Upgrade,
            currentKeybox = provisionedKeybox(),
            newKeyset = w3UpgradeKeyset(),
            sealedCsek = SealedCsekFake
          )
        ),
        // 7. proceed(CloudBackup) → LocalKeyboxActivation
        Ok(
          MigrationProgress.LocalKeyboxActivation(
            type = MigrationType.W3Upgrade,
            currentKeybox = provisionedKeybox(),
            newKeyset = w3UpgradeKeyset()
          )
        ),
        // 8. proceed(LocalKeyboxActivation) → Completed
        Ok(MigrationProgress.Completed(MigrationType.W3Upgrade))
      )
    )
    accountService.setActiveAccount(FullAccountMock)
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock.copy(serial = "old-device-serial"))
    ssekDao.set(SealedSsekFake, SsekFake)

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeIntroBodyModel> {
        onContinue()
      }
      cloudBackupHealthRepository.performSyncCalls.awaitItem() // onContinue
      awaitUntilBody<W3UpgradeDeviceReadyBodyModel> {
        onYes()
      }

      val pairProps = awaitUntilBodyMock<PairNewHardwareProps>(id = "pair-new-hardware") {}
      pairProps.request.shouldBeTypeOf<PairNewHardwareProps.Request.Ready>()
        .onSuccess(fingerprintEnrolled)

      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel> {
        onContinue()
      }
      val popProps = awaitUntilBodyMock<ProofOfPossessionNfcProps>(id = "proof-of-possession") {}
      popProps.request.shouldBeTypeOf<Request.HwKeyProof>()
        .onSuccess(HwFactorProofOfPossession("w1-proof"))

      awaitUntilBody<W3UpgradeNewHardwareAuthRotationInstructionsBodyModel> {
        onContinue()
      }
      val confirmProps =
        awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeRotateAppAuthKeysResult>>(
          id = "nfc-confirmable"
        ) {}
      confirmProps.onSuccess(rotationResult)

      // Composite upgrade authorization: signs both proofs + seals DDK in one confirmable tap
      val upgradeProps =
        awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeAuthorizeW3Result>>(
          id = "nfc-confirmable"
        ) {}
      upgradeProps.onSuccess(
        UpgradeAuthorizeW3Result(
          descriptorBackupsSignature = "descriptor-hw-sig",
          activateKeysetSignature = "activate-hw-sig",
          sealedDdkData = "sealed-ddk".encodeUtf8()
        )
      )

      // Provisioning hardware descriptor
      val provisioningProps =
        awaitUntilBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {
          config.eventTrackerContext.shouldBe(
            NfcEventTrackerScreenIdContext.VERIFY_KEYS_AND_BUILD_HARDWARE_DESCRIPTOR
          )
          config.hardwareTypeOverride.shouldBe(HardwareType.W3)
          config.hardwareVerification.shouldBe(Required())
        }
      @Suppress("UNCHECKED_CAST")
      (provisioningProps as NfcSessionUIStateMachineProps<Any>).onSuccess(
        Ok(
          AppGlobalAuthKeyHwSignatureMock
        )
      )

      val cloudBackupProps =
        awaitUntilBodyMock<FullAccountCloudSignInAndBackupProps>(id = "cloud-backup") {
          // Should use the CSEK sealed by W3 during pairing, not the old W1 key from the prior backup.
          sealedCsek.shouldBe(SealedCsekFake)
          keybox.activeSpendingKeyset.localId.shouldBe("uuid-0")
          keybox.activeAppKeyBundle.authKey.shouldBe(AppAuthPublicKeysMock.appGlobalAuthPublicKey)
          keybox.appGlobalAuthKeyHwSignature.shouldBe(AppGlobalAuthKeyHwSignatureMock)
        }
      cloudBackupProps.onBackupSaved()

      awaitUntilBody<W3UpgradeOldHardwareInstructionsBodyModel> {
        onContinue()
      }
      val sweepProps = awaitUntilBodyMock<SweepUiProps>(id = "sweep") {
        val w3Context =
          sweepContext.shouldBeInstanceOf<build.wallet.recovery.sweep.SweepContext.W3Upgrade>()
        w3Context.replacedHardwareFingerprint.shouldBe("e5ff120e")
      }
      sweepProps.onSuccess()
      onUpgradeCompleteCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }

    val createKeysetCall = migrationService.proceedCalls.first()
      .shouldBeInstanceOf<MigrationProgress.CreateNewKeyset.W3Upgrade>()
    createKeysetCall.oldDeviceSerial.shouldBe("old-device-serial")
    createKeysetCall.oldHardwareFingerprint.shouldBe("e5ff120e")
    createKeysetCall.newDeviceSerial.shouldBe("new-device-serial")
    migrationService.proceedCalls.map { it::class.simpleName }.shouldBe(
      listOf(
        "W3Upgrade",
        "AuthKeyRotation",
        "DescriptorBackup", // batched in RunningServerKeysetActivation
        "ServerKeysetActivation", // batched in RunningServerKeysetActivation
        "DdkBackup", // batched in RunningServerKeysetActivation
        "HardwareDescriptorProvisioning",
        "CloudBackup",
        "LocalKeyboxActivation"
      )
    )
  }

  fun setUnconfirmedUtxos() {
    bitcoinWalletService.transactionsData.value = TransactionsDataMock.copy(
      utxos = Utxos(
        confirmed = emptySet(),
        unconfirmed = setOf(
          BdkUtxo(
            outPoint = BdkOutPoint("unconfirmed-txid", 0u),
            txOut = BdkTxOut(value = 10_000u, scriptPubkey = BdkScriptMock()),
            isSpent = false
          )
        )
      )
    )
  }

  test("pending transactions warning blocks W3 upgrade and Got It returns to intro") {
    migrationService.resumeResult = Ok(MigrationProgress.NotStarted(MigrationType.W3Upgrade))
    setUnconfirmedUtxos()

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeIntroBodyModel> {
        onContinue()
      }
      cloudBackupHealthRepository.performSyncCalls.awaitItem() // onContinue
      awaitUntilScreenWithBody<W3UpgradeIntroBodyModel>(
        matchingScreen = { it.bottomSheetModel != null }
      ) {
        bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<W3UpgradePendingTransactionsWarningSheetModel>()
          .onGotIt()
      }
      awaitUntilScreenWithBody<W3UpgradeIntroBodyModel>(
        matchingScreen = { it.bottomSheetModel == null }
      )
    }
  }

  test("pending transactions warning - Back returns to intro") {
    migrationService.resumeResult = Ok(MigrationProgress.NotStarted(MigrationType.W3Upgrade))
    setUnconfirmedUtxos()

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeIntroBodyModel> {
        onContinue()
      }
      cloudBackupHealthRepository.performSyncCalls.awaitItem() // onContinue
      awaitUntilScreenWithBody<W3UpgradeIntroBodyModel>(
        matchingScreen = { it.bottomSheetModel != null }
      ) {
        bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<W3UpgradePendingTransactionsWarningSheetModel>()
          .onBack()
      }
      awaitUntilScreenWithBody<W3UpgradeIntroBodyModel>(
        matchingScreen = { it.bottomSheetModel == null }
      )
    }
  }

  test("no pending transactions proceeds to device ready screen") {
    migrationService.resumeResult = Ok(MigrationProgress.NotStarted(MigrationType.W3Upgrade))
    // TransactionsDataMock has unconfirmed = emptySet() by default

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeIntroBodyModel> {
        onContinue()
      }
      cloudBackupHealthRepository.performSyncCalls.awaitItem() // onContinue
      awaitUntilBody<W3UpgradeDeviceReadyBodyModel> {
        designSystemV2Model?.eyebrow.shouldBe("Step 1 of 4")
      }
    }
  }

  test("device ready does not precompute whether sweep is needed") {
    migrationService.resumeResult = Ok(MigrationProgress.NotStarted(MigrationType.W3Upgrade))
    migrationService.estimateMigrationFeesResults += Err(MigrationError.InsufficientFundsForMigration)

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeIntroBodyModel> {
        onContinue()
      }
      cloudBackupHealthRepository.performSyncCalls.awaitItem() // onContinue
      awaitUntilBody<W3UpgradeDeviceReadyBodyModel> {
        designSystemV2Model?.eyebrow.shouldBe("Step 1 of 4")
      }
    }

    migrationService.estimateMigrationFeesResults.size.shouldBe(1)
  }

  test("resume auth rotation does not precompute whether sweep is needed") {
    val authRotationProgress = MigrationProgress.AuthKeyRotation(
      type = MigrationType.W3Upgrade,
      currentKeybox = FullAccountMock.keybox,
      newKeyset = w3UpgradeKeyset()
    )
    migrationService.resumeResult = Ok(authRotationProgress)
    migrationService.estimateMigrationFeesResults += Err(MigrationError.InsufficientFundsForMigration)

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel> {
        designSystemV2Model?.eyebrow.shouldBe("Step 2 of 4")
      }
    }

    migrationService.estimateMigrationFeesResults.size.shouldBe(1)
  }

  test("UTXO consolidation required - shows sheet and continue navigates to consolidation") {
    migrationService.resumeResult = Ok(MigrationProgress.NotStarted(MigrationType.W3Upgrade))
    setTransactionData(confirmed = generateConfirmedUtxos(200), unconfirmed = emptySet())

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeIntroBodyModel> {
        onContinue()
      }
      cloudBackupHealthRepository.performSyncCalls.awaitItem() // onContinue
      awaitUntilScreenWithBody<W3UpgradeIntroBodyModel>(
        matchingScreen = { it.bottomSheetModel != null }
      ) {
        bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<W3UpgradeUtxoConsolidationRequiredSheetModel>()
          .onContinue()
      }
      awaitBodyMock<UtxoConsolidationProps>(id = "utxo-consolidation") {
        context.shouldBe(UtxoConsolidationContext.W3Upgrade)
        onConsolidationSuccess()
      }
      awaitUntilBody<W3UpgradeIntroBodyModel>()
    }
  }

  test("UTXO consolidation required - back on sheet returns to intro") {
    migrationService.resumeResult = Ok(MigrationProgress.NotStarted(MigrationType.W3Upgrade))
    setTransactionData(confirmed = generateConfirmedUtxos(200), unconfirmed = emptySet())

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeIntroBodyModel> {
        onContinue()
      }
      cloudBackupHealthRepository.performSyncCalls.awaitItem() // onContinue
      awaitUntilScreenWithBody<W3UpgradeIntroBodyModel>(
        matchingScreen = { it.bottomSheetModel != null }
      ) {
        bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<W3UpgradeUtxoConsolidationRequiredSheetModel>()
          .onBack()
      }
      awaitUntilScreenWithBody<W3UpgradeIntroBodyModel>(
        matchingScreen = { it.bottomSheetModel == null }
      )
    }
  }

  test("UTXO consolidation - back from consolidation flow returns to intro") {
    migrationService.resumeResult = Ok(MigrationProgress.NotStarted(MigrationType.W3Upgrade))
    setTransactionData(confirmed = generateConfirmedUtxos(200), unconfirmed = emptySet())

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeIntroBodyModel> {
        onContinue()
      }
      cloudBackupHealthRepository.performSyncCalls.awaitItem() // onContinue
      awaitUntilScreenWithBody<W3UpgradeIntroBodyModel>(
        matchingScreen = { it.bottomSheetModel != null }
      ) {
        bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<W3UpgradeUtxoConsolidationRequiredSheetModel>()
          .onContinue()
      }
      awaitBodyMock<UtxoConsolidationProps>(id = "utxo-consolidation") {
        onBack()
      }
      awaitUntilBody<W3UpgradeIntroBodyModel>()
    }
  }

  test("pending transactions warning takes priority over UTXO consolidation") {
    migrationService.resumeResult = Ok(MigrationProgress.NotStarted(MigrationType.W3Upgrade))
    setTransactionData(
      confirmed = generateConfirmedUtxos(200),
      unconfirmed = setOf(
        BdkUtxo(
          outPoint = BdkOutPoint("unconfirmed-txid", 0u),
          txOut = BdkTxOut(value = 10_000u, scriptPubkey = BdkScriptMock()),
          isSpent = false
        )
      )
    )

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeIntroBodyModel> {
        onContinue()
      }
      cloudBackupHealthRepository.performSyncCalls.awaitItem() // onContinue
      awaitUntilScreenWithBody<W3UpgradeIntroBodyModel>(
        matchingScreen = { it.bottomSheetModel != null }
      ) {
        bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<W3UpgradePendingTransactionsWarningSheetModel>()
      }
    }
  }

  test("UTXO consolidation skipped when feature flag is disabled") {
    migrationService.resumeResult = Ok(MigrationProgress.NotStarted(MigrationType.W3Upgrade))
    utxoMaxConsolidationCountFeatureFlag.setFlagValue(FeatureFlagValue.DoubleFlag(-1.0))
    setTransactionData(confirmed = generateConfirmedUtxos(200), unconfirmed = emptySet())

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeIntroBodyModel> {
        onContinue()
      }
      cloudBackupHealthRepository.performSyncCalls.awaitItem() // onContinue
      awaitUntilBody<W3UpgradeDeviceReadyBodyModel>()
    }
  }

  test("UTXO consolidation skipped when under threshold") {
    migrationService.resumeResult = Ok(MigrationProgress.NotStarted(MigrationType.W3Upgrade))
    setTransactionData(confirmed = generateConfirmedUtxos(100), unconfirmed = emptySet())

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeIntroBodyModel> {
        onContinue()
      }
      cloudBackupHealthRepository.performSyncCalls.awaitItem() // onContinue
      awaitUntilBody<W3UpgradeDeviceReadyBodyModel>()
    }
  }

  test("resume at sweep uses persisted fingerprint not keybox fingerprint") {
    // Simulate resuming after keybox was already updated to W3 — the keybox's
    // hardware fingerprint is now the new W3 device, but the sweep must use the
    // old W1 fingerprint that was persisted before pairing.
    val w3Keyset = w3UpgradeKeyset()
    val w3Keybox = provisionedKeybox()
    migrationService.resumeResult = Ok(
      MigrationProgress.LocalKeyboxActivation(
        type = MigrationType.W3Upgrade,
        currentKeybox = w3Keybox,
        newKeyset = w3Keyset
      )
    )
    migrationService.proceedResult = Ok(MigrationProgress.Completed(MigrationType.W3Upgrade))
    // Persist a different fingerprint than what the current keybox would report,
    // simulating the old W1 device's fingerprint saved before the upgrade.
    migrationService.savedOldHardwareFingerprint = "old-w1-fingerprint"
    accountService.setActiveAccount(FullAccountMock)

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeOldHardwareInstructionsBodyModel> {
        onContinue()
      }
      val sweepProps = awaitUntilBodyMock<SweepUiProps>(id = "sweep") {
        val w3Context =
          sweepContext.shouldBeInstanceOf<build.wallet.recovery.sweep.SweepContext.W3Upgrade>()
        // Must use the persisted old W1 fingerprint, NOT the keybox's current fingerprint
        w3Context.replacedHardwareFingerprint.shouldBe("old-w1-fingerprint")
      }
      sweepProps.onSuccess()
      onUpgradeCompleteCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  // -- Intro phase tests --

  test("intro screen back button calls onExit when no migration in progress") {
    migrationService.resumeResult = Ok(MigrationProgress.NotStarted(MigrationType.W3Upgrade))

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeIntroBodyModel> {
        onBack.shouldNotBeNull().invoke()
      }
    }
    onExitCalls.awaitItem()
  }

  test("cloud-restored placeholder shows intro without a back button") {
    migrationService.resumeResult = Ok(
      MigrationProgress.NotStarted(
        type = MigrationType.W3Upgrade,
        resumedFromCloudBackup = true
      )
    )

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeIntroBodyModel> {
        onBack.shouldBeNull()
      }
    }
  }

  test("error screen hides cancel button when migration is in progress") {
    // When a migration is in-progress, resolveInitialUiState sets isMigrationInProgress = true
    // and jumps past the intro screen entirely.
    // The intro screen's onBack is guarded by `props.onExit.takeUnless { isMigrationInProgress }`,
    // but in practice the intro is never rendered while in-progress — the state machine skips it.
    // The user-visible effect of isMigrationInProgress is that the error screen's Cancel button
    // is hidden, preventing the user from exiting mid-migration. Verify that here.
    val authRotationProgress = MigrationProgress.AuthKeyRotation(
      type = MigrationType.W3Upgrade,
      currentKeybox = FullAccountMock.keybox,
      newKeyset = w3UpgradeKeyset()
    )
    migrationService.resumeResult = Ok(authRotationProgress)
    migrationService.proceedResult = Err(MigrationError.AuthKeyRotationFailed(Exception("fail")))

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel> { onContinue() }
      val popProps = awaitUntilBodyMock<ProofOfPossessionNfcProps>(id = "proof-of-possession") {}
      (popProps.request as Request.HwKeyProof)
        .onSuccess(HwFactorProofOfPossession("w1-proof"))
      awaitUntilBody<W3UpgradeNewHardwareAuthRotationInstructionsBodyModel> { onContinue() }
      val confirmProps =
        awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeRotateAppAuthKeysResult>>(
          id = "nfc-confirmable"
        ) {}
      confirmProps.onSuccess(rotationResult)

      awaitUntilBody<FormBodyModel> {
        // Cancel button should be hidden when migration is in progress
        secondaryButton.shouldBeNull()
      }
    }
  }

  test("in-progress migration skips intro and jumps to resumed state") {
    val authRotationProgress = MigrationProgress.AuthKeyRotation(
      type = MigrationType.W3Upgrade,
      currentKeybox = FullAccountMock.keybox,
      newKeyset = w3UpgradeKeyset()
    )
    migrationService.resumeResult = Ok(authRotationProgress)

    stateMachine.test(props) {
      // Loading → auth rotation instructions.
      awaitBody<LoadingSuccessBodyModel>()
      awaitBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel>()
    }
  }

  test("device ready screen No button shows no-device alert and dismisses back to screen") {
    migrationService.resumeResult = Ok(MigrationProgress.NotStarted(MigrationType.W3Upgrade))

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeIntroBodyModel> {
        onContinue()
      }
      cloudBackupHealthRepository.performSyncCalls.awaitItem() // onContinue
      awaitUntilBody<W3UpgradeDeviceReadyBodyModel> {
        onNo()
      }
      with(awaitItem()) {
        alertModel.shouldBeTypeOf<ButtonAlertModel>().onPrimaryButtonClick()
      }
      awaitUntilBody<W3UpgradeDeviceReadyBodyModel>()
    }
  }

  test("device ready screen back button returns to intro") {
    migrationService.resumeResult = Ok(MigrationProgress.NotStarted(MigrationType.W3Upgrade))

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeIntroBodyModel> {
        onContinue()
      }
      cloudBackupHealthRepository.performSyncCalls.awaitItem() // onContinue
      awaitUntilBody<W3UpgradeDeviceReadyBodyModel> {
        onBack?.invoke()
      }
      awaitUntilBody<W3UpgradeIntroBodyModel>()
    }
  }

  test("device ready shows error when firmware serial is missing") {
    migrationService.resumeResult = Ok(MigrationProgress.NotStarted(MigrationType.W3Upgrade))
    // firmwareDeviceInfoDao is reset (no stored device info) by default

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeIntroBodyModel> {
        onContinue()
      }
      cloudBackupHealthRepository.performSyncCalls.awaitItem() // onContinue
      awaitUntilBody<W3UpgradeDeviceReadyBodyModel> {
        onYes()
      }
      // Serial is null → error
      awaitUntilBody<FormBodyModel>(id = WalletMigrationEventTrackerScreenId.W3_UPGRADE_ERROR)
    }
  }

  // -- Creating keyset error tests --

  test("creating keyset shows error when SSEK is not found") {
    migrationService.resumeResult = Ok(MigrationProgress.NotStarted(MigrationType.W3Upgrade))
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock.copy(serial = "old-serial"))
    // ssekDao is empty — get(sealedSsek) returns null

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeIntroBodyModel> { onContinue() }
      cloudBackupHealthRepository.performSyncCalls.awaitItem() // onContinue
      awaitUntilBody<W3UpgradeDeviceReadyBodyModel> { onYes() }
      awaitUntilBodyMock<PairNewHardwareProps>(id = "pair-new-hardware") {}
        .request.shouldBeTypeOf<PairNewHardwareProps.Request.Ready>()
        .onSuccess(fingerprintEnrolled)
      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel> { onContinue() }
      awaitUntilBodyMock<ProofOfPossessionNfcProps>(id = "proof-of-possession") {}
        .request.shouldBeTypeOf<Request.HwKeyProof>()
        .onSuccess(HwFactorProofOfPossession("w1-proof"))

      // CreatingKeyset: SSEK not found → error
      awaitUntilBody<FormBodyModel>(id = WalletMigrationEventTrackerScreenId.W3_UPGRADE_ERROR)
    }
  }

  test("creating keyset shows error when migration proceed fails during auto-loop") {
    migrationService.resumeResult = Ok(MigrationProgress.NotStarted(MigrationType.W3Upgrade))
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock.copy(serial = "old-serial"))
    ssekDao.set(SealedSsekFake, SsekFake)
    // First proceed (NotStarted → CreateNewKeyset) fails
    migrationService.proceedResult =
      Err(MigrationError.ServerKeysetCreationFailed(Exception("fail")))

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeIntroBodyModel> { onContinue() }
      cloudBackupHealthRepository.performSyncCalls.awaitItem() // onContinue
      awaitUntilBody<W3UpgradeDeviceReadyBodyModel> { onYes() }
      awaitUntilBodyMock<PairNewHardwareProps>(id = "pair-new-hardware") {}
        .request.shouldBeTypeOf<PairNewHardwareProps.Request.Ready>()
        .onSuccess(fingerprintEnrolled)
      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel> { onContinue() }
      awaitUntilBodyMock<ProofOfPossessionNfcProps>(id = "proof-of-possession") {}
        .request.shouldBeTypeOf<Request.HwKeyProof>()
        .onSuccess(HwFactorProofOfPossession("w1-proof"))

      // CreatingKeyset: proceed fails → error
      awaitUntilBody<FormBodyModel>(id = WalletMigrationEventTrackerScreenId.W3_UPGRADE_ERROR)
    }
  }

  // -- Auth rotation error tests --

  test("auth rotation keeps current global auth key and generates a new recovery auth key when preparing rotation") {
    val authRotationProgress = MigrationProgress.AuthKeyRotation(
      type = MigrationType.W3Upgrade,
      currentKeybox = FullAccountMock.keybox,
      newKeyset = w3UpgradeKeyset()
    )
    migrationService.resumeResult = Ok(authRotationProgress)
    migrationService.proceedResult = Err(MigrationError.AuthKeyRotationFailed(Exception("fail")))

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel> { onContinue() }
      val popProps = awaitUntilBodyMock<ProofOfPossessionNfcProps>(id = "proof-of-possession") {}
      (popProps.request as Request.HwKeyProof)
        .onSuccess(HwFactorProofOfPossession("w1-proof"))
      awaitUntilBody<W3UpgradeNewHardwareAuthRotationInstructionsBodyModel> { onContinue() }
      val confirmProps =
        awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeRotateAppAuthKeysResult>>(
          id = "nfc-confirmable"
        ) {}
      confirmProps.onSuccess(rotationResult)
      awaitUntilBody<FormBodyModel>(id = WalletMigrationEventTrackerScreenId.W3_UPGRADE_ERROR)
    }

    val proceededState =
      migrationService.proceedCalls.single().shouldBeInstanceOf<MigrationProgress.AuthKeyRotation>()
    proceededState.newAppAuthKeys.shouldNotBeNull()
    proceededState.newAppAuthKeys!!.appGlobalAuthPublicKey
      .shouldBe(FullAccountMock.keybox.activeAppKeyBundle.authKey)
    proceededState.newAppAuthKeys!!.appRecoveryAuthPublicKey
      .shouldBe(AppRecoveryAuthPublicKeyMock2)
  }

  test("generating recovery auth key shows error when recovery key generation fails") {
    val authRotationProgress = MigrationProgress.AuthKeyRotation(
      type = MigrationType.W3Upgrade,
      currentKeybox = FullAccountMock.keybox,
      newKeyset = w3UpgradeKeyset()
    )
    migrationService.resumeResult = Ok(authRotationProgress)
    appKeysGenerator.recoveryAuthKeyResult = Err(Exception("recovery key gen failed"))

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel> { onContinue() }
      awaitUntilBody<FormBodyModel>(id = WalletMigrationEventTrackerScreenId.W3_UPGRADE_ERROR)
    }
  }

  test("running auth rotation shows error when proceed fails") {
    val authRotationProgress = MigrationProgress.AuthKeyRotation(
      type = MigrationType.W3Upgrade,
      currentKeybox = FullAccountMock.keybox,
      newKeyset = w3UpgradeKeyset()
    )
    migrationService.resumeResult = Ok(authRotationProgress)
    // proceed(AuthKeyRotation) fails
    migrationService.proceedResult = Err(MigrationError.AuthKeyRotationFailed(Exception("fail")))

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel> { onContinue() }
      val popProps = awaitUntilBodyMock<ProofOfPossessionNfcProps>(id = "proof-of-possession") {}
      (popProps.request as Request.HwKeyProof)
        .onSuccess(HwFactorProofOfPossession("w1-proof"))

      awaitUntilBody<W3UpgradeNewHardwareAuthRotationInstructionsBodyModel> { onContinue() }
      val confirmProps =
        awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeRotateAppAuthKeysResult>>(
          id = "nfc-confirmable"
        ) {}
      confirmProps.onSuccess(rotationResult)

      // RunningAuthRotation fails → error
      awaitUntilBody<FormBodyModel>(id = WalletMigrationEventTrackerScreenId.W3_UPGRADE_ERROR)
    }
  }

  // -- Preparing upgrade authorization error tests --

  test("preparing upgrade authorization shows error when DDK generation fails") {
    val descriptorBackupProgress = MigrationProgress.DescriptorBackup(
      type = MigrationType.W3Upgrade,
      currentKeybox = rotatedKeybox(),
      newKeyset = w3UpgradeKeyset()
    )
    migrationService.resumeResult = Ok(descriptorBackupProgress)
    // Fail the DDK keypair generation so PreparingUpgradeAuthorization fails
    relationshipsCrypto.generateAsymmetricKeyResult = Err(
      RelationshipsCryptoError.KeyGenerationFailed(Exception("DDK generation failed"))
    )

    stateMachine.test(props) {
      // PreparingUpgradeAuthorization fails → error
      awaitUntilBody<FormBodyModel>(id = WalletMigrationEventTrackerScreenId.W3_UPGRADE_ERROR)
    }
  }

  // -- AuthorizingW3Upgrade cancel test --

  test("authorizing W3 upgrade cancel goes to error") {
    val descriptorBackupProgress = MigrationProgress.DescriptorBackup(
      type = MigrationType.W3Upgrade,
      currentKeybox = rotatedKeybox(),
      newKeyset = w3UpgradeKeyset()
    )
    migrationService.resumeResult = Ok(descriptorBackupProgress)

    stateMachine.test(props) {
      val upgradeProps =
        awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeAuthorizeW3Result>>(
          id = "nfc-confirmable"
        ) {}
      upgradeProps.onCancel()

      awaitUntilBody<FormBodyModel>(id = WalletMigrationEventTrackerScreenId.W3_UPGRADE_ERROR)
    }
  }

  // -- RunningServerKeysetActivation error tests --

  test("server keyset activation shows error when descriptor backup proceed fails") {
    val descriptorBackupProgress = MigrationProgress.DescriptorBackup(
      type = MigrationType.W3Upgrade,
      currentKeybox = rotatedKeybox(),
      newKeyset = w3UpgradeKeyset()
    )
    migrationService.resumeResult = Ok(descriptorBackupProgress)
    // First proceed (descriptor backup) will fail
    migrationService.proceedResult = Err(
      MigrationError.DescriptorBackupFailed(Exception("fail"))
    )

    stateMachine.test(props) {
      val upgradeProps =
        awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeAuthorizeW3Result>>(
          id = "nfc-confirmable"
        ) {}
      upgradeProps.onSuccess(
        UpgradeAuthorizeW3Result(
          descriptorBackupsSignature = "descriptor-hw-sig",
          activateKeysetSignature = "activate-hw-sig",
          sealedDdkData = "sealed-ddk".encodeUtf8()
        )
      )

      // RunningServerKeysetActivation fails because descriptor backup proceed fails
      awaitUntilBody<FormBodyModel>(id = WalletMigrationEventTrackerScreenId.W3_UPGRADE_ERROR)
    }
  }

  // -- Cloud backup error test --

  test("cloud backup failure goes to error") {
    val cloudBackupProgress = MigrationProgress.CloudBackup(
      type = MigrationType.W3Upgrade,
      currentKeybox = provisionedKeybox(),
      newKeyset = w3UpgradeKeyset(),
      sealedCsek = SealedCsekFake
    )
    migrationService.resumeResult = Ok(cloudBackupProgress)

    stateMachine.test(props) {
      val cloudBackupProps =
        awaitUntilBodyMock<FullAccountCloudSignInAndBackupProps>(id = "cloud-backup") {}
      cloudBackupProps.onBackupFailed(null)

      awaitUntilBody<FormBodyModel>(id = WalletMigrationEventTrackerScreenId.W3_UPGRADE_ERROR)
    }
  }

  // -- CheckingForFunds tests --

  test("insufficient funds skips sweep and completes") {
    val localKeyboxActivation = MigrationProgress.LocalKeyboxActivation(
      type = MigrationType.W3Upgrade,
      currentKeybox = provisionedKeybox(),
      newKeyset = w3UpgradeKeyset()
    )
    migrationService.resumeResult = Ok(localKeyboxActivation)
    migrationService.savedOldHardwareFingerprint = "old-fingerprint"
    migrationService.estimateMigrationFeesResult = Err(MigrationError.InsufficientFundsForMigration)
    migrationService.proceedResult = Ok(MigrationProgress.Completed(MigrationType.W3Upgrade))
    accountService.setActiveAccount(FullAccountMock)

    stateMachine.test(props) {
      // Insufficient funds → skips sweep → proceeds to completion
      onUpgradeCompleteCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("completion shows loading state while returning to money home") {
    val localKeyboxActivation = MigrationProgress.LocalKeyboxActivation(
      type = MigrationType.W3Upgrade,
      currentKeybox = provisionedKeybox(),
      newKeyset = w3UpgradeKeyset()
    )
    migrationService.resumeResult = Ok(localKeyboxActivation)
    migrationService.savedOldHardwareFingerprint = "old-fingerprint"
    migrationService.estimateMigrationFeesResult = Err(MigrationError.InsufficientFundsForMigration)
    migrationService.proceedResult = Ok(MigrationProgress.Completed(MigrationType.W3Upgrade))
    accountService.setActiveAccount(FullAccountMock)

    stateMachine.test(props) {
      awaitUntilBody<LoadingSuccessBodyModel>(id = WalletMigrationEventTrackerScreenId.W3_UPGRADE_COMPLETE) {
        message.shouldBe("Loading wallet...")
      }
      onUpgradeCompleteCalls.awaitItem()
    }
  }

  test("fee estimation transient error goes to error state") {
    val localKeyboxActivation = MigrationProgress.LocalKeyboxActivation(
      type = MigrationType.W3Upgrade,
      currentKeybox = provisionedKeybox(),
      newKeyset = w3UpgradeKeyset()
    )
    migrationService.resumeResult = Ok(localKeyboxActivation)
    migrationService.savedOldHardwareFingerprint = "old-fingerprint"
    migrationService.estimateMigrationFeesResult = Err(
      MigrationError.FeeEstimationFailed(Exception("transient error"))
    )
    accountService.setActiveAccount(FullAccountMock)

    stateMachine.test(props) {
      awaitUntilBody<FormBodyModel>(id = WalletMigrationEventTrackerScreenId.W3_UPGRADE_ERROR)
    }
  }

  // -- Error screen tests --

  test("error screen retry resolves initial state") {
    // First resume returns an error state, retry resumes successfully
    val authRotationProgress = MigrationProgress.AuthKeyRotation(
      type = MigrationType.W3Upgrade,
      currentKeybox = FullAccountMock.keybox,
      newKeyset = w3UpgradeKeyset()
    )
    migrationService.resumeResult = Ok(authRotationProgress)
    migrationService.proceedResult = Err(MigrationError.AuthKeyRotationFailed(Exception("fail")))

    stateMachine.test(props) {
      awaitUntilScreenWithBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel>(
        matchingBody = { it.designSystemV2Model?.eyebrow == "Step 2 of 4" }
      ) {
        body.shouldBeInstanceOf<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel>()
          .onContinue()
      }
      awaitUntilBodyMock<ProofOfPossessionNfcProps>(id = "proof-of-possession") {
        (request as Request.HwKeyProof)
          .onSuccess(HwFactorProofOfPossession("w1-proof"))
      }
      awaitUntilBody<W3UpgradeNewHardwareAuthRotationInstructionsBodyModel> { onContinue() }
      awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeRotateAppAuthKeysResult>>(
        id = "nfc-confirmable"
      ) {
        onSuccess(rotationResult)
      }

      awaitUntilBody<FormBodyModel> {
        eventTrackerScreenInfo?.eventTrackerScreenId
          .shouldBe(WalletMigrationEventTrackerScreenId.W3_UPGRADE_ERROR)
        primaryButton.shouldNotBeNull().text.shouldBe("Retry")

        primaryButton.shouldNotBeNull().onClick()
      }

      awaitUntilScreenWithBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel>(
        matchingBody = { it.designSystemV2Model?.eyebrow == "Step 2 of 4" }
      )
    }
  }

  test("error screen cancel calls onExit when not in progress") {
    migrationService.resumeResult = Ok(MigrationProgress.NotStarted(MigrationType.W3Upgrade))

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeIntroBodyModel> { onContinue() }
      cloudBackupHealthRepository.performSyncCalls.awaitItem() // onContinue
      // No device info → error on device ready → Yes
      awaitUntilBody<W3UpgradeDeviceReadyBodyModel> { onYes() }

      awaitUntilBody<FormBodyModel> {
        secondaryButton.shouldNotBeNull().text.shouldBe("Cancel")
        secondaryButton.shouldNotBeNull().onClick()
      }
    }
    onExitCalls.awaitItem()
  }

  // -- Resume tests --

  test("resume from cloud backup preserves keybox and sealedCsek") {
    val cloudBackupProgress = MigrationProgress.CloudBackup(
      type = MigrationType.W3Upgrade,
      currentKeybox = provisionedKeybox(),
      newKeyset = w3UpgradeKeyset(),
      sealedCsek = SealedCsekFake
    )
    migrationService.resumeResult = Ok(cloudBackupProgress)

    stateMachine.test(props) {
      awaitUntilBodyMock<FullAccountCloudSignInAndBackupProps>(id = "cloud-backup") {
        sealedCsek.shouldBe(SealedCsekFake)
        keybox.activeSpendingKeyset.localId.shouldBe("uuid-0")
        isSkipCloudBackupInstructions.shouldBe(true)
      }
    }
  }

  test("resume from cloud backup onBackupSaved proceeds and loads fingerprint") {
    val cloudBackupProgress = MigrationProgress.CloudBackup(
      type = MigrationType.W3Upgrade,
      currentKeybox = provisionedKeybox(),
      newKeyset = w3UpgradeKeyset(),
      sealedCsek = SealedCsekFake
    )
    migrationService.resumeResult = Ok(cloudBackupProgress)
    migrationService.proceedResult = Ok(
      MigrationProgress.LocalKeyboxActivation(
        type = MigrationType.W3Upgrade,
        currentKeybox = provisionedKeybox(),
        newKeyset = w3UpgradeKeyset()
      )
    )
    migrationService.savedOldHardwareFingerprint = "old-w1-fp"

    stateMachine.test(props) {
      val cloudBackupProps =
        awaitUntilBodyMock<FullAccountCloudSignInAndBackupProps>(id = "cloud-backup") {}
      cloudBackupProps.onBackupSaved()

      // After backup saved, it proceeds and loads the fingerprint for sweep
      awaitUntilBody<W3UpgradeOldHardwareInstructionsBodyModel> {
        designSystemV2Model?.eyebrow.shouldBe("Step 4 of 4")
        onContinue()
      }
      // Assert the sweep props carry the persisted old-device fingerprint
      awaitUntilBodyMock<SweepUiProps>(id = "sweep") {
        val w3Context =
          sweepContext.shouldBeInstanceOf<build.wallet.recovery.sweep.SweepContext.W3Upgrade>()
        w3Context.replacedHardwareFingerprint.shouldBe("old-w1-fp")
      }
    }
    // Verify proceed was called with the CloudBackup progress
    migrationService.proceedCalls.single()
      .shouldBeInstanceOf<MigrationProgress.CloudBackup>()
  }

  test("resume from cloud backup onBackupSaved shows error when fingerprint is missing") {
    val cloudBackupProgress = MigrationProgress.CloudBackup(
      type = MigrationType.W3Upgrade,
      currentKeybox = provisionedKeybox(),
      newKeyset = w3UpgradeKeyset(),
      sealedCsek = SealedCsekFake
    )
    migrationService.resumeResult = Ok(cloudBackupProgress)
    migrationService.proceedResult = Ok(
      MigrationProgress.LocalKeyboxActivation(
        type = MigrationType.W3Upgrade,
        currentKeybox = provisionedKeybox(),
        newKeyset = w3UpgradeKeyset()
      )
    )
    // No fingerprint saved → error after proceed
    migrationService.savedOldHardwareFingerprint = null

    stateMachine.test(props) {
      val cloudBackupProps =
        awaitUntilBodyMock<FullAccountCloudSignInAndBackupProps>(id = "cloud-backup") {}
      cloudBackupProps.onBackupSaved()

      awaitUntilBody<FormBodyModel>(id = WalletMigrationEventTrackerScreenId.W3_UPGRADE_ERROR)
    }
  }

  test("resume from DdkBackup rewinds preserving sealedCsek and keyset data") {
    val originalKeybox = rotatedKeybox()
    val originalKeyset = w3UpgradeKeyset()
    migrationService.resumeResult = Ok(
      MigrationProgress.DdkBackup(
        type = MigrationType.W3Upgrade,
        currentKeybox = originalKeybox,
        newKeyset = originalKeyset,
        sealedCsek = SealedCsekFake
      )
    )
    migrationService.proceedResults.addAll(
      listOf(
        // proceed(DescriptorBackup) → ServerKeysetActivation
        Ok(
          MigrationProgress.ServerKeysetActivation(
            type = MigrationType.W3Upgrade,
            currentKeybox = originalKeybox,
            newKeyset = originalKeyset
          )
        ),
        // proceed(ServerKeysetActivation) → HardwareDescriptorProvisioning
        Ok(
          MigrationProgress.HardwareDescriptorProvisioning(
            type = MigrationType.W3Upgrade,
            currentKeybox = originalKeybox,
            newKeyset = originalKeyset,
            signedKeysResponse = SignedKeysetVerificationResponseMock
          )
        ),
        // proceed(DdkBackup) → CloudBackup
        Ok(
          MigrationProgress.CloudBackup(
            type = MigrationType.W3Upgrade,
            currentKeybox = originalKeybox,
            newKeyset = originalKeyset,
            sealedCsek = SealedCsekFake
          )
        )
      )
    )

    stateMachine.test(props) {
      // DdkBackup rewinds to PreparingUpgradeAuthorization → AuthorizingW3Upgrade
      val upgradeProps =
        awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeAuthorizeW3Result>>(
          id = "nfc-confirmable"
        ) {}

      // Complete the NFC tap so the rewound DescriptorBackup flows into proceed()
      upgradeProps.onSuccess(
        UpgradeAuthorizeW3Result(
          descriptorBackupsSignature = "descriptor-hw-sig",
          activateKeysetSignature = "activate-hw-sig",
          sealedDdkData = "sealed-ddk".encodeUtf8()
        )
      )

      // Reaches ProvisioningHardwareDescriptor after batched proceeds
      awaitUntilBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {}
    }

    // The first proceed call is the rewound DescriptorBackup — verify it carries
    // the original DdkBackup's keybox, keyset, and sealedCsek.
    val rewoundDescriptorBackup = migrationService.proceedCalls.first()
      .shouldBeInstanceOf<MigrationProgress.DescriptorBackup>()
    rewoundDescriptorBackup.currentKeybox.shouldBe(originalKeybox)
    rewoundDescriptorBackup.newKeyset.shouldBe(originalKeyset)
    rewoundDescriptorBackup.sealedCsek.shouldBe(SealedCsekFake)
  }

  test("resume from ServerKeysetActivation rewinds preserving keybox and keyset") {
    val originalKeybox = rotatedKeybox()
    val originalKeyset = w3UpgradeKeyset()
    migrationService.resumeResult = Ok(
      MigrationProgress.ServerKeysetActivation(
        type = MigrationType.W3Upgrade,
        currentKeybox = originalKeybox,
        newKeyset = originalKeyset,
        sealedCsek = SealedCsekFake
      )
    )
    migrationService.proceedResults.addAll(
      listOf(
        Ok(
          MigrationProgress.ServerKeysetActivation(
            type = MigrationType.W3Upgrade,
            currentKeybox = originalKeybox,
            newKeyset = originalKeyset
          )
        ),
        Ok(
          MigrationProgress.HardwareDescriptorProvisioning(
            type = MigrationType.W3Upgrade,
            currentKeybox = originalKeybox,
            newKeyset = originalKeyset,
            signedKeysResponse = SignedKeysetVerificationResponseMock
          )
        ),
        Ok(
          MigrationProgress.CloudBackup(
            type = MigrationType.W3Upgrade,
            currentKeybox = originalKeybox,
            newKeyset = originalKeyset,
            sealedCsek = SealedCsekFake
          )
        )
      )
    )

    stateMachine.test(props) {
      val upgradeProps =
        awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeAuthorizeW3Result>>(
          id = "nfc-confirmable"
        ) {}
      upgradeProps.onSuccess(
        UpgradeAuthorizeW3Result(
          descriptorBackupsSignature = "descriptor-hw-sig",
          activateKeysetSignature = "activate-hw-sig",
          sealedDdkData = "sealed-ddk".encodeUtf8()
        )
      )
      awaitUntilBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {}
    }

    val rewoundDescriptorBackup = migrationService.proceedCalls.first()
      .shouldBeInstanceOf<MigrationProgress.DescriptorBackup>()
    rewoundDescriptorBackup.currentKeybox.shouldBe(originalKeybox)
    rewoundDescriptorBackup.newKeyset.shouldBe(originalKeyset)
    rewoundDescriptorBackup.sealedCsek.shouldBe(SealedCsekFake)
  }

  test("resume from HardwareDescriptorProvisioning rewinds preserving keybox and keyset") {
    val originalKeybox = rotatedKeybox()
    val originalKeyset = w3UpgradeKeyset()
    migrationService.resumeResult = Ok(
      MigrationProgress.HardwareDescriptorProvisioning(
        type = MigrationType.W3Upgrade,
        currentKeybox = originalKeybox,
        newKeyset = originalKeyset,
        signedKeysResponse = SignedKeysetVerificationResponseMock,
        sealedCsek = SealedCsekFake
      )
    )
    migrationService.proceedResults.addAll(
      listOf(
        Ok(
          MigrationProgress.ServerKeysetActivation(
            type = MigrationType.W3Upgrade,
            currentKeybox = originalKeybox,
            newKeyset = originalKeyset
          )
        ),
        Ok(
          MigrationProgress.HardwareDescriptorProvisioning(
            type = MigrationType.W3Upgrade,
            currentKeybox = originalKeybox,
            newKeyset = originalKeyset,
            signedKeysResponse = SignedKeysetVerificationResponseMock
          )
        ),
        Ok(
          MigrationProgress.CloudBackup(
            type = MigrationType.W3Upgrade,
            currentKeybox = originalKeybox,
            newKeyset = originalKeyset,
            sealedCsek = SealedCsekFake
          )
        )
      )
    )

    stateMachine.test(props) {
      val upgradeProps =
        awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeAuthorizeW3Result>>(
          id = "nfc-confirmable"
        ) {}
      upgradeProps.onSuccess(
        UpgradeAuthorizeW3Result(
          descriptorBackupsSignature = "descriptor-hw-sig",
          activateKeysetSignature = "activate-hw-sig",
          sealedDdkData = "sealed-ddk".encodeUtf8()
        )
      )
      awaitUntilBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {}
    }

    val rewoundDescriptorBackup = migrationService.proceedCalls.first()
      .shouldBeInstanceOf<MigrationProgress.DescriptorBackup>()
    rewoundDescriptorBackup.currentKeybox.shouldBe(originalKeybox)
    rewoundDescriptorBackup.newKeyset.shouldBe(originalKeyset)
    rewoundDescriptorBackup.sealedCsek.shouldBe(SealedCsekFake)
  }

  test("resume from LocalKeyboxActivation with fingerprint reaches sweep") {
    val localKeyboxActivation = MigrationProgress.LocalKeyboxActivation(
      type = MigrationType.W3Upgrade,
      currentKeybox = provisionedKeybox(),
      newKeyset = w3UpgradeKeyset()
    )
    migrationService.resumeResult = Ok(localKeyboxActivation)
    migrationService.savedOldHardwareFingerprint = "persisted-w1-fingerprint"
    migrationService.proceedResult = Ok(MigrationProgress.Completed(MigrationType.W3Upgrade))
    accountService.setActiveAccount(FullAccountMock)

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeOldHardwareInstructionsBodyModel> { onContinue() }
      awaitUntilBodyMock<SweepUiProps>(id = "sweep") {
        val w3Context =
          sweepContext.shouldBeInstanceOf<build.wallet.recovery.sweep.SweepContext.W3Upgrade>()
        w3Context.replacedHardwareFingerprint.shouldBe("persisted-w1-fingerprint")
      }
    }
  }

  test("resume from LocalKeyboxActivation without fingerprint goes to error") {
    val localKeyboxActivation = MigrationProgress.LocalKeyboxActivation(
      type = MigrationType.W3Upgrade,
      currentKeybox = provisionedKeybox(),
      newKeyset = w3UpgradeKeyset()
    )
    migrationService.resumeResult = Ok(localKeyboxActivation)
    // No fingerprint saved
    migrationService.savedOldHardwareFingerprint = null
    accountService.setActiveAccount(FullAccountMock)

    stateMachine.test(props) {
      awaitUntilBody<FormBodyModel>(id = WalletMigrationEventTrackerScreenId.W3_UPGRADE_ERROR)
    }
  }

  test("resume from Completed shows intro (completed is not in-progress)") {
    migrationService.resumeResult = Ok(MigrationProgress.Completed(MigrationType.W3Upgrade))
    accountService.setActiveAccount(FullAccountMock)

    stateMachine.test(props) {
      // Completed means migration is done — not in progress, shows intro
      awaitUntilBody<W3UpgradeIntroBodyModel>()
    }
  }

  // -- Proceed data verification tests --

  test("auth rotation proceed carries hwAuthPublicKey, hwSignedAccountId, and proof") {
    val authRotationProgress = MigrationProgress.AuthKeyRotation(
      type = MigrationType.W3Upgrade,
      currentKeybox = FullAccountMock.keybox,
      newKeyset = w3UpgradeKeyset()
    )
    val descriptorBackupProgress = MigrationProgress.DescriptorBackup(
      type = MigrationType.W3Upgrade,
      currentKeybox = rotatedKeybox(),
      newKeyset = w3UpgradeKeyset()
    )
    migrationService.resumeResult = Ok(authRotationProgress)
    migrationService.proceedResult = Ok(descriptorBackupProgress)

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel> { onContinue() }
      awaitUntilBodyMock<ProofOfPossessionNfcProps>(id = "proof-of-possession") {
        (request as Request.HwKeyProof)
          .onSuccess(HwFactorProofOfPossession("w1-pop-token"))
      }
      awaitUntilBody<W3UpgradeNewHardwareAuthRotationInstructionsBodyModel> { onContinue() }
      awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeRotateAppAuthKeysResult>>(
        id = "nfc-confirmable"
      ) {
        onSuccess(rotationResult)
      }
      // Wait for the composite upgrade authorization tap
      awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeAuthorizeW3Result>>(
        id = "nfc-confirmable"
      ) {}
    }

    // Verify the auth rotation proceed was called with correct data
    val proceededAuth = migrationService.proceedCalls.single()
      .shouldBeInstanceOf<MigrationProgress.AuthKeyRotation>()
    proceededAuth.hwAuthPublicKey.shouldBe(HwAuthSecp256k1PublicKeyMock)
    proceededAuth.hwSignedAccountId.shouldBe("signed-account-id")
    proceededAuth.proof.shouldBe(
      PrivilegedActionProof.HwKeyProof(HwFactorProofOfPossession("w1-pop-token"))
    )
    proceededAuth.newAppAuthKeys.shouldNotBeNull()
    proceededAuth.newAppAuthKeys!!.appGlobalAuthKeyHwSignature.shouldBe(
      AppGlobalAuthKeyHwSignature("hw-app-auth-signature")
    )
  }

  test("server keyset activation batches descriptor backup, keyset activation, and DDK backup proceeds") {
    val descriptorBackupProgress = MigrationProgress.DescriptorBackup(
      type = MigrationType.W3Upgrade,
      currentKeybox = rotatedKeybox(),
      newKeyset = w3UpgradeKeyset()
    )
    migrationService.resumeResult = Ok(descriptorBackupProgress)
    migrationService.proceedResults.addAll(
      listOf(
        // 1. proceed(DescriptorBackup) → ServerKeysetActivation
        Ok(
          MigrationProgress.ServerKeysetActivation(
            type = MigrationType.W3Upgrade,
            currentKeybox = rotatedKeybox(),
            newKeyset = w3UpgradeKeyset()
          )
        ),
        // 2. proceed(ServerKeysetActivation) → HardwareDescriptorProvisioning
        Ok(
          MigrationProgress.HardwareDescriptorProvisioning(
            type = MigrationType.W3Upgrade,
            currentKeybox = rotatedKeybox(),
            newKeyset = w3UpgradeKeyset(),
            signedKeysResponse = SignedKeysetVerificationResponseMock
          )
        ),
        // 3. proceed(DdkBackup) → CloudBackup
        Ok(
          MigrationProgress.CloudBackup(
            type = MigrationType.W3Upgrade,
            currentKeybox = rotatedKeybox(),
            newKeyset = w3UpgradeKeyset()
          )
        )
      )
    )

    stateMachine.test(props) {
      val upgradeProps =
        awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeAuthorizeW3Result>>(
          id = "nfc-confirmable"
        ) {}
      upgradeProps.onSuccess(
        UpgradeAuthorizeW3Result(
          descriptorBackupsSignature = "descriptor-hw-sig",
          activateKeysetSignature = "activate-hw-sig",
          sealedDdkData = "sealed-ddk".encodeUtf8()
        )
      )

      // After batched proceed calls, reaches ProvisioningHardwareDescriptor
      awaitUntilBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {}
    }

    // Verify proceed was called 3 times in order: DescriptorBackup, ServerKeysetActivation, DdkBackup
    migrationService.proceedCalls.map { it::class.simpleName }.shouldBe(
      listOf("DescriptorBackup", "ServerKeysetActivation", "DdkBackup")
    )

    // Verify the DdkBackup proceed carries sealedDdkData
    val ddkBackup = migrationService.proceedCalls[2]
      .shouldBeInstanceOf<MigrationProgress.DdkBackup>()
    ddkBackup.sealedDdkData.shouldBe("sealed-ddk".encodeUtf8())
  }

  test("provisioning hardware descriptor proceed saves appGlobalAuthKeyHwSignature") {
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
        // proceed(DdkBackup) → CloudBackup
        Ok(
          MigrationProgress.CloudBackup(
            type = MigrationType.W3Upgrade,
            currentKeybox = rotatedKeybox(),
            newKeyset = w3UpgradeKeyset()
          )
        ),
        // proceed(HardwareDescriptorProvisioning) → DdkBackup (skipped to CloudBackup in provisioning handler)
        Ok(
          MigrationProgress.DdkBackup(
            type = MigrationType.W3Upgrade,
            currentKeybox = provisionedKeybox(),
            newKeyset = w3UpgradeKeyset(),
            sealedCsek = SealedCsekFake
          )
        )
      )
    )

    stateMachine.test(props) {
      // Navigate through auth rotation
      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel> { onContinue() }
      val popProps = awaitUntilBodyMock<ProofOfPossessionNfcProps>(id = "proof-of-possession") {}
      (popProps.request as Request.HwKeyProof)
        .onSuccess(HwFactorProofOfPossession("w1-proof"))

      awaitUntilBody<W3UpgradeNewHardwareAuthRotationInstructionsBodyModel> { onContinue() }
      val confirmProps =
        awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeRotateAppAuthKeysResult>>(
          id = "nfc-confirmable"
        ) {}
      confirmProps.onSuccess(rotationResult)

      // AuthorizingW3Upgrade
      val upgradeProps =
        awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeAuthorizeW3Result>>(
          id = "nfc-confirmable"
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
        awaitUntilBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {
          config.hardwareVerification.shouldBe(Required())
          config.hardwareTypeOverride.shouldBe(HardwareType.W3)
        }
      @Suppress("UNCHECKED_CAST")
      (provisioningProps as NfcSessionUIStateMachineProps<Any>).onSuccess(
        Ok(AppGlobalAuthKeyHwSignatureMock)
      )

      // After provisioning, reaches cloud backup
      awaitUntilBodyMock<FullAccountCloudSignInAndBackupProps>(id = "cloud-backup") {
        // Keybox should have the new app global auth key HW signature from provisioning
        keybox.appGlobalAuthKeyHwSignature.shouldBe(AppGlobalAuthKeyHwSignatureMock)
      }
    }

    // Verify proceed calls include HardwareDescriptorProvisioning with the signature
    val hwProvisioningCall = migrationService.proceedCalls
      .filterIsInstance<MigrationProgress.HardwareDescriptorProvisioning>()
    hwProvisioningCall.size.shouldBe(1)
    hwProvisioningCall.single().appGlobalAuthKeyHwSignature.shouldBe(AppGlobalAuthKeyHwSignatureMock)
  }

  test("pairing new hardware exit returns to intro") {
    migrationService.resumeResult = Ok(MigrationProgress.NotStarted(MigrationType.W3Upgrade))
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock.copy(serial = "old-serial"))

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeIntroBodyModel> { onContinue() }
      cloudBackupHealthRepository.performSyncCalls.awaitItem() // onContinue
      awaitUntilBody<W3UpgradeDeviceReadyBodyModel> { onYes() }

      val pairProps = awaitUntilBodyMock<PairNewHardwareProps>(id = "pair-new-hardware") {}
      pairProps.onExit.shouldNotBeNull().invoke()

      awaitUntilBody<W3UpgradeIntroBodyModel>()
    }
  }

  test("cloud-restored pairing flow back returns to intro without leaving upgrade") {
    migrationService.resumeResult = Ok(
      MigrationProgress.NotStarted(
        type = MigrationType.W3Upgrade,
        resumedFromCloudBackup = true
      )
    )
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock.copy(serial = "old-serial"))

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeIntroBodyModel> {
        onBack.shouldBeNull()
        onContinue()
      }
      // Backup health sync runs on Continue click.
      cloudBackupHealthRepository.performSyncCalls.awaitItem() // onContinue
      awaitUntilBody<W3UpgradeDeviceReadyBodyModel> { onYes() }
      awaitUntilBodyMock<PairNewHardwareProps>(id = "pair-new-hardware") {
        onExit()
      }
      awaitUntilBody<W3UpgradeIntroBodyModel> {
        onBack.shouldBeNull()
      }
    }
  }

  test("first-time flow shows exit button on old hardware instruction screen") {
    migrationService.resumeResult = Ok(MigrationProgress.NotStarted(MigrationType.W3Upgrade))
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock.copy(serial = "old-device-serial"))

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeIntroBodyModel> { onContinue() }
      cloudBackupHealthRepository.performSyncCalls.awaitItem() // onContinue
      awaitUntilBody<W3UpgradeDeviceReadyBodyModel> { onYes() }
      awaitUntilBodyMock<PairNewHardwareProps>(id = "pair-new-hardware") {}
        .request.shouldBeTypeOf<PairNewHardwareProps.Request.Ready>()
        .onSuccess(fingerprintEnrolled)

      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel> {
        // Exit button should be available in first-time flow (pre-keyset)
        onDeferExit.shouldNotBeNull()
      }
    }
  }

  test("exit from old hardware instructions shows confirmation alert and calls onExit") {
    migrationService.resumeResult = Ok(MigrationProgress.NotStarted(MigrationType.W3Upgrade))
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock.copy(serial = "old-device-serial"))

    var exitCalled = false
    val exitProps = props.copy(onExit = { exitCalled = true })

    stateMachine.test(exitProps) {
      awaitUntilBody<W3UpgradeIntroBodyModel> { onContinue() }
      cloudBackupHealthRepository.performSyncCalls.awaitItem() // onContinue
      awaitUntilBody<W3UpgradeDeviceReadyBodyModel> { onYes() }
      awaitUntilBodyMock<PairNewHardwareProps>(id = "pair-new-hardware") {}
        .request.shouldBeTypeOf<PairNewHardwareProps.Request.Ready>()
        .onSuccess(fingerprintEnrolled)

      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel> {
        onDeferExit.shouldNotBeNull().invoke()
      }

      // ConfirmingExit state shows the instruction screen with an alert
      awaitUntilScreenWithBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel>(
        matchingScreen = { it.alertModel != null }
      ) {
        alertModel.shouldNotBeNull()
          .shouldBeInstanceOf<build.wallet.ui.model.alert.ButtonAlertModel>()
          .onPrimaryButtonClick()
      }
    }

    exitCalled.shouldBe(true)
    // No DAO state to clean up — all writes deferred until after W1 tap
    migrationService.clearMigrationCalls.shouldBe(emptyList())
  }

  // -- Action event tracking tests --

  test("STARTED event fires when user taps Continue on intro") {
    migrationService.resumeResult = Ok(MigrationProgress.NotStarted(MigrationType.W3Upgrade))
    val trackedActions = mutableListOf<Action>()

    createStateMachine(recordingEventTracker(trackedActions)).test(props) {
      awaitUntilBody<W3UpgradeIntroBodyModel> { onContinue() }
      cloudBackupHealthRepository.performSyncCalls.awaitItem() // onContinue
      awaitUntilBody<W3UpgradeDeviceReadyBodyModel>()
    }
    trackedActions.shouldContain(Action.ACTION_APP_W3_UPGRADE_STARTED)
  }

  test("HW_PAIRED event fires when new hardware is paired") {
    migrationService.resumeResult = Ok(MigrationProgress.NotStarted(MigrationType.W3Upgrade))
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock.copy(serial = "old-device-serial"))
    val trackedActions = mutableListOf<Action>()

    createStateMachine(recordingEventTracker(trackedActions)).test(props) {
      awaitUntilBody<W3UpgradeIntroBodyModel> { onContinue() }
      cloudBackupHealthRepository.performSyncCalls.awaitItem() // onContinue
      awaitUntilBody<W3UpgradeDeviceReadyBodyModel> { onYes() }
      awaitUntilBodyMock<PairNewHardwareProps>(id = "pair-new-hardware") {}
        .request.shouldBeTypeOf<PairNewHardwareProps.Request.Ready>()
        .onSuccess(fingerprintEnrolled)
      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel>()
    }
    trackedActions.shouldContain(Action.ACTION_APP_W3_UPGRADE_HW_PAIRED)
  }

  test("CANCELLED event fires when user confirms exit") {
    migrationService.resumeResult = Ok(MigrationProgress.NotStarted(MigrationType.W3Upgrade))
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock.copy(serial = "old-device-serial"))
    val trackedActions = mutableListOf<Action>()
    var exitCalled = false
    val exitProps = props.copy(onExit = { exitCalled = true })

    createStateMachine(recordingEventTracker(trackedActions)).test(exitProps) {
      awaitUntilBody<W3UpgradeIntroBodyModel> { onContinue() }
      cloudBackupHealthRepository.performSyncCalls.awaitItem() // onContinue
      awaitUntilBody<W3UpgradeDeviceReadyBodyModel> { onYes() }
      awaitUntilBodyMock<PairNewHardwareProps>(id = "pair-new-hardware") {}
        .request.shouldBeTypeOf<PairNewHardwareProps.Request.Ready>()
        .onSuccess(fingerprintEnrolled)

      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel> {
        onDeferExit.shouldNotBeNull().invoke()
      }
      awaitUntilScreenWithBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel>(
        matchingScreen = { it.alertModel != null }
      ) {
        alertModel.shouldNotBeNull()
          .shouldBeInstanceOf<ButtonAlertModel>()
          .onPrimaryButtonClick()
      }
    }
    exitCalled.shouldBe(true)
    trackedActions.shouldContain(Action.ACTION_APP_W3_UPGRADE_CANCELLED)
  }

  test("AUTH_ROTATED event fires after successful auth key rotation") {
    val authRotationProgress = MigrationProgress.AuthKeyRotation(
      type = MigrationType.W3Upgrade,
      currentKeybox = FullAccountMock.keybox,
      newKeyset = w3UpgradeKeyset()
    )
    val descriptorBackupProgress = MigrationProgress.DescriptorBackup(
      type = MigrationType.W3Upgrade,
      currentKeybox = rotatedKeybox(),
      newKeyset = w3UpgradeKeyset()
    )
    migrationService.resumeResult = Ok(authRotationProgress)
    migrationService.proceedResult = Ok(descriptorBackupProgress)
    val trackedActions = mutableListOf<Action>()

    createStateMachine(recordingEventTracker(trackedActions)).test(props) {
      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel> { onContinue() }
      awaitUntilBodyMock<ProofOfPossessionNfcProps>(id = "proof-of-possession") {
        (request as Request.HwKeyProof).onSuccess(HwFactorProofOfPossession("w1-proof"))
      }
      awaitUntilBody<W3UpgradeNewHardwareAuthRotationInstructionsBodyModel> { onContinue() }
      awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeRotateAppAuthKeysResult>>(
        id = "nfc-confirmable"
      ) {
        onSuccess(rotationResult)
      }
      // RunningAuthRotation calls proceed() and then fires AUTH_ROTATED
      awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeAuthorizeW3Result>>(
        id = "nfc-confirmable"
      ) {}
    }
    trackedActions.shouldContain(Action.ACTION_APP_W3_UPGRADE_AUTH_ROTATED)
  }

  test("COMPLETE event fires when Success state is entered") {
    val localKeyboxActivation = MigrationProgress.LocalKeyboxActivation(
      type = MigrationType.W3Upgrade,
      currentKeybox = provisionedKeybox(),
      newKeyset = w3UpgradeKeyset()
    )
    migrationService.resumeResult = Ok(localKeyboxActivation)
    migrationService.savedOldHardwareFingerprint = "old-fingerprint"
    migrationService.estimateMigrationFeesResult = Err(MigrationError.InsufficientFundsForMigration)
    migrationService.proceedResult = Ok(MigrationProgress.Completed(MigrationType.W3Upgrade))
    accountService.setActiveAccount(FullAccountMock)
    val trackedActions = mutableListOf<Action>()

    createStateMachine(recordingEventTracker(trackedActions)).test(props) {
      onUpgradeCompleteCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
    trackedActions.shouldContain(Action.ACTION_APP_W3_UPGRADE_COMPLETE)
  }

  test("dismissing exit confirmation keeps user on instruction screen") {
    migrationService.resumeResult = Ok(MigrationProgress.NotStarted(MigrationType.W3Upgrade))
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock.copy(serial = "old-device-serial"))

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeIntroBodyModel> { onContinue() }
      cloudBackupHealthRepository.performSyncCalls.awaitItem() // onContinue
      awaitUntilBody<W3UpgradeDeviceReadyBodyModel> { onYes() }
      awaitUntilBodyMock<PairNewHardwareProps>(id = "pair-new-hardware") {}
        .request.shouldBeTypeOf<PairNewHardwareProps.Request.Ready>()
        .onSuccess(fingerprintEnrolled)

      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel> {
        onDeferExit.shouldNotBeNull().invoke()
      }

      // Dismiss the alert
      awaitUntilScreenWithBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel>(
        matchingScreen = { it.alertModel != null }
      ) {
        alertModel.shouldNotBeNull()
          .shouldBeInstanceOf<build.wallet.ui.model.alert.ButtonAlertModel>()
          .onSecondaryButtonClick!!()
      }

      // Should still be on the instruction screen with no alert
      awaitUntilScreenWithBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel>(
        matchingScreen = { it.alertModel == null }
      )
    }

    migrationService.clearMigrationCalls.shouldBe(emptyList())
  }

  test("resume flow does not show exit button on old hardware instruction screen") {
    val authRotationProgress = MigrationProgress.AuthKeyRotation(
      type = MigrationType.W3Upgrade,
      currentKeybox = FullAccountMock.keybox,
      newKeyset = w3UpgradeKeyset()
    )
    migrationService.resumeResult = Ok(authRotationProgress)

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel> {
        // Exit button should NOT be available in resume flow (post-keyset)
        onDeferExit.shouldBe(null)
      }
    }
  }

  test("exit does not modify AppInstallation serial — pairing skipped the write") {
    migrationService.resumeResult = Ok(MigrationProgress.NotStarted(MigrationType.W3Upgrade))
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock.copy(serial = "old-w1-serial"))

    var exitCalled = false
    val exitProps = props.copy(onExit = { exitCalled = true })

    stateMachine.test(exitProps) {
      awaitUntilBody<W3UpgradeIntroBodyModel> { onContinue() }
      cloudBackupHealthRepository.performSyncCalls.awaitItem() // onContinue
      awaitUntilBody<W3UpgradeDeviceReadyBodyModel> { onYes() }
      awaitUntilBodyMock<PairNewHardwareProps>(id = "pair-new-hardware") {}
        .request.shouldBeTypeOf<PairNewHardwareProps.Request.Ready>()
        .onSuccess(fingerprintEnrolled)

      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel> {
        onDeferExit.shouldNotBeNull().invoke()
      }

      awaitUntilScreenWithBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel>(
        matchingScreen = { it.alertModel != null }
      ) {
        alertModel.shouldNotBeNull()
          .shouldBeInstanceOf<build.wallet.ui.model.alert.ButtonAlertModel>()
          .onPrimaryButtonClick()
      }
    }

    exitCalled.shouldBe(true)
  }

  test("resume flow falls back to W1 proof only when direct auth rotation retry needs proof") {
    val descriptorBackupProgress = MigrationProgress.DescriptorBackup(
      type = MigrationType.W3Upgrade,
      currentKeybox = rotatedKeybox(),
      newKeyset = w3UpgradeKeyset()
    )
    migrationService.resumeResult = Ok(resumedAuthRotationProgress())
    migrationService.proceedResults.addAll(
      listOf(
        Err(MigrationError.MissingContext.W3AuthRotationOldHardwareProof),
        Ok(descriptorBackupProgress)
      )
    )

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel> {
        onDeferExit.shouldBe(null)
        onContinue()
      }
      awaitUntilBodyMock<ProofOfPossessionNfcProps>(id = "proof-of-possession") {
        (request as Request.HwKeyProof)
          .onSuccess(HwFactorProofOfPossession("w1-proof"))
      }
      awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeAuthorizeW3Result>>(
        id = "nfc-confirmable"
      ) {}
    }

    migrationService.resumeCalls.shouldBe(listOf(MigrationType.W3Upgrade))
    migrationService.proceedCalls.first().shouldBe(resumedAuthRotationProgress())
    val retriedState = migrationService.proceedCalls.last()
      .shouldBeInstanceOf<MigrationProgress.AuthKeyRotation>()
    retriedState.newAppAuthKeys.shouldBe(rotatedW3AuthKeys())
    retriedState.hwAuthPublicKey.shouldBe(HwAuthSecp256k1PublicKeyMock)
    retriedState.hwSignedAccountId.shouldBe("signed-account-id")
    retriedState.proof.shouldBe(
      PrivilegedActionProof.HwKeyProof(HwFactorProofOfPossession("w1-proof"))
    )
  }

  test("cloud-restored auth rotation uses rotateAppAuthKeys and skips upgradeRotateAppAuthKeys") {
    val w3DeviceInfo = FirmwareDeviceInfoMock.copy(hwRevision = "w3a-evt", serial = "w3-serial")
    val nfcCommandsMock = W3NfcCommandsMock { Turbine(name = it) }
    nfcCommandsMock.deviceInfoResult = w3DeviceInfo
    migrationService.resumeResult = Ok(resumedFromCloudBackupAuthRotationProgress())

    stateMachine.test(props) {
      awaitUntilBody<W3UpgradeNewHardwareAuthRotationInstructionsBodyModel> { onContinue() }
      val confirmProps =
        awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<RotateAppAuthKeysCompositeResult>>(
          id = "nfc-confirmable"
        ) {}
      confirmProps.session(w3NfcSessionFake(), nfcCommandsMock)
    }

    authTokensService.refreshAccessTokenCalls.shouldContain(FullAccountMock.accountId to Global)
    nfcCommandsMock.rotateAppAuthKeysCalls.awaitItem()
    nfcCommandsMock.upgradeRotateAppAuthKeysCalls.expectNoEvents()
  }

  test("cloud-restored composite tap stores unsealed SSEK before continuing") {
    val recoveredSsek = "recovered-ssek".encodeUtf8()
    migrationService.resumeResult = Ok(
      MigrationProgress.DescriptorBackup(
        type = MigrationType.W3Upgrade,
        currentKeybox = rotatedKeybox(),
        newKeyset = w3UpgradeKeyset(),
        resumedFromCloudBackup = true,
        sealedSsekForDecryption = SealedSsekFake
      )
    )
    migrationService.proceedResult = Ok(MigrationProgress.Completed(MigrationType.W3Upgrade))

    stateMachine.test(props) {
      val upgradeProps =
        awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<UpgradeAuthorizeW3Result>>(
          id = "nfc-confirmable"
        ) {}
      upgradeProps.onSuccess(
        UpgradeAuthorizeW3Result(
          descriptorBackupsSignature = "descriptor-hw-sig",
          activateKeysetSignature = "activate-hw-sig",
          sealedDdkData = "sealed-ddk".encodeUtf8(),
          unsealedSsek = recoveredSsek
        )
      )
      onUpgradeCompleteCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }

    ssekDao.get(SealedSsekFake).get().shouldNotBeNull().key.raw.shouldBe(recoveredSsek)
  }
})

private val noopEventTracker = object : EventTracker {
  override fun track(
    action: Action,
    context: EventTrackerContext?,
  ) {}

  override fun track(eventTrackerCountInfo: EventTrackerCountInfo) {}

  override fun track(eventTrackerScreenInfo: EventTrackerScreenInfo) {}

  override fun track(eventTrackerFingerprintScanStatsInfo: EventTrackerFingerprintScanStatsInfo) {}
}

private fun recordingEventTracker(actions: MutableList<Action>) =
  object : EventTracker {
    override fun track(
      action: Action,
      context: EventTrackerContext?,
    ) {
      actions += action
    }

    override fun track(eventTrackerCountInfo: EventTrackerCountInfo) {}

    override fun track(eventTrackerScreenInfo: EventTrackerScreenInfo) {}

    override fun track(eventTrackerFingerprintScanStatsInfo: EventTrackerFingerprintScanStatsInfo) {}
  }

private fun w3NfcSessionFake() =
  NfcSessionFake(
    NfcSession.Parameters(
      isHardwareFake = true,
      hardwareType = HardwareType.W3,
      needsAuthentication = true,
      shouldLock = true,
      skipFirmwareTelemetry = false,
      asyncNfcSigning = false,
      nfcFlowName = "fake-flow-name",
      requirePairedHardware = NfcSession.RequirePairedHardware.NotRequired,
      maxNfcRetryAttempts = 3,
      onTagConnected = {},
      onTagDisconnected = {}
    )
  )
