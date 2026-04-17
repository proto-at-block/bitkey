package build.wallet.f8e.onboarding

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingKeysetMock
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class CreateAccountKeysetF8eClientFake : CreateAccountKeysetF8eClient {
  var createKeysetResult: Result<F8eSpendingKeyset, NetworkingError> =
    Ok(F8eSpendingKeysetMock)
  var lastHardwareSpendingKey: HwSpendingPublicKey? = null

  override suspend fun createKeyset(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    hardwareSpendingKey: HwSpendingPublicKey,
    appSpendingKey: AppSpendingPublicKey,
    network: BitcoinNetworkType,
    appAuthKey: PublicKey<AppGlobalAuthKey>?,
  ): Result<F8eSpendingKeyset, NetworkingError> {
    lastHardwareSpendingKey = hardwareSpendingKey
    return createKeysetResult
  }

  fun reset() {
    createKeysetResult = Ok(F8eSpendingKeysetMock)
    lastHardwareSpendingKey = null
  }
}
