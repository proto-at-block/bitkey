package build.wallet.recovery

import bitkey.account.HardwareType
import bitkey.recovery.InitiateDelayNotifyRecoveryError
import build.wallet.auth.AccountAuthTokensMock
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.auth.HwAuthPublicKeyMock
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keybox.AppKeyBundleMock
import build.wallet.bitkey.recovery.HardwareKeysForRecovery
import build.wallet.f8e.auth.AuthF8eClient.InitiateAuthenticationSuccess
import build.wallet.f8e.auth.InitiateAuthenticationSuccessMock
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.platform.random.UuidGenerator
import build.wallet.recovery.LostAppAndCloudRecoveryService.CompletedAuth
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class LostAppAndCloudRecoveryServiceFake(
  private val uuidGenerator: UuidGenerator = UuidGenerator { "fake-uuid" },
) : LostAppAndCloudRecoveryService {
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
        bitcoinNetworkType = BitcoinNetworkType.BITCOIN,
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

  override fun buildHardwareKeys(
    proof: PrivilegedActionProof,
    hardwareAuthKey: HwAuthPublicKey,
    spendingKey: HwSpendingPublicKey,
    appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    bitcoinNetworkType: BitcoinNetworkType,
    hardwareType: HardwareType,
    spendingKeyProof: build.wallet.bitkey.hardware.HwSpendingKeyProof?,
  ): HardwareKeysForRecovery =
    HardwareKeysForRecovery(
      proof = proof,
      newAppGlobalAuthKeyHwSignature = appGlobalAuthKeyHwSignature,
      newKeyBundle = HwKeyBundle(
        localId = uuidGenerator.random(),
        spendingKey = spendingKey,
        authKey = hardwareAuthKey,
        networkType = bitcoinNetworkType
      ),
      hardwareType = hardwareType
    )

  override suspend fun cancelRecovery(
    accountId: FullAccountId,
    proof: PrivilegedActionProof,
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
          bitcoinNetworkType = BitcoinNetworkType.BITCOIN,
          existingHwSpendingKeys = emptyList()
        )
      )
  }
}
