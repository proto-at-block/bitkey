package build.wallet.statemachine.account.recovery

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action.ACTION_APP_CLOUD_RECOVERY_KEY_RECOVERED
import build.wallet.auth.*
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
import build.wallet.debug.DebugOptionsFake
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.keybox.wallet.AppSpendingWalletProviderMock
import build.wallet.notifications.DeviceTokenManagerMock
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.platform.random.UuidGeneratorFake
import build.wallet.recovery.Recovery.NoActiveRecovery
import build.wallet.recovery.RecoverySyncerMock
import build.wallet.recovery.socrec.PostSocRecTaskRepositoryMock
import build.wallet.recovery.socrec.SocRecChallengeRepositoryMock
import build.wallet.recovery.socrec.SocRecStartedChallengeDaoFake
import build.wallet.relationships.RelationshipsServiceMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.recovery.cloud.FullAccountCloudBackupRestorationUiProps
import build.wallet.statemachine.recovery.cloud.FullAccountCloudBackupRestorationUiStateMachineImpl
import build.wallet.statemachine.recovery.socrec.challenge.RecoveryChallengeUiProps
import build.wallet.statemachine.recovery.socrec.challenge.RecoveryChallengeUiStateMachine
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class FullAccountCloudBackupRestorationUiStateMachineImplTests : FunSpec({
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
  val authTokenDao = AuthTokenDaoMock(turbines::create)
  val appPrivateKeyDao = AppPrivateKeyDaoFake()
  val nfcSessionUIStateMachine =
    object : NfcSessionUIStateMachine,
      ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>(
        "sign-auth-challenge-fake"
      ) {}
  val recoveryChallengeUiStateMachineMock =
    object : RecoveryChallengeUiStateMachine,
      ScreenStateMachineMock<RecoveryChallengeUiProps>("recovery-challenge-fake") {}
  val recoverySyncer =
    RecoverySyncerMock(
      recovery = NoActiveRecovery,
      turbines::create
    )
  val relationshipsService = RelationshipsServiceMock(turbines::create)
  val socRecChallengeRepository = SocRecChallengeRepositoryMock()

  val keyboxDao = KeyboxDaoMock(turbines::create)
  val appAuthKeyMessageSigner = AppAuthKeyMessageSignerMock()
  val deviceInfoProvider = DeviceInfoProviderMock()

  val eventTracker = EventTrackerMock(turbines::create)
  val onExitCalls = turbines.create<Unit>("on exit calls")

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
      authTokenDao = authTokenDao,
      appPrivateKeyDao = appPrivateKeyDao,
      nfcSessionUIStateMachine = nfcSessionUIStateMachine,
      keyboxDao = keyboxDao,
      recoverySyncer = recoverySyncer,
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
    debugOptions = DebugOptionsFake,
    backup = CloudBackupV2WithFullAccountMock,
    onExit = { onExitCalls.add(Unit) }
  )

  beforeTest {
    appAuthKeyMessageSigner.reset()
    keyboxDao.reset()
    authTokenDao.reset()
    recoverySyncer.reset()
    cloudBackupDao.reset()
  }

  test("happy path - restore from cloud back up") {
    stateMachineActiveDeviceFlagOn.test(props) {
      accountAuthorizer.authResults =
        mutableListOf(
          Ok(accountAuthorizer.defaultAuthResult.get()!!.copy(accountId = "account-id")),
          Ok(accountAuthorizer.defaultAuthResult.get()!!.copy(accountId = "account-id"))
        )

      // Cloud back up found model
      awaitScreenWithBody<FormBodyModel> {
        clickPrimaryButton()
      }
      // Unsealing CSEK
      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<Csek>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(CsekFake)
      }
      csekDao.get(SealedCsekFake).shouldBe(Ok(CsekFake))

      // activating restored keybox
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      cloudBackupDao.get("account-id").shouldBeOk(CloudBackupV2WithFullAccountMock)
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_CLOUD_RECOVERY_KEY_RECOVERED)
      )

      // Set the global token
      accountAuthorizer.authCalls.awaitItem()
      authTokenDao.setTokensCalls.awaitItem().shouldBe(
        AuthTokenDaoMock.SetTokensParams(
          accountId = FullAccountId("account-id"),
          tokens = AccountAuthTokens(
            accessToken = AccessToken("access-token-fake"),
            refreshToken = RefreshToken("refresh-token-fake")
          ),
          scope = AuthTokenScope.Global
        )
      )

      // Set the recovery token
      accountAuthorizer.authCalls.awaitItem()
      authTokenDao.setTokensCalls.awaitItem().shouldBe(
        AuthTokenDaoMock.SetTokensParams(
          // We want to re-use the global ID, not use the recovery ID
          accountId = FullAccountId("account-id"),
          tokens = AccountAuthTokens(
            accessToken = AccessToken("access-token-fake"),
            refreshToken = RefreshToken("refresh-token-fake")
          ),
          scope = AuthTokenScope.Recovery
        )
      )

      deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
      recoverySyncer.clearCalls.awaitItem()
      relationshipsService.syncCalls.awaitItem()
      spendingWallet.syncCalls.awaitItem()

      fullAccountAuthKeyRotationService.recommendKeyRotationCalls.awaitItem()
      keyboxDao.activeKeybox.value
        .shouldBeOk()
        .shouldNotBeNull()
    }
  }
})
