package build.wallet.statemachine.account.recovery

import bitkey.auth.AccessToken
import bitkey.auth.AccountAuthTokens
import bitkey.auth.AuthTokenScope.Global
import bitkey.auth.AuthTokenScope.Recovery
import bitkey.auth.RefreshToken
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action.ACTION_APP_CLOUD_RECOVERY_KEY_RECOVERED
import build.wallet.auth.AccountAuthenticatorMock
import build.wallet.auth.AppAuthKeyMessageSignerMock
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
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.FingerprintResetMinFirmwareVersionFeatureFlag
import build.wallet.feature.flags.ReplaceFullWithLiteAccountFeatureFlag
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
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.recovery.cloud.*
import build.wallet.statemachine.recovery.socrec.challenge.RecoveryChallengeUiProps
import build.wallet.statemachine.recovery.socrec.challenge.RecoveryChallengeUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.testing.shouldBeOk
import build.wallet.time.ClockFake
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
        val selectCloudBackupUiStateMachine = object : SelectCloudBackupUiStateMachine,
          ScreenStateMachineMock<SelectCloudBackupUiProps>("select-cloud-backup-fake") {}
        val stateMachineActiveDeviceFlagOn =
          FullAccountCloudBackupRestorationUiStateMachineImpl(
            appSpendingWalletProvider = AppSpendingWalletProviderMock(spendingWallet),
            backupRestorer = backupRestorer,
            eventTracker = eventTracker,
            deviceTokenManager = deviceTokenManager,
            csekDao = csekDao,
            accountAuthenticator = accountAuthorizer,
            authTokensService = authTokensService,
            appPrivateKeyDao = appPrivateKeyDao,
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
            existingFullAccountUiStateMachine = existingFullAccountUiStateMachine,
            replaceFullWithLiteAccountFeatureFlag = ReplaceFullWithLiteAccountFeatureFlag(
              FeatureFlagDaoFake()
            ),
            provisionAppAuthKeyTransactionProvider = provisionAppAuthKeyTransactionProvider,
            fingerprintResetMinFirmwareVersionFeatureFlag = fingerprintResetMinFirmwareVersionFeatureFlag,
            firmwareDeviceInfoDao = firmwareDeviceInfoDao,
            hardwareUnlockInfoService = hardwareUnlockInfoService,
            selectCloudBackupUiStateMachine = selectCloudBackupUiStateMachine
          )

        val props = FullAccountCloudBackupRestorationUiProps(
          backups = listOf(backup as CloudBackup),
          onRecoverAppKey = { onRecoverAppKeyCalls.add(Unit) },
          onExit = { onExitCalls.add(Unit) },
          goToLiteAccountCreation = {}
        )

        beforeTest {
          authTokensService.reset()
          appAuthKeyMessageSigner.reset()
          keyboxDao.reset()
          recoveryStatusService.reset()
          cloudBackupDao.reset()
          csekDao.reset()
          provisionAppAuthKeyTransactionProvider.reset()
          firmwareDeviceInfoDao.reset()
          backupRestorer.restoration = AccountRestorationMock.copy(
            cloudBackupForLocalStorage = backup as CloudBackup
          )
          // Set up firmware device info with version that meets minimum requirement
          firmwareDeviceInfoDao.setDeviceInfo(
            FirmwareDeviceInfo(
              version = "2.0.0", // Version that meets minimum requirement
              serial = "fake-serial",
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
            // Unsealing CSEK
            awaitBodyMock<NfcSessionUIStateMachineProps<Pair<Csek, CloudBackup>>>(
              id = nfcSessionUIStateMachine.id
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
          }
        }

        test("restore from cloud backup skips provisioning when firmware version is below minimum") {
          // Set firmware version below the minimum requirement
          firmwareDeviceInfoDao.setDeviceInfo(
            FirmwareDeviceInfo(
              version = "0.5.0", // Version below minimum requirement
              serial = "fake-serial",
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
            // Unsealing CSEK
            awaitBodyMock<NfcSessionUIStateMachineProps<Pair<Csek, CloudBackup>>>(
              id = nfcSessionUIStateMachine.id
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
            // Unsealing CSEK
            awaitBodyMock<NfcSessionUIStateMachineProps<Pair<Csek, CloudBackup>>>(
              id = nfcSessionUIStateMachine.id
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

            awaitBodyMock<NfcSessionUIStateMachineProps<Csek>>(id = nfcSessionUIStateMachine.id) {
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

            // Unsealing CSEK - should try each backup
            awaitBodyMock<NfcSessionUIStateMachineProps<Pair<Csek, CloudBackup>>>(
              id = nfcSessionUIStateMachine.id
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

            awaitBodyMock<NfcSessionUIStateMachineProps<*>>(
              id = nfcSessionUIStateMachine.id
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
