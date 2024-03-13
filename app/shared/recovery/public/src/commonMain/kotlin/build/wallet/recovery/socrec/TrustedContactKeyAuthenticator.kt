package build.wallet.recovery.socrec

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.socrec.TrustedContact
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result
import kotlinx.coroutines.CoroutineScope

/**
 * Authenticates and endorses Trusted Contacts who have accepted their invitation.
 */
interface TrustedContactKeyAuthenticator {
  fun backgroundAuthenticateAndEndorse(
    scope: CoroutineScope,
    account: FullAccount,
  )

  /**
   * Authenticates, regenerates and endorses TC certificates using new auth keys.
   */
  suspend fun authenticateRegenerateAndEndorse(
    accountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    contacts: List<TrustedContact>,
    oldAppGlobalAuthKey: AppGlobalAuthPublicKey?,
    oldHwAuthKey: HwAuthPublicKey,
    newAppGlobalAuthKey: AppGlobalAuthPublicKey,
    newAppGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
  ): Result<Unit, Error>
}
