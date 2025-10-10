package build.wallet.recovery

import bitkey.recovery.InitiateDelayNotifyRecoveryError
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.keybox.AppKeyBundleMock
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class LostHardwareRecoveryServiceFake : LostHardwareRecoveryService {
  var initiateResult: Result<Unit, InitiateDelayNotifyRecoveryError> = Ok(Unit)

  override suspend fun generateNewAppKeys(): Result<AppKeyBundle, Throwable> {
    return Ok(AppKeyBundleMock)
  }

  override suspend fun initiate(
    destinationAppKeyBundle: AppKeyBundle,
    destinationHardwareKeyBundle: HwKeyBundle,
    appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
  ): Result<Unit, InitiateDelayNotifyRecoveryError> {
    return initiateResult
  }

  var cancelResult: Result<Unit, CancelDelayNotifyRecoveryError> = Ok(Unit)

  override suspend fun cancelRecovery(): Result<Unit, CancelDelayNotifyRecoveryError> {
    return cancelResult
  }

  override suspend fun cancelRecoveryWithHwProofOfPossession(
    proofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, CancelDelayNotifyRecoveryError> {
    return cancelResult
  }

  fun reset() {
    initiateResult = Ok(Unit)
    cancelResult = Ok(Unit)
  }
}
