package build.wallet.recovery

import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.coroutines.turbine.turbines
import build.wallet.recovery.RecoveryAppAuthPublicKeyProviderError.NoRecoveryInProgress
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RecoveryAppAuthPublicKeyProviderImplTests : FunSpec({
  val recoveryDao = RecoveryDaoMock(turbines::create)
  val provider =
    RecoveryAppAuthPublicKeyProviderImpl(
      recoveryDao = recoveryDao
    )

  test("Returns PublicKey<AppGlobalAuthKey> when recovery is StillRecovering") {
    recoveryDao.recovery = StillRecoveringInitiatedRecoveryMock
    provider.getAppPublicKeyForInProgressRecovery(AuthTokenScope.Global)
      .get().shouldBe(StillRecoveringInitiatedRecoveryMock.appGlobalAuthKey)
    provider.getAppPublicKeyForInProgressRecovery(AuthTokenScope.Recovery)
      .get().shouldBe(StillRecoveringInitiatedRecoveryMock.appRecoveryAuthKey)
  }

  test("Returns error when recovery is NoActiveRecovery") {
    recoveryDao.recovery = Recovery.NoActiveRecovery
    val key = provider.getAppPublicKeyForInProgressRecovery(AuthTokenScope.Global)
    key.shouldBeErrOfType<NoRecoveryInProgress>()
  }

  test("Returns error when recovery is Loading") {
    recoveryDao.recovery = Recovery.Loading
    val key = provider.getAppPublicKeyForInProgressRecovery(AuthTokenScope.Global)
    key.shouldBeErrOfType<NoRecoveryInProgress>()
  }

  test("Returns error when recovery is NoLongerRecovering") {
    recoveryDao.recovery = Recovery.NoLongerRecovering(PhysicalFactor.App)
    val key = provider.getAppPublicKeyForInProgressRecovery(AuthTokenScope.Global)
    key.shouldBeErrOfType<NoRecoveryInProgress>()
  }

  test("Returns error when recovery is SomeoneElseIsRecovering") {
    recoveryDao.recovery = Recovery.SomeoneElseIsRecovering(PhysicalFactor.App)
    val key = provider.getAppPublicKeyForInProgressRecovery(AuthTokenScope.Global)
    key.shouldBeErrOfType<NoRecoveryInProgress>()
  }
})
