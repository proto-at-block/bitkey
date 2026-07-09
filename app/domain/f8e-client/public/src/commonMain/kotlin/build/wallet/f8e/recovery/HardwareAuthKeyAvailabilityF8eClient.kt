package build.wallet.f8e.recovery

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.HardwareAuthKeyAvailabilityErrorCode
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Checks whether a hardware auth key can be used by the current account.
 *
 * This is a read-only server check used by W3 upgrade after the new hardware is paired, but before
 * the app creates a new keyset or starts auth-key rotation. The check does not claim the key or
 * mutate account or recovery state.
 *
 * All [HardwareAuthKeyAvailabilityStatus] values mean the key is usable by the current account. If
 * the key is linked to another account, or reserved by another pending recovery, F8e returns
 * [HardwareAuthKeyAvailabilityErrorCode.HW_AUTH_PUBKEY_IN_USE] instead of a status.
 */
interface HardwareAuthKeyAvailabilityF8eClient {
  /**
   * Returns the server's availability status for [hardwareAuthPublicKey], scoped to [fullAccountId].
   */
  suspend fun checkAvailability(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    hardwareAuthPublicKey: HwAuthPublicKey,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
  ): Result<HardwareAuthKeyAvailabilityStatus, F8eError<HardwareAuthKeyAvailabilityErrorCode>>
}

/**
 * Availability result for a hardware auth key relative to the requesting account.
 */
@Serializable
enum class HardwareAuthKeyAvailabilityStatus {
  /** The key is not linked to any account or pending recovery known to F8e. */
  @SerialName("available")
  Available,

  /** The key is already claimed by the requesting account, but is not its active hardware auth key. */
  @SerialName("claimed_by_current_account")
  ClaimedByCurrentAccount,

  /** The key is already the active hardware auth key on the requesting account. */
  @SerialName("active_on_current_account")
  ActiveOnCurrentAccount,
}
