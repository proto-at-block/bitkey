package build.wallet.statemachine.data.keybox

import build.wallet.auth.AccountAuthTokensMock
import build.wallet.auth.AuthTokenDaoMock
import build.wallet.auth.FullAccountAuthKeyRotationServiceMock
import build.wallet.auth.PendingAuthKeyRotationAttempt
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.keybox.wallet.AppSpendingWalletProviderMock
import build.wallet.money.exchange.ExchangeRateSyncerMock
import build.wallet.recovery.socrec.TrustedContactKeyAuthenticatorMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.*
import build.wallet.statemachine.data.keybox.transactions.FullAccountTransactionsData
import build.wallet.statemachine.data.keybox.transactions.FullAccountTransactionsData.LoadingFullAccountTransactionsData
import build.wallet.statemachine.data.keybox.transactions.FullAccountTransactionsDataProps
import build.wallet.statemachine.data.keybox.transactions.FullAccountTransactionsDataStateMachine
import build.wallet.statemachine.data.keybox.transactions.KeyboxTransactionsDataMock
import build.wallet.statemachine.data.mobilepay.MobilePayData
import build.wallet.statemachine.data.mobilepay.MobilePayData.LoadingMobilePayData
import build.wallet.statemachine.data.mobilepay.MobilePayDataStateMachine
import build.wallet.statemachine.data.mobilepay.MobilePayProps
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.AwaitingNewHardwareData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryDataStateMachine
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryProps
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class HasActiveFullAccountDataStateMachineImplTests : FunSpec({

  val mobilePayDataStateMachine =
    object : MobilePayDataStateMachine, StateMachineMock<MobilePayProps, MobilePayData>(
      LoadingMobilePayData
    ) {}

  val accountAuthTokenDao = AuthTokenDaoMock(turbines::create)

  val fullAccountTransactionsDataStateMachine =
    object : FullAccountTransactionsDataStateMachine,
      StateMachineMock<FullAccountTransactionsDataProps, FullAccountTransactionsData>(
        LoadingFullAccountTransactionsData
      ) {}

  val awaitingNewHardwareData =
    AwaitingNewHardwareData(
      newAppGlobalAuthKey = AppGlobalAuthPublicKeyMock,
      addHardwareKeys = { _, _, _ -> }
    )

  val lostHardwareRecoveryDataStateMachine =
    object : LostHardwareRecoveryDataStateMachine,
      StateMachineMock<LostHardwareRecoveryProps, LostHardwareRecoveryData>(
        awaitingNewHardwareData
      ) {}

  val spendingWallet = SpendingWalletMock(turbines::create, KeyboxMock.activeSpendingKeyset.localId)

  val exchangeRateSyncer = ExchangeRateSyncerMock(turbines::create)

  val trustedContactCloudBackupRefresher = TrustedContactCloudBackupRefresherFake(turbines::create)

  val fullAccountAuthKeyRotationService = FullAccountAuthKeyRotationServiceMock(turbines::create)

  val trustedContactKeyAuthenticator = TrustedContactKeyAuthenticatorMock(turbines::create)

  val stateMachine = HasActiveFullAccountDataStateMachineImpl(
    mobilePayDataStateMachine = mobilePayDataStateMachine,
    fullAccountTransactionsDataStateMachine = fullAccountTransactionsDataStateMachine,
    lostHardwareRecoveryDataStateMachine = lostHardwareRecoveryDataStateMachine,
    appSpendingWalletProvider = AppSpendingWalletProviderMock(spendingWallet),
    exchangeRateSyncer = exchangeRateSyncer,
    trustedContactCloudBackupRefresher = trustedContactCloudBackupRefresher,
    fullAccountAuthKeyRotationService = fullAccountAuthKeyRotationService,
    trustedContactKeyAuthenticator = trustedContactKeyAuthenticator
  )

  beforeTest {
    mobilePayDataStateMachine.reset()
    spendingWallet.reset()
    fullAccountAuthKeyRotationService.reset()
  }

  fun props(account: FullAccount = FullAccountMock) =
    HasActiveFullAccountDataProps(
      account = account,
      hardwareRecovery = null
    )

  test("handle rotate keys") {
    fullAccountAuthKeyRotationService.pendingKeyRotationAttempt.value =
      PendingAuthKeyRotationAttempt.ProposedAttempt
    stateMachine.test(props()) {
      trustedContactCloudBackupRefresher.refreshCloudBackupsWhenNecessaryCalls.awaitItem()
        .shouldBeEqual(FullAccountMock)
      trustedContactKeyAuthenticator.backgroundAuthenticateAndEndorseCalls.awaitItem()
        .shouldBeEqual(FullAccountMock)
      awaitItem().shouldBe(LoadingActiveFullAccountData(FullAccountMock))

      accountAuthTokenDao.getTokensResult = Ok(AccountAuthTokensMock)
      fullAccountTransactionsDataStateMachine.emitModel(KeyboxTransactionsDataMock)

      awaitItem().shouldBeTypeOf<RotatingAuthKeys>().let {
        it.account.shouldBe(FullAccountMock)
        it.pendingAttempt.shouldBe(PendingAuthKeyRotationAttempt.ProposedAttempt)
      }

      exchangeRateSyncer.startSyncerCalls.awaitItem()
    }
  }

  test("load active keybox") {
    stateMachine.test(props()) {
      trustedContactCloudBackupRefresher.refreshCloudBackupsWhenNecessaryCalls.awaitItem()
        .shouldBeEqual(FullAccountMock)
      trustedContactKeyAuthenticator.backgroundAuthenticateAndEndorseCalls.awaitItem()
        .shouldBeEqual(FullAccountMock)
      awaitItem().shouldBe(LoadingActiveFullAccountData(FullAccountMock))

      accountAuthTokenDao.getTokensResult = Ok(AccountAuthTokensMock)
      fullAccountTransactionsDataStateMachine.emitModel(KeyboxTransactionsDataMock)

      awaitItem()
        .shouldBeTypeOf<ActiveFullAccountLoadedData>()
        .let {
          it.account.shouldBe(FullAccountMock)
          it.spendingWallet.shouldBe(spendingWallet)
          it.transactionsData.shouldBe(KeyboxTransactionsDataMock)
          it.mobilePayData.shouldBe(LoadingMobilePayData)
          it.lostHardwareRecoveryData.shouldBe(awaitingNewHardwareData)
        }

      exchangeRateSyncer.startSyncerCalls.awaitItem()
    }
  }
})
