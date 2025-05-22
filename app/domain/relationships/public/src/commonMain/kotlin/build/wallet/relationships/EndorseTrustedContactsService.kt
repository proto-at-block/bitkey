package build.wallet.relationships

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.relationships.EndorsedTrustedContact
import build.wallet.crypto.PublicKey
import com.github.michaelbull.result.Result

/**
 * Authenticates and endorses Recovery Contacts who have accepted their invitation.
 */
interface EndorseTrustedContactsService {
  /**
   * Authenticates, regenerates and endorses RC certificates using new auth keys.
   */
  suspend fun authenticateRegenerateAndEndorse(
    accountId: FullAccountId,
    contacts: List<EndorsedTrustedContact>,
    oldAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>?,
    oldHwAuthKey: HwAuthPublicKey,
    newAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>,
    newAppGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
  ): Result<Unit, Error>
}
