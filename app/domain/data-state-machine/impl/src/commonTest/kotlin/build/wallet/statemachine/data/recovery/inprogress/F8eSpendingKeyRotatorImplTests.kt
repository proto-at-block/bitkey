package build.wallet.statemachine.data.recovery.inprogress

import bitkey.account.AccountConfigServiceFake
import build.wallet.bitkey.f8e.F8eSpendingKeysetMock
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.spending.AppSpendingPublicKeyMock
import build.wallet.bitkey.spending.HwSpendingPublicKeyMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.crypto.PublicKey
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.onboarding.CreateAccountKeysetF8eClientFake
import build.wallet.f8e.onboarding.SetActiveSpendingKeysetF8eClientFake
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.recovery.LocalRecoveryAttemptProgress
import build.wallet.recovery.RecoveryStatusServiceMock
import build.wallet.testing.shouldBeErr
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class F8eSpendingKeyRotatorImplTests : FunSpec({
  val createAccountKeysetF8eClient = CreateAccountKeysetF8eClientFake()
  val setActiveSpendingKeysetF8eClient = SetActiveSpendingKeysetF8eClientFake()
  val accountConfigService = AccountConfigServiceFake()
  val recoveryStatusService = RecoveryStatusServiceMock(turbine = turbines::create)

  val rotator = F8eSpendingKeyRotatorImpl(
    createAccountKeysetF8eClient = createAccountKeysetF8eClient,
    setActiveSpendingKeysetF8eClient = setActiveSpendingKeysetF8eClient,
    accountConfigService = accountConfigService,
    recoveryStatusService = recoveryStatusService
  )

  beforeTest {
    createAccountKeysetF8eClient.reset()
    setActiveSpendingKeysetF8eClient.reset()
    accountConfigService.reset()
    recoveryStatusService.reset()
  }

  test("createSpendingKeyset success") {
    createAccountKeysetF8eClient.createKeysetResult = Ok(F8eSpendingKeysetMock)

    val result = rotator.createSpendingKeyset(
      fullAccountId = FullAccountIdMock,
      appAuthKey = PublicKey("app-auth-key"),
      hardwareProofOfPossession = HwFactorProofOfPossession("hw-proof"),
      appSpendingKey = AppSpendingPublicKeyMock,
      hardwareSpendingKey = HwSpendingPublicKeyMock
    )

    result.shouldBeOk(F8eSpendingKeysetMock)
  }

  test("createSpendingKeyset returns error when create keyset fails") {
    val networkError = NetworkError(Error())
    createAccountKeysetF8eClient.createKeysetResult = Err(networkError)

    val result = rotator.createSpendingKeyset(
      fullAccountId = FullAccountIdMock,
      appAuthKey = PublicKey("app-auth-key"),
      hardwareProofOfPossession = HwFactorProofOfPossession("hw-proof"),
      appSpendingKey = AppSpendingPublicKeyMock,
      hardwareSpendingKey = HwSpendingPublicKeyMock
    )

    result.shouldBeErr(networkError)
  }

  test("activateSpendingKeyset success") {
    setActiveSpendingKeysetF8eClient.setResult = Ok(Unit)

    val result = rotator.activateSpendingKeyset(
      fullAccountId = FullAccountIdMock,
      keyset = F8eSpendingKeysetMock,
      appAuthKey = PublicKey("app-auth-key"),
      hardwareProofOfPossession = HwFactorProofOfPossession("hw-proof")
    )

    result.shouldBeOk(Unit)
    val expectedProgress = LocalRecoveryAttemptProgress.ActivatedSpendingKeys(
      f8eSpendingKeyset = F8eSpendingKeysetMock
    )
    recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem().shouldBe(expectedProgress)
  }

  test("activateSpendingKeyset returns error when f8e call fails") {
    val networkError = NetworkError(Error())
    setActiveSpendingKeysetF8eClient.setResult = Err(networkError)

    val result = rotator.activateSpendingKeyset(
      fullAccountId = FullAccountIdMock,
      keyset = F8eSpendingKeysetMock,
      appAuthKey = PublicKey("app-auth-key"),
      hardwareProofOfPossession = HwFactorProofOfPossession("hw-proof")
    )

    result.shouldBeErr(networkError)
    recoveryStatusService.setLocalRecoveryProgressCalls.expectNoEvents()
  }

  test("activateSpendingKeyset returns error when updating local recovery progress fails") {
    setActiveSpendingKeysetF8eClient.setResult = Ok(Unit)
    val error = Error("uh oh")
    recoveryStatusService.setLocalRecoveryProgressResult = Err(error)

    val result = rotator.activateSpendingKeyset(
      fullAccountId = FullAccountIdMock,
      keyset = F8eSpendingKeysetMock,
      appAuthKey = PublicKey("app-auth-key"),
      hardwareProofOfPossession = HwFactorProofOfPossession("hw-proof")
    )

    result.shouldBeErr(error)
    recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
  }
})
