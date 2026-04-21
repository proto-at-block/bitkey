package build.wallet.statemachine.account.recovery

import bitkey.auth.AccessToken
import bitkey.auth.AccountAuthTokens
import bitkey.auth.AuthTokenScope.Global
import bitkey.auth.AuthTokenScope.Recovery
import bitkey.auth.RefreshToken
import build.wallet.account.analytics.AppInstallationDaoMock
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action.ACTION_APP_CLOUD_RECOVERY_KEY_RECOVERED
import build.wallet.auth.AccountAuthenticatorMock
import build.wallet.auth.AppAuthKeyMessageSignerMock
import build.wallet.auth.AuthSignatureMismatch
import build.wallet.auth.AuthTokensServiceFake
import build.wallet.auth.FullAccountAuthKeyRotationServiceMock
import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.cloud.backup.AccountRestorationMock
import build.wallet.cloud.backup.AllFullAccountBackupMocks
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.CloudBackupV3
import build.wallet.cloud.backup.FullAccountCloudBackupRestorerMock
import build.wallet.cloud.backup.csek.Csek
import build.wallet.cloud.backup.csek.CsekDaoFake
import build.wallet.cloud.backup.csek.CsekFake
import build.wallet.cloud.backup.local.CloudBackupDaoFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.AuthF8eClient.InitiateAuthenticationSuccess
import build.wallet.f8e.auth.AuthF8eClientMock
import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.test.HttpResponseMock
import build.wallet.nfc.NfcCommandsMock
import build.wallet.nfc.NfcSessionFake
import build.wallet.statemachine.recovery.cloud.RecommendTapOtherBitkeyModel
import io.ktor.http.HttpStatusCode
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.FingerprintResetMinFirmwareVersionFeatureFlag
import build.wallet.feature.flags.ReplaceFullWithLiteAccountFeatureFlag
import build.wallet.feature.flags.W3MidUpgradeRecoveryGuardFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareDeviceInfoDaoMock
import build.wallet.firmware.FirmwareMetadata.FirmwareSlot
import build.wallet.firmware.HardwareUnlockInfoServiceFake
import build.wallet.firmware.SecureBootConfig
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.keybox.wallet.AppSpendingWalletProviderMock
import build.wallet.nfc.NfcException
import build.wallet.nfc.transaction.ProvisionAppAuthKeyTransactionProviderFake
import build.wallet.notifications.DeviceTokenManagerMock
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.platform.random.UuidGeneratorFake
import build.wallet.recovery.Recovery.NoActiveRecovery
import build.wallet.recovery.RecoveryStatusServiceMock
import build.wallet.recovery.socrec.PostSocRecTaskRepositoryMock
import build.wallet.recovery.socrec.SocRecChallengeRepositoryMock
import build.wallet.recovery.socrec.SocRecStartedChallengeDaoFake
import build.wallet.relationships.RelationshipsServiceMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.nfc.NfcConfirmableSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcConfirmableSessionUiStateMachineMock
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.recovery.cloud.*
import build.wallet.statemachine.recovery.socrec.challenge.RecoveryChallengeUiProps
import build.wallet.statemachine.recovery.socrec.challenge.RecoveryChallengeUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.testing.shouldBeOk
import build.wallet.time.ClockFake
import build.wallet.wallet.migration.MigrationServiceFake
import build.wallet.wallet.migration.W3UpgradeCheckpointWriterFake
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant

class FullAccountCloudBackupRestorationUiStateMachineImplTests : FunSpec({

  context("parameterized tests for all backup versions") {
    AllFullAccountBackupMocks.forEach { backup ->
      val backupVersion = when (backup) {
        is CloudBackupV2 -> "v2"
        is CloudBackupV3 -> "v3"
        else -> "unknown"
      }

      context("backup $backupVersion") {
        val clock = ClockFake()
        val cloudBackupDao = CloudBackupDaoFake()
        val backupRestorer =
          FullAccountCloudBackupRestorerMock().apply {
            restoration =
              AccountRestorationMock.copy(
                cloudBackupForLocalStorage = backup as CloudBackup
              )
          }
        val deviceTokenManager =
          DeviceTokenManagerMock { name -> turbines.create("$backupVersion-$name") }
        val csekDao = CsekDaoFake()
        val accountAuthorizer =
          AccountAuthenticatorMock { name -> turbines.create("$backupVersion-$name") }
        val authTokensService = AuthTokensServiceFake()
        val appPrivateKeyDao = AppPrivateKeyDaoFake()
        val nfcConfirmableSessionUiStateMachine =
          NfcConfirmableSessionUiStateMachineMock(id = "nfc-confirmable-session-fake")
        val nfcSessionUIStateMachine =
          object : NfcSessionUIStateMachine,
            ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>(
              "sign-auth-challenge-fake"
            ) {}
        val recoveryChallengeUiStateMachineMock =
          object : RecoveryChallengeUiStateMachine,
            ScreenStateMachineMock<RecoveryChallengeUiProps>("recovery-challenge-fake") {}
        val recoveryStatusService =
          RecoveryStatusServiceMock(
            recovery = NoActiveRecovery,
            turbine = { name -> turbines.create("$backupVersion-$name") }
          )
        val relationshipsService =
          RelationshipsServiceMock({ name -> turbines.create("$backupVersion-$name") }, clock)
        val socRecChallengeRepository = SocRecChallengeRepositoryMock()

        val keyboxDao =
          KeyboxDaoMock(turbine = { name -> turbines.create("$backupVersion-$name") }, null, null)
        val appAuthKeyMessageSigner = AppAuthKeyMessageSignerMock()
        val deviceInfoProvider = DeviceInfoProviderMock()

        val eventTracker = EventTrackerMock { name -> turbines.create("$backupVersion-$name") }
        val onExitCalls = turbines.create<Unit>("$backupVersion-on exit calls")
        val onRecoverAppKeyCalls = turbines.create<Unit>("$backupVersion-on recover app key calls")

        val postSocRecTaskRepository = PostSocRecTaskRepositoryMock()
        val socRecPendingChallengeDao = SocRecStartedChallengeDaoFake()

        val fullAccountAuthKeyRotationService =
          FullAccountAuthKeyRotationServiceMock { name -> turbines.create("$backupVersion-$name") }
        val migrationService = MigrationServiceFake()
        val w3UpgradeCheckpointWriter = W3UpgradeCheckpointWriterFake()

        val spendingWallet =
          SpendingWalletMock(turbine = { name -> turbines.create("$backupVersion-$name") })

        val existingFullAccountUiStateMachine = object : ExistingFullAccountUiStateMachine,
          ScreenStateMachineMock<ExistingFullAccountUiProps>("existing-full-account-fake") {}

        val provisionAppAuthKeyTransactionProvider = ProvisionAppAuthKeyTransactionProviderFake()

        val firmwareDeviceInfoDao =
          FirmwareDeviceInfoDaoMock { name -> turbines.create("$backupVersion-$name") }
        val fingerprintResetMinFirmwareVersionFeatureFlag =
          FingerprintResetMinFirmwareVersionFeatureFlag(FeatureFlagDaoFake())

        val hardwareUnlockInfoService = HardwareUnlockInfoServiceFake()
        val appInstallationDao = AppInstallationDaoMock()
        val selectCloudBackupUiStateMachine = object : SelectCloudBackupUiStateMachine,
          ScreenStateMachineMock<SelectCloudBackupUiProps>("select-cloud-backup-fake") {}
        val authF8eClient = AuthF8eClientMock()
        val w3MidUpgradeRecoveryGuardFeatureFlag =
          W3MidUpgradeRecoveryGuardFeatureFlag(FeatureFlagDaoFake())
        val stateMachineActiveDeviceFlagOn =
          FullAccountCloudBackupRestorationUiStateMachineImpl(
            appSpendingWalletProvider = AppSpendingWalletProviderMock(spendingWallet),
            appInstallationDao = appInstallationDao,
            backupRestorer = backupRestorer,
            eventTracker = eventTracker,
            deviceTokenManager = deviceTokenManager,
            csekDao = csekDao,
            accountAuthenticator = accountAuthorizer,
            authTokensService = authTokensService,
            appPrivateKeyDao = appPrivateKeyDao,
            nfcConfirmableSessionUiStateMachine = nfcConfirmableSessionUiStateMachine,
            nfcSessionUIStateMachine = nfcSessionUIStateMachine,
            keyboxDao = keyboxDao,
            recoveryStatusService = recoveryStatusService,
            deviceInfoProvider = deviceInfoProvider,
            uuidGenerator = UuidGeneratorFake(),
            cloudBackupDao = cloudBackupDao,
            recoveryChallengeStateMachine = recoveryChallengeUiStateMachineMock,
            relationshipsService = relationshipsService,
            socRecChallengeRepository = socRecChallengeRepository,
            postSocRecTaskRepository = postSocRecTaskRepository,
            socRecStartedChallengeDao = socRecPendingChallengeDao,
            fullAccountAuthKeyRotationService = fullAccountAuthKeyRotationService,
            migrationService = migrationService,
            w3UpgradeCheckpointWriter = w3UpgradeCheckpointWriter,
            existingFullAccountUiStateMachine = existingFullAccountUiStateMachine,
            replaceFullWithLiteAccountFeatureFlag = ReplaceFullWithLiteAccountFeatureFlag(
              FeatureFlagDaoFake()
            ),
            provisionAppAuthKeyTransactionProvider = provisionAppAuthKeyTransactionProvider,
            fingerprintResetMinFirmwareVersionFeatureFlag = fingerprintResetMinFirmwareVersionFeatureFlag,
            firmwareDeviceInfoDao = firmwareDeviceInfoDao,
            hardwareUnlockInfoService = hardwareUnlockInfoService,
            selectCloudBackupUiStateMachine = selectCloudBackupUiStateMachine,
            authF8eClient = authF8eClient,
            w3MidUpgradeRecoveryGuardFeatureFlag = w3MidUpgradeRecoveryGuardFeatureFlag
          )

        val props = FullAccountCloudBackupRestorationUiProps(
          backups = listOf(backup as CloudBackup),
          onRecoverAppKey = { onRecoverAppKeyCalls.add(Unit) },
          onExit = { onExitCalls.add(Unit) },
          goToLiteAccountCreation = {}
        )

        beforeTest {
          authF8eClient.reset()
          // Guard is opt-in; enable it for tests that exercise the W3 mid-upgrade
          // recovery path. The flag-off behavior is covered by a dedicated test
          // below that disables it.
          w3MidUpgradeRecoveryGuardFeatureFlag.setFlagValue(true)
          authTokensService.reset()
          appAuthKeyMessageSigner.reset()
          keyboxDao.reset()
          recoveryStatusService.reset()
          cloudBackupDao.reset()
          csekDao.reset()
          migrationService.reset()
          w3UpgradeCheckpointWriter.reset()
          provisionAppAuthKeyTransactionProvider.reset()
          firmwareDeviceInfoDao.reset()
          appInstallationDao.reset()
          backupRestorer.restoration = AccountRestorationMock.copy(
            cloudBackupForLocalStorage = backup as CloudBackup
          )
          // Set up firmware device info with version that meets minimum requirement
          firmwareDeviceInfoDao.setDeviceInfo(
            FirmwareDeviceInfo(
              version = "2.0.0", // Version that meets minimum requirement
              serial = "fakeS203serial",
              swType = "dev",
              hwRevision = "evt",
              activeSlot = FirmwareSlot.A,
              batteryCharge = 80.0,
              vCell = 4200,
              avgCurrentMa = 100,
              batteryCycles = 10,
              secureBootConfig = SecureBootConfig.PROD,
              timeRetrieved = 1234567890,
              bioMatchStats = null,
              mcuInfo = emptyList()
            )
          )
        }

        test("happy path - restore from cloud back up") {
          stateMachineActiveDeviceFlagOn.testWithVirtualTime(props) {
            accountAuthorizer.authResults =
              mutableListOf(
                Ok(accountAuthorizer.defaultAuthResult.get()!!.copy(accountId = "account-id")),
                Ok(accountAuthorizer.defaultAuthResult.get()!!.copy(accountId = "account-id"))
              )

            // Cloud back up found model
            awaitBody<FormBodyModel> {
              clickPrimaryButton()
            }
            // Unsealing CSEK (combined with hardware type detection in single NFC session)
            awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Pair<Csek, CloudBackup>>>(
              id = nfcConfirmableSessionUiStateMachine.id
            ) {
              onSuccess(Pair(CsekFake, backup as CloudBackup))
            }

            // activating restored keybox
            awaitBody<LoadingSuccessBodyModel> {
              state.shouldBe(LoadingSuccessBodyModel.State.Loading)
            }

            cloudBackupDao.get("account-id").shouldBeOk(backup as CloudBackup)
            eventTracker.eventCalls.awaitItem().shouldBe(
              TrackedAction(ACTION_APP_CLOUD_RECOVERY_KEY_RECOVERED)
            )

            // Set the global token
            accountAuthorizer.authCalls.awaitItem()
            authTokensService.getTokens(FullAccountId("account-id"), Global).shouldBeOk(
              AccountAuthTokens(
                accessToken = AccessToken("access-token-fake"),
                refreshToken = RefreshToken("refresh-token-fake"),
                accessTokenExpiresAt = Instant.DISTANT_FUTURE
              )
            )

            // Set the recovery token
            accountAuthorizer.authCalls.awaitItem()
            // We want to re-use the global ID, not use the recovery ID
            authTokensService.getTokens(FullAccountId("account-id"), Recovery).shouldBeOk(
              AccountAuthTokens(
                accessToken = AccessToken("access-token-fake"),
                refreshToken = RefreshToken("refresh-token-fake"),
                accessTokenExpiresAt = Instant.DISTANT_FUTURE
              )
            )

            deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
            recoveryStatusService.clearCalls.awaitItem()
            relationshipsService.syncCalls.awaitItem()
            spendingWallet.syncCalls.awaitItem()

            // Provisioning app auth key to hardware
            awaitBodyMock<NfcSessionUIStateMachineProps<Unit>>(
              id = nfcSessionUIStateMachine.id
            ) {
              // Simulate successful provisioning by calling onSuccess on the transaction
              onSuccess(Unit)
            }

            // Saving keybox as active (final loading state)
            awaitBody<LoadingSuccessBodyModel> {
              state.shouldBe(LoadingSuccessBodyModel.State.Loading)
            }

            fullAccountAuthKeyRotationService.recommendKeyRotationCalls.awaitItem()
            keyboxDao.activeKeybox.value
              .shouldBeOk()
              .shouldNotBeNull()
            migrationService.isW3UpgradeInProgressCalls.shouldBe(0)
          }
        }

        test("restore from cloud backup skips provisioning and key rotation when recovery auth fails during W3 upgrade") {
          migrationService.isW3UpgradeInProgressResult = true

          stateMachineActiveDeviceFlagOn.testWithVirtualTime(props) {
            accountAuthorizer.authResults =
              mutableListOf(
                Ok(accountAuthorizer.defaultAuthResult.get()!!.copy(accountId = "account-id")),
                Err(AuthSignatureMismatch)
              )

            awaitBody<FormBodyModel> {
              clickPrimaryButton()
            }
            awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Pair<Csek, CloudBackup>>>(
              id = nfcConfirmableSessionUiStateMachine.id
            ) {
              onSuccess(Pair(CsekFake, backup as CloudBackup))
            }

            awaitBody<LoadingSuccessBodyModel> {
              state.shouldBe(LoadingSuccessBodyModel.State.Loading)
            }

            cloudBackupDao.get("account-id").shouldBeOk(backup as CloudBackup)
            eventTracker.eventCalls.awaitItem().shouldBe(
              TrackedAction(ACTION_APP_CLOUD_RECOVERY_KEY_RECOVERED)
            )

            accountAuthorizer.authCalls.awaitItem()
            accountAuthorizer.authCalls.awaitItem()
            authTokensService.getTokens(FullAccountId("account-id"), Global).shouldBeOk(
              AccountAuthTokens(
                accessToken = AccessToken("access-token-fake"),
                refreshToken = RefreshToken("refresh-token-fake"),
                accessTokenExpiresAt = Instant.DISTANT_FUTURE
              )
            )
            authTokensService.getTokens(FullAccountId("account-id"), Recovery).shouldBeOk(null)
            deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
            recoveryStatusService.clearCalls.awaitItem()
            relationshipsService.syncCalls.awaitItem()
            spendingWallet.syncCalls.awaitItem()

            migrationService.isW3UpgradeInProgressCalls.shouldBe(1)
            w3UpgradeCheckpointWriter.persistCloudRestoreCheckpointCalls.shouldBe(1)
            fullAccountAuthKeyRotationService.recommendKeyRotationCalls.expectNoEvents()
          }
        }

        test("restore from cloud backup still fails when recovery auth fails outside W3 upgrade") {
          stateMachineActiveDeviceFlagOn.testWithVirtualTime(props) {
            accountAuthorizer.authResults =
              mutableListOf(
                Ok(accountAuthorizer.defaultAuthResult.get()!!.copy(accountId = "account-id")),
                Err(AuthSignatureMismatch)
              )

            awaitBody<FormBodyModel> {
              clickPrimaryButton()
            }
            awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Pair<Csek, CloudBackup>>>(
              id = nfcConfirmableSessionUiStateMachine.id
            ) {
              onSuccess(Pair(CsekFake, backup as CloudBackup))
            }

            awaitBody<LoadingSuccessBodyModel> {
              state.shouldBe(LoadingSuccessBodyModel.State.Loading)
            }

            eventTracker.eventCalls.awaitItem().shouldBe(
              TrackedAction(ACTION_APP_CLOUD_RECOVERY_KEY_RECOVERED)
            )

            accountAuthorizer.authCalls.awaitItem()
            accountAuthorizer.authCalls.awaitItem()
            migrationService.isW3UpgradeInProgressCalls.shouldBe(1)
            w3UpgradeCheckpointWriter.persistCloudRestoreCheckpointCalls.shouldBe(0)

            awaitBody<ProblemWithCloudBackupModel> {
              failure.shouldBe(CloudBackupFailure.AppCantPerformPostRestorationSteps)
            }
          }
        }

        test("restore from cloud backup skips provisioning when firmware version is below minimum") {
          // Set firmware version below the minimum requirement
          firmwareDeviceInfoDao.setDeviceInfo(
            FirmwareDeviceInfo(
              version = "0.5.0", // Version below minimum requirement
              serial = "fakeS203serial",
              swType = "dev",
              hwRevision = "evt",
              activeSlot = FirmwareSlot.A,
              batteryCharge = 80.0,
              vCell = 4200,
              avgCurrentMa = 100,
              batteryCycles = 10,
              secureBootConfig = SecureBootConfig.PROD,
              timeRetrieved = 1234567890,
              bioMatchStats = null,
              mcuInfo = emptyList()
            )
          )

          stateMachineActiveDeviceFlagOn.testWithVirtualTime(props) {
            accountAuthorizer.authResults =
              mutableListOf(
                Ok(accountAuthorizer.defaultAuthResult.get()!!.copy(accountId = "account-id")),
                Ok(accountAuthorizer.defaultAuthResult.get()!!.copy(accountId = "account-id"))
              )

            // Cloud back up found model
            awaitBody<FormBodyModel> {
              clickPrimaryButton()
            }
            // Unsealing CSEK (combined with hardware type detection in single NFC session)
            awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Pair<Csek, CloudBackup>>>(
              id = nfcConfirmableSessionUiStateMachine.id
            ) {
              onSuccess(Pair(CsekFake, backup as CloudBackup))
            }

            // activating restored keybox
            awaitBody<LoadingSuccessBodyModel> {
              state.shouldBe(LoadingSuccessBodyModel.State.Loading)
            }

            cloudBackupDao.get("account-id").shouldBeOk(backup as CloudBackup)
            eventTracker.eventCalls.awaitItem().shouldBe(
              TrackedAction(ACTION_APP_CLOUD_RECOVERY_KEY_RECOVERED)
            )

            // Set the global token
            accountAuthorizer.authCalls.awaitItem()
            authTokensService.getTokens(FullAccountId("account-id"), Global).shouldBeOk(
              AccountAuthTokens(
                accessToken = AccessToken("access-token-fake"),
                refreshToken = RefreshToken("refresh-token-fake"),
                accessTokenExpiresAt = Instant.DISTANT_FUTURE
              )
            )

            // Set the recovery token
            accountAuthorizer.authCalls.awaitItem()
            authTokensService.getTokens(FullAccountId("account-id"), Recovery).shouldBeOk(
              AccountAuthTokens(
                accessToken = AccessToken("access-token-fake"),
                refreshToken = RefreshToken("refresh-token-fake"),
                accessTokenExpiresAt = Instant.DISTANT_FUTURE
              )
            )

            deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
            recoveryStatusService.clearCalls.awaitItem()
            relationshipsService.syncCalls.awaitItem()
            spendingWallet.syncCalls.awaitItem()

            // App auth key provisioning should be skipped, no NFC session for provisioning
            // Keybox should be saved directly
            fullAccountAuthKeyRotationService.recommendKeyRotationCalls.awaitItem()
            keyboxDao.activeKeybox.value
              .shouldBeOk()
              .shouldNotBeNull()
          }
        }

        test("user sees problem with cloud backup screen when backup is corrupted and is able to recover") {
          backupRestorer.restoration = null

          stateMachineActiveDeviceFlagOn.test(props) {
            accountAuthorizer.authResults =
              mutableListOf(
                Ok(accountAuthorizer.defaultAuthResult.get()!!.copy(accountId = "account-id")),
                Ok(accountAuthorizer.defaultAuthResult.get()!!.copy(accountId = "account-id"))
              )

            // Cloud back up found model
            awaitBody<FormBodyModel> {
              clickPrimaryButton()
            }
            // Unsealing CSEK (combined with hardware type detection in single NFC session)
            awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Pair<Csek, CloudBackup>>>(
              id = nfcConfirmableSessionUiStateMachine.id
            ) {
              onSuccess(Pair(CsekFake, backup as CloudBackup))
            }

            // activating restored keybox
            awaitBody<LoadingSuccessBodyModel> {
              state.shouldBe(LoadingSuccessBodyModel.State.Loading)
            }

            awaitBody<ProblemWithCloudBackupModel> {
              val listGroup = mainContentList.first() as FormMainContentModel.ListGroup
              listGroup.listGroupModel.items[0].title.shouldBe("Recover your wallet")
              listGroup.listGroupModel.items[0].onClick.shouldNotBeNull().invoke()
            }

            onRecoverAppKeyCalls.awaitItem()
          }
        }

        test("nfc unseal failure surfaces problem with cloud backup screen") {
          stateMachineActiveDeviceFlagOn.test(props) {
            awaitBody<FormBodyModel> {
              clickPrimaryButton()
            }

            awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Pair<Csek, CloudBackup>>>(
              id = nfcConfirmableSessionUiStateMachine.id
            ) {
              onError(NfcException.CommandErrorSealCsekResponseUnsealException())
            }

            awaitBody<ProblemWithCloudBackupModel>()
          }
        }

        test("tries each backup until hardware key succeeds - restores with successful backup") {
          val backup1 = when (backup) {
            is CloudBackupV2 -> (backup as CloudBackupV2).copy(accountId = "account-1") as CloudBackup
            is CloudBackupV3 -> (backup as CloudBackupV3).copy(accountId = "account-1") as CloudBackup
            else -> error("Unsupported backup type")
          }
          val backup2 = when (backup) {
            is CloudBackupV2 -> (backup as CloudBackupV2).copy(accountId = "account-2") as CloudBackup
            is CloudBackupV3 -> (backup as CloudBackupV3).copy(accountId = "account-2") as CloudBackup
            else -> error("Unsupported backup type")
          }
          val backup3 = when (backup) {
            is CloudBackupV2 -> (backup as CloudBackupV2).copy(accountId = "account-3") as CloudBackup
            is CloudBackupV3 -> (backup as CloudBackupV3).copy(accountId = "account-3") as CloudBackup
            else -> error("Unsupported backup type")
          }

          val propsWithMultipleBackups = FullAccountCloudBackupRestorationUiProps(
            backups = listOf(backup1, backup2, backup3),
            onRecoverAppKey = { onRecoverAppKeyCalls.add(Unit) },
            onExit = { onExitCalls.add(Unit) },
            goToLiteAccountCreation = {}
          )

          // Set up restoration to succeed with backup2
          backupRestorer.restoration = AccountRestorationMock.copy(
            cloudBackupForLocalStorage = backup2
          )

          stateMachineActiveDeviceFlagOn.testWithVirtualTime(propsWithMultipleBackups) {
            accountAuthorizer.authResults =
              mutableListOf(
                Ok(accountAuthorizer.defaultAuthResult.get()!!.copy(accountId = "account-2")),
                Ok(accountAuthorizer.defaultAuthResult.get()!!.copy(accountId = "account-2"))
              )

            // Cloud backup found model
            awaitBody<FormBodyModel> {
              clickPrimaryButton()
            }

            // Unsealing CSEK - tries each backup (combined with hardware type detection)
            awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Pair<Csek, CloudBackup>>>(
              id = nfcConfirmableSessionUiStateMachine.id
            ) {
              // Simulate successful unsealing with backup2
              onSuccess(Pair(CsekFake, backup2))
            }

            // Note: When mocking the NFC session, the CSEK is not actually stored in the DAO
            // since we bypass the real unsealing logic. The CSEK storage happens inside the
            // NFC session lambda which is mocked here.

            // activating restored keybox
            awaitBody<LoadingSuccessBodyModel> {
              state.shouldBe(LoadingSuccessBodyModel.State.Loading)
            }

            // Verify restoration used backup2
            cloudBackupDao.get("account-2").shouldBeOk(backup2)

            eventTracker.eventCalls.awaitItem().shouldBe(
              TrackedAction(ACTION_APP_CLOUD_RECOVERY_KEY_RECOVERED)
            )

            accountAuthorizer.authCalls.awaitItem()
            accountAuthorizer.authCalls.awaitItem()
            deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
            recoveryStatusService.clearCalls.awaitItem()
            relationshipsService.syncCalls.awaitItem()
            spendingWallet.syncCalls.awaitItem()

            // Provisioning app auth key to hardware
            awaitBodyMock<NfcSessionUIStateMachineProps<Unit>>(
              id = nfcSessionUIStateMachine.id
            ) {
              onSuccess(Unit)
            }

            // Saving keybox as active
            awaitBody<LoadingSuccessBodyModel> {
              state.shouldBe(LoadingSuccessBodyModel.State.Loading)
            }

            fullAccountAuthKeyRotationService.recommendKeyRotationCalls.awaitItem()
            keyboxDao.activeKeybox.value
              .shouldBeOk()
              .shouldNotBeNull()
          }
        }

        test("does not recommend key rotation when saving keybox fails") {
          keyboxDao.saveKeyboxAsActiveResult = Err(Error("db write failed"))

          stateMachineActiveDeviceFlagOn.testWithVirtualTime(props) {
            accountAuthorizer.authResults =
              mutableListOf(
                Ok(accountAuthorizer.defaultAuthResult.get()!!.copy(accountId = "account-id")),
                Ok(accountAuthorizer.defaultAuthResult.get()!!.copy(accountId = "account-id"))
              )

            // Cloud back up found model
            awaitBody<FormBodyModel> {
              clickPrimaryButton()
            }
            // Unsealing CSEK (combined with hardware type detection in single NFC session)
            awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Pair<Csek, CloudBackup>>>(
              id = nfcConfirmableSessionUiStateMachine.id
            ) {
              onSuccess(Pair(CsekFake, backup as CloudBackup))
            }

            // activating restored keybox
            awaitBody<LoadingSuccessBodyModel> {
              state.shouldBe(LoadingSuccessBodyModel.State.Loading)
            }

            cloudBackupDao.get("account-id").shouldBeOk(backup as CloudBackup)
            eventTracker.eventCalls.awaitItem().shouldBe(
              TrackedAction(ACTION_APP_CLOUD_RECOVERY_KEY_RECOVERED)
            )

            // Set the global token
            accountAuthorizer.authCalls.awaitItem()
            // Set the recovery token
            accountAuthorizer.authCalls.awaitItem()
            deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
            recoveryStatusService.clearCalls.awaitItem()
            relationshipsService.syncCalls.awaitItem()
            spendingWallet.syncCalls.awaitItem()

            // Provisioning app auth key to hardware
            awaitBodyMock<NfcSessionUIStateMachineProps<Unit>>(
              id = nfcSessionUIStateMachine.id
            ) {
              onSuccess(Unit)
            }

            // Saving keybox as active (final loading state)
            awaitBody<LoadingSuccessBodyModel> {
              state.shouldBe(LoadingSuccessBodyModel.State.Loading)
            }

            // Should show failure screen — NOT recommend key rotation
            awaitBody<ProblemWithCloudBackupModel> {
              failure.shouldBe(CloudBackupFailure.AppCantPerformPostRestorationSteps)
            }

            // Verify recommendKeyRotation was never called
            fullAccountAuthKeyRotationService.recommendKeyRotationCalls.expectNoEvents()
          }
        }

        test("W3 unseal failure falls back to ProblemWithCloudBackup when guard flag is off") {
          w3MidUpgradeRecoveryGuardFeatureFlag.setFlagValue(false)
          authF8eClient.initiateAuthenticationResult = Err(
            HttpError.ClientError(HttpResponseMock(HttpStatusCode.NotFound))
          )

          stateMachineActiveDeviceFlagOn.test(props) {
            awaitBody<FormBodyModel> { clickPrimaryButton() }

            awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Pair<Csek, CloudBackup>>>(
              id = nfcConfirmableSessionUiStateMachine.id
            ) {
              val w3Commands = NfcCommandsMock(turbine = { name ->
                app.cash.turbine.Turbine(name = name)
              }).apply {
                deviceInfoResult = FirmwareDeviceInfoMock.copy(hwRevision = "w3a-core-evt")
              }
              @Suppress("SwallowedException")
              try {
                session(NfcSessionFake(), w3Commands)
              } catch (_: Throwable) {
              }
              onError(NfcException.CommandErrorSealCsekResponseUnsealException())
            }

            // With the guard disabled, the W3 unseal failure must route directly
            // to the legacy ProblemWithCloudBackup screen — no probe, no modal.
            awaitBody<ProblemWithCloudBackupModel> {
              failure.shouldBe(CloudBackupFailure.HWCantDecryptCSEK)
            }
          }
        }

        test("W3 unseal failure + recovery pubkey rejected by server shows blocking modal") {
          authF8eClient.initiateAuthenticationResult = Err(
            HttpError.ClientError(HttpResponseMock(HttpStatusCode.NotFound))
          )

          stateMachineActiveDeviceFlagOn.testWithVirtualTime(props) {
            awaitBody<FormBodyModel> { clickPrimaryButton() }

            awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Pair<Csek, CloudBackup>>>(
              id = nfcConfirmableSessionUiStateMachine.id
            ) {
              // Run session with W3 hardware so capturedDeviceInfo is populated,
              // then simulate the "no CSEK matched" signal the real firmware
              // command layer emits when every candidate is exhausted.
              val w3Commands = NfcCommandsMock(turbine = { name ->
                app.cash.turbine.Turbine(name = name)
              }).apply {
                deviceInfoResult = FirmwareDeviceInfoMock.copy(hwRevision = "w3a-core-evt")
              }
              @Suppress("SwallowedException")
              try {
                session(NfcSessionFake(), w3Commands)
              } catch (_: Throwable) {
                // Ignored — we only care that the session populated capturedDeviceInfo.
              }
              onError(NfcException.CommandErrorSealCsekResponseUnsealException())
            }

            awaitUntilBody<RecommendTapOtherBitkeyModel>()
            onRecoverAppKeyCalls.expectNoEvents()
          }
        }

        test("W3 unseal failure + recovery pubkey still valid on server falls back to ProblemWithCloudBackup") {
          authF8eClient.initiateAuthenticationResult = Ok(
            InitiateAuthenticationSuccess(
              username = "account-id",
              accountId = "account-id",
              challenge = "challenge",
              session = "session"
            )
          )

          stateMachineActiveDeviceFlagOn.testWithVirtualTime(props) {
            awaitBody<FormBodyModel> { clickPrimaryButton() }

            awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Pair<Csek, CloudBackup>>>(
              id = nfcConfirmableSessionUiStateMachine.id
            ) {
              val w3Commands = NfcCommandsMock(turbine = { name ->
                app.cash.turbine.Turbine(name = name)
              }).apply {
                deviceInfoResult = FirmwareDeviceInfoMock.copy(hwRevision = "w3a-core-evt")
              }
              @Suppress("SwallowedException")
              try {
                session(NfcSessionFake(), w3Commands)
              } catch (_: Throwable) {
              }
              onError(NfcException.CommandErrorSealCsekResponseUnsealException())
            }

            awaitUntilBody<ProblemWithCloudBackupModel> {
              failure.shouldBe(CloudBackupFailure.HWCantDecryptCSEK)
            }
          }
        }

        test("W3 unseal failure + server network error falls back to ProblemWithCloudBackup") {
          authF8eClient.initiateAuthenticationResult = Err(
            HttpError.NetworkError(Exception("no connectivity"))
          )

          stateMachineActiveDeviceFlagOn.testWithVirtualTime(props) {
            awaitBody<FormBodyModel> { clickPrimaryButton() }

            awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Pair<Csek, CloudBackup>>>(
              id = nfcConfirmableSessionUiStateMachine.id
            ) {
              val w3Commands = NfcCommandsMock(turbine = { name ->
                app.cash.turbine.Turbine(name = name)
              }).apply {
                deviceInfoResult = FirmwareDeviceInfoMock.copy(hwRevision = "w3a-core-evt")
              }
              @Suppress("SwallowedException")
              try {
                session(NfcSessionFake(), w3Commands)
              } catch (_: Throwable) {
              }
              onError(NfcException.CommandErrorSealCsekResponseUnsealException())
            }

            awaitUntilBody<ProblemWithCloudBackupModel> {
              failure.shouldBe(CloudBackupFailure.HWCantDecryptCSEK)
            }
          }
        }

        test("Try a different Bitkey from blocking modal re-enters the NFC unseal flow") {
          authF8eClient.initiateAuthenticationResult = Err(
            HttpError.ClientError(HttpResponseMock(HttpStatusCode.NotFound))
          )

          stateMachineActiveDeviceFlagOn.testWithVirtualTime(props) {
            awaitBody<FormBodyModel> { clickPrimaryButton() }

            awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Pair<Csek, CloudBackup>>>(
              id = nfcConfirmableSessionUiStateMachine.id
            ) {
              val w3Commands = NfcCommandsMock(turbine = { name ->
                app.cash.turbine.Turbine(name = name)
              }).apply {
                deviceInfoResult = FirmwareDeviceInfoMock.copy(hwRevision = "w3a-core-evt")
              }
              @Suppress("SwallowedException")
              try {
                session(NfcSessionFake(), w3Commands)
              } catch (_: Throwable) {
              }
              onError(NfcException.CommandErrorSealCsekResponseUnsealException())
            }

            awaitUntilBody<RecommendTapOtherBitkeyModel> {
              clickPrimaryButton()
            }

            // Back in the NFC unseal flow.
            awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Pair<Csek, CloudBackup>>>(
              id = nfcConfirmableSessionUiStateMachine.id
            )
          }
        }

        test("Canceling NFC after Try a different Bitkey bounces back to blocking modal (not CloudBackupFound)") {
          authF8eClient.initiateAuthenticationResult = Err(
            HttpError.ClientError(HttpResponseMock(HttpStatusCode.NotFound))
          )

          stateMachineActiveDeviceFlagOn.testWithVirtualTime(props) {
            awaitBody<FormBodyModel> { clickPrimaryButton() }

            awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Pair<Csek, CloudBackup>>>(
              id = nfcConfirmableSessionUiStateMachine.id
            ) {
              val w3Commands = NfcCommandsMock(turbine = { name ->
                app.cash.turbine.Turbine(name = name)
              }).apply {
                deviceInfoResult = FirmwareDeviceInfoMock.copy(hwRevision = "w3a-core-evt")
              }
              @Suppress("SwallowedException")
              try {
                session(NfcSessionFake(), w3Commands)
              } catch (_: Throwable) {
              }
              onError(NfcException.CommandErrorSealCsekResponseUnsealException())
            }

            awaitUntilBody<RecommendTapOtherBitkeyModel> {
              clickPrimaryButton()
            }

            // NFC re-opens after tapping "Try a different Bitkey".
            awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Pair<Csek, CloudBackup>>>(
              id = nfcConfirmableSessionUiStateMachine.id
            ) {
              onCancel()
            }

            // Canceling should return to the blocking modal, NOT to CloudBackupFound.
            awaitBody<RecommendTapOtherBitkeyModel>()
            onRecoverAppKeyCalls.expectNoEvents()
            onExitCalls.expectNoEvents()
          }
        }

        test("Back from blocking modal exits via props.onExit (does not fall through to CloudBackupFound)") {
          authF8eClient.initiateAuthenticationResult = Err(
            HttpError.ClientError(HttpResponseMock(HttpStatusCode.NotFound))
          )

          stateMachineActiveDeviceFlagOn.testWithVirtualTime(props) {
            awaitBody<FormBodyModel> { clickPrimaryButton() }

            awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Pair<Csek, CloudBackup>>>(
              id = nfcConfirmableSessionUiStateMachine.id
            ) {
              val w3Commands = NfcCommandsMock(turbine = { name ->
                app.cash.turbine.Turbine(name = name)
              }).apply {
                deviceInfoResult = FirmwareDeviceInfoMock.copy(hwRevision = "w3a-core-evt")
              }
              @Suppress("SwallowedException")
              try {
                session(NfcSessionFake(), w3Commands)
              } catch (_: Throwable) {
              }
              onError(NfcException.CommandErrorSealCsekResponseUnsealException())
            }

            awaitUntilBody<RecommendTapOtherBitkeyModel> {
              onBack()
            }

            onExitCalls.awaitItem()
            onRecoverAppKeyCalls.expectNoEvents()
          }
        }

        test("shows error when no backup can be unsealed with hardware key") {
          val backup1 = when (backup) {
            is CloudBackupV2 -> (backup as CloudBackupV2).copy(accountId = "account-1") as CloudBackup
            is CloudBackupV3 -> (backup as CloudBackupV3).copy(accountId = "account-1") as CloudBackup
            else -> error("Unsupported backup type")
          }
          val backup2 = when (backup) {
            is CloudBackupV2 -> (backup as CloudBackupV2).copy(accountId = "account-2") as CloudBackup
            is CloudBackupV3 -> (backup as CloudBackupV3).copy(accountId = "account-2") as CloudBackup
            else -> error("Unsupported backup type")
          }

          val propsWithMultipleBackups = FullAccountCloudBackupRestorationUiProps(
            backups = listOf(backup1, backup2),
            onRecoverAppKey = { },
            onExit = { },
            goToLiteAccountCreation = {}
          )

          stateMachineActiveDeviceFlagOn.test(propsWithMultipleBackups) {
            awaitBody<FormBodyModel> {
              clickPrimaryButton()
            }

            awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Pair<Csek, CloudBackup>>>(
              id = nfcConfirmableSessionUiStateMachine.id
            ) {
              // Simulate unsealing failure
              onError(NfcException.CommandErrorSealCsekResponseUnsealException())
            }

            awaitBody<ProblemWithCloudBackupModel> {
              failure.shouldBe(CloudBackupFailure.HWCantDecryptCSEK)
            }
          }
        }
      }
    }
  }
})
