package build.wallet.recovery.socrec

import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.OutgoingInvitation
import com.github.michaelbull.result.Result

interface InviteCodeLoader {
  suspend fun getInviteCode(invitation: Invitation): Result<OutgoingInvitation, Error>
}
