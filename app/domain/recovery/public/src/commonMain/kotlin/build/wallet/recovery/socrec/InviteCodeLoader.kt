package build.wallet.recovery.socrec

import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.OutgoingInvitation
import com.github.michaelbull.result.Result

interface InviteCodeLoader {
  suspend fun getInviteCode(
    invitation: Invitation,
  ): Result<OutgoingInvitation, InviteCodeLoadError>
}

/**
 * Reasons [InviteCodeLoader.getInviteCode] can fail.
 *
 * Distinguishes the irrecoverable "PAKE data is not on this device" case (which makes the
 * invitation unsharable and unreinvitable) from transient/unexpected errors where retrying or
 * leaving the invite alone is the right call.
 */
sealed class InviteCodeLoadError(message: String, cause: Throwable? = null) :
  Error(message, cause) {
  /**
   * The PAKE secret needed to reconstruct the invite code is not present on this device.
   * The invite can no longer be shared or reinvited — the only recovery path is removal.
   */
  data class MissingPakeData(val relationshipId: String) :
    InviteCodeLoadError("missing pake data for $relationshipId")

  /** Transient/unexpected error loading PAKE data from local storage. */
  data class StorageError(override val cause: Throwable) :
    InviteCodeLoadError("error loading pake data", cause)

  /** Encoding the invite code from PAKE data + invitation code failed. */
  data class EncodingError(override val cause: Throwable) :
    InviteCodeLoadError("error building invite code", cause)
}
