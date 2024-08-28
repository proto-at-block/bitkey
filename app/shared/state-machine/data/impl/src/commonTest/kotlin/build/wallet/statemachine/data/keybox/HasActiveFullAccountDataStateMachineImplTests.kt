package build.wallet.statemachine.data.keybox

import build.wallet.auth.AccountAuthTokensMock
import build.wallet.auth.AuthTokenDaoMock
import build.wallet.auth.FullAccountAuthKeyRotationServiceMock
import build.wallet.auth.PendingAuthKeyRotationAttempt
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.RotatingAuthKeys
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

  val accountAuthTokenDao = AuthTokenDaoMock(turbines::create)

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

  val trustedContactCloudBackupRefresher = TrustedContactCloudBackupRefresherFake(turbines::create)

  val fullAccountAuthKeyRotationService = FullAccountAuthKeyRotationServiceMock(turbines::create)

  val stateMachine = HasActiveFullAccountDataStateMachineImpl(
    lostHardwareRecoveryDataStateMachine = lostHardwareRecoveryDataStateMachine,
    trustedContactCloudBackupRefresher = trustedContactCloudBackupRefresher,
    fullAccountAuthKeyRotationService = fullAccountAuthKeyRotationService
  )

  beforeTest {
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
      awaitItem()
        .shouldBeTypeOf<ActiveFullAccountLoadedData>()
        .let {
          it.account.shouldBe(FullAccountMock)
          it.lostHardwareRecoveryData.shouldBe(awaitingNewHardwareData)
        }

      accountAuthTokenDao.getTokensResult = Ok(AccountAuthTokensMock)

      awaitItem().shouldBeTypeOf<RotatingAuthKeys>().let {
        it.account.shouldBe(FullAccountMock)
        it.pendingAttempt.shouldBe(PendingAuthKeyRotationAttempt.ProposedAttempt)
      }
    }
  }

  test("load active keybox") {
    stateMachine.test(props()) {
      trustedContactCloudBackupRefresher.refreshCloudBackupsWhenNecessaryCalls.awaitItem()
        .shouldBeEqual(FullAccountMock)

      accountAuthTokenDao.getTokensResult = Ok(AccountAuthTokensMock)

      awaitItem()
        .shouldBeTypeOf<ActiveFullAccountLoadedData>()
        .let {
          it.account.shouldBe(FullAccountMock)
          it.lostHardwareRecoveryData.shouldBe(awaitingNewHardwareData)
        }
    }
  }
})
