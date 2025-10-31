package build.wallet.f8e.onboarding

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingKeysetPrivateWalletMock
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class CreateAccountKeysetV2F8eClientFake : CreateAccountKeysetV2F8eClient {
  var createKeysetResult: Result<F8eSpendingKeyset, NetworkingError> =
    Ok(F8eSpendingKeysetPrivateWalletMock)

  override suspend fun createKeyset(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    hardwareSpendingKey: HwSpendingPublicKey,
    appSpendingKey: AppSpendingPublicKey,
    network: BitcoinNetworkType,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    hardwareProofOfPossession: HwFactorProofOfPossession,
  ): Result<F8eSpendingKeyset, NetworkingError> {
    return createKeysetResult
  }

  fun reset() {
    createKeysetResult = Ok(F8eSpendingKeysetPrivateWalletMock)
  }
}
