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
import build.wallet.cloud.backup.CloudBackupV2WithFullAccountMock
import build.wallet.cloud.backup.FullAccountCloudBackupRestorerMock
import build.wallet.cloud.backup.csek.Csek
import build.wallet.cloud.backup.csek.CsekDaoFake
import build.wallet.cloud.backup.csek.CsekFake
import build.wallet.cloud.backup.csek.SealedCsekFake
import build.wallet.cloud.backup.local.CloudBackupDaoFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.keybox.wallet.AppSpendingWalletProviderMock
import build.wallet.nfc.NfcException
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
import build.wallet.statemachine.recovery.cloud.FullAccountCloudBackupRestorationUiProps
import build.wallet.statemachine.recovery.cloud.FullAccountCloudBackupRestorationUiStateMachineImpl
import build.wallet.statemachine.recovery.cloud.ProblemWithCloudBackupModel
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
  val clock = ClockFake()
  val cloudBackupDao = CloudBackupDaoFake()
  val backupRestorer =
    FullAccountCloudBackupRestorerMock().apply {
      restoration =
        AccountRestorationMock.copy(
          cloudBackupForLocalStorage = CloudBackupV2WithFullAccountMock
        )
    }
  val deviceTokenManager = DeviceTokenManagerMock(turbines::create)
  val csekDao = CsekDaoFake()
  val accountAuthorizer = AccountAuthenticatorMock(turbines::create)
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
      turbines::create
    )
  val relationshipsService = RelationshipsServiceMock(turbines::create, clock)
  val socRecChallengeRepository = SocRecChallengeRepositoryMock()

  val keyboxDao = KeyboxDaoMock(turbines::create)
  val appAuthKeyMessageSigner = AppAuthKeyMessageSignerMock()
  val deviceInfoProvider = DeviceInfoProviderMock()

  val eventTracker = EventTrackerMock(turbines::create)
  val onExitCalls = turbines.create<Unit>("on exit calls")
  val onRecoverAppKeyCalls = turbines.create<Unit>("on recover app key calls")

  val postSocRecTaskRepository = PostSocRecTaskRepositoryMock()
  val socRecPendingChallengeDao = SocRecStartedChallengeDaoFake()

  val fullAccountAuthKeyRotationService = FullAccountAuthKeyRotationServiceMock(turbines::create)

  val spendingWallet = SpendingWalletMock(turbines::create)

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
      fullAccountAuthKeyRotationService = fullAccountAuthKeyRotationService
    )

  val props = FullAccountCloudBackupRestorationUiProps(
    backup = CloudBackupV2WithFullAccountMock,
    onRecoverAppKey = { onRecoverAppKeyCalls.add(Unit) },
    onExit = { onExitCalls.add(Unit) }
  )

  beforeTest {
    authTokensService.reset()
    appAuthKeyMessageSigner.reset()
    keyboxDao.reset()
    recoveryStatusService.reset()
    cloudBackupDao.reset()
    backupRestorer.restoration = AccountRestorationMock.copy(
      cloudBackupForLocalStorage = CloudBackupV2WithFullAccountMock
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
      awaitBodyMock<NfcSessionUIStateMachineProps<Csek>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(CsekFake)
      }
      csekDao.get(SealedCsekFake).shouldBe(Ok(CsekFake))

      // activating restored keybox
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      cloudBackupDao.get("account-id").shouldBeOk(CloudBackupV2WithFullAccountMock)
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
      awaitBodyMock<NfcSessionUIStateMachineProps<Csek>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(CsekFake)
      }
      csekDao.get(SealedCsekFake).shouldBe(Ok(CsekFake))

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
})
