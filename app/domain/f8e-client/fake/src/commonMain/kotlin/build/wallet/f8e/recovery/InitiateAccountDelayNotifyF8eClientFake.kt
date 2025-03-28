package build.wallet.f8e.recovery

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.InitiateAccountDelayNotifyErrorCode
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.recovery.InitiateAccountDelayNotifyF8eClient.SuccessfullyInitiated
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlin.time.Duration

class InitiateAccountDelayNotifyF8eClientFake : InitiateAccountDelayNotifyF8eClient {
  override suspend fun initiate(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    lostFactor: PhysicalFactor,
    appGlobalAuthKey: PublicKey<AppGlobalAuthKey>,
    appRecoveryAuthKey: PublicKey<AppRecoveryAuthKey>,
    hwFactorProofOfPossession: HwFactorProofOfPossession?,
    delayPeriod: Duration?,
    hardwareAuthKey: HwAuthPublicKey,
  ): Result<SuccessfullyInitiated, F8eError<InitiateAccountDelayNotifyErrorCode>> {
    return Ok(
      SuccessfullyInitiated(LostHardwareServerRecoveryMock)
    )
  }
}
