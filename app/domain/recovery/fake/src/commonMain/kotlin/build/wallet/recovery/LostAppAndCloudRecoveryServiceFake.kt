package build.wallet.recovery

import bitkey.recovery.InitiateDelayNotifyRecoveryError
import build.wallet.auth.AccountAuthTokensMock
import build.wallet.bitkey.auth.HwAuthPublicKeyMock
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.keybox.AppKeyBundleMock
import build.wallet.bitkey.recovery.HardwareKeysForRecovery
import build.wallet.f8e.auth.AuthF8eClient.InitiateAuthenticationSuccess
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.auth.InitiateAuthenticationSuccessMock
import build.wallet.recovery.LostAppAndCloudRecoveryService.CompletedAuth
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class LostAppAndCloudRecoveryServiceFake : LostAppAndCloudRecoveryService {
  var cancelResult: Result<Unit, CancelDelayNotifyRecoveryError> = Ok(Unit)
  var initiateAuthResult: Result<InitiateAuthenticationSuccess, Error> =
    Ok(InitiateAuthenticationSuccessMock)

  override suspend fun initiateAuth(
    hwAuthKey: HwAuthPublicKey,
  ): Result<InitiateAuthenticationSuccess, Error> {
    return initiateAuthResult
  }

  var completeAuthResult: Result<CompletedAuth, Throwable> =
    Ok(
      CompletedAuth.WithDirectKeys(
        accountId = FullAccountIdMock,
        authTokens = AccountAuthTokensMock,
        hwAuthKey = HwAuthPublicKeyMock,
        destinationAppKeys = AppKeyBundleMock,
        existingHwSpendingKeys = emptyList()
      )
    )

  override suspend fun completeAuth(
    accountId: FullAccountId,
    session: String,
    hwAuthKey: HwAuthPublicKey,
    hwSignedChallenge: String,
  ): Result<CompletedAuth, Throwable> {
    return completeAuthResult
  }

  var initiateRecoveryResult: Result<Unit, InitiateDelayNotifyRecoveryError> = Ok(Unit)

  override suspend fun initiateRecovery(
    completedAuth: CompletedAuth,
    hardwareKeysForRecovery: HardwareKeysForRecovery,
  ): Result<Unit, InitiateDelayNotifyRecoveryError> {
    return initiateRecoveryResult
  }

  override suspend fun cancelRecovery(
    accountId: FullAccountId,
    hwProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, CancelDelayNotifyRecoveryError> {
    return cancelResult
  }

  fun reset() {
    cancelResult = Ok(Unit)
    initiateRecoveryResult = Ok(Unit)
    initiateAuthResult = Ok(InitiateAuthenticationSuccessMock)
    completeAuthResult =
      Ok(
        CompletedAuth.WithDirectKeys(
          accountId = FullAccountIdMock,
          authTokens = AccountAuthTokensMock,
          hwAuthKey = HwAuthPublicKeyMock,
          destinationAppKeys = AppKeyBundleMock,
          existingHwSpendingKeys = emptyList()
        )
      )
  }
}
