package build.wallet.recovery

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.recovery.HardwareKeysForRecovery
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.recovery.LostAppRecoveryInitiator.InitiateDelayNotifyAppRecoveryError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class LostAppRecoveryInitiatorMock(
  turbine: (String) -> Turbine<Any>,
) : LostAppRecoveryInitiator {
  var recoveryResult: Result<Unit, InitiateDelayNotifyAppRecoveryError> = Ok(Unit)
  val initiateCalls = turbine("LostAppRecoveryInitiatorMock initiate calls")

  override suspend fun initiate(
    hardwareKeysForRecovery: HardwareKeysForRecovery,
    newAppKeys: AppKeyBundle,
    appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    fullAccountId: FullAccountId,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, InitiateDelayNotifyAppRecoveryError> {
    initiateCalls += Unit
    return recoveryResult
  }

  fun reset() {
    recoveryResult = Ok(Unit)
  }
}
