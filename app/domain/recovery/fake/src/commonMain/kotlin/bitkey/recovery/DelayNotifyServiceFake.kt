package bitkey.recovery

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.challange.SignedChallenge.HardwareSignedChallenge
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingKeysetMock
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.crypto.PublicKey
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class DelayNotifyServiceFake : DelayNotifyService {
  var cancelResult: Result<Unit, Error> = Ok(Unit)
  var activateSpendingKeysetResult: Result<Unit, Error> = Ok(Unit)
  var createSpendingKeysetResult: Result<F8eSpendingKeyset, Error> = Ok(F8eSpendingKeysetMock)
  var rotateAuthKeysResult: Result<Unit, Error> = Ok(Unit)
  var rotateAuthTokensResult: Result<Unit, Throwable> = Ok(Unit)
  var verifyAuthKeysAfterRotationResult: Result<Unit, Error> = Ok(Unit)
  var regenerateTrustedContactCertificatesResult: Result<Unit, Error> = Ok(Unit)
  var removeTrustedContactsResult: Result<Unit, Error> = Ok(Unit)

  override suspend fun cancelDelayNotify(
    request: DelayNotifyCancellationRequest,
  ): Result<Unit, Error> {
    return cancelResult
  }

  override suspend fun activateSpendingKeyset(
    keyset: F8eSpendingKeyset,
    hardwareProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, Error> {
    return activateSpendingKeysetResult
  }

  override suspend fun createSpendingKeyset(
    hardwareProofOfPossession: HwFactorProofOfPossession,
  ): Result<F8eSpendingKeyset, Error> {
    return createSpendingKeysetResult
  }

  override suspend fun rotateAuthKeys(
    hardwareSignedChallenge: HardwareSignedChallenge,
    sealedCsek: SealedCsek,
    sealedSsek: SealedSsek,
  ): Result<Unit, Error> {
    return rotateAuthKeysResult
  }

  override suspend fun rotateAuthTokens(): Result<Unit, Throwable> {
    return rotateAuthTokensResult
  }

  override suspend fun verifyAuthKeysAfterRotation(): Result<Unit, Error> {
    return verifyAuthKeysAfterRotationResult
  }

  override suspend fun regenerateTrustedContactCertificates(
    oldAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>?,
  ): Result<Unit, Error> {
    return regenerateTrustedContactCertificatesResult
  }

  override suspend fun removeTrustedContacts(): Result<Unit, Error> {
    return removeTrustedContactsResult
  }

  fun reset() {
    cancelResult = Ok(Unit)
    activateSpendingKeysetResult = Ok(Unit)
    createSpendingKeysetResult = Ok(F8eSpendingKeysetMock)
    rotateAuthKeysResult = Ok(Unit)
    rotateAuthTokensResult = Ok(Unit)
    verifyAuthKeysAfterRotationResult = Ok(Unit)
    regenerateTrustedContactCertificatesResult = Ok(Unit)
    removeTrustedContactsResult = Ok(Unit)
  }
}
