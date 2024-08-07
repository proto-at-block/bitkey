package build.wallet.f8e.onboarding.frost

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.SoftwareAccountId
import build.wallet.bitkey.f8e.SoftwareKeysetId
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

interface ActivateSpendingDescriptorF8eClient {
  suspend fun activateSpendingDescriptor(
    f8eEnvironment: F8eEnvironment,
    accountId: SoftwareAccountId,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    keysetId: SoftwareKeysetId,
  ): Result<Unit, NetworkingError>
}
