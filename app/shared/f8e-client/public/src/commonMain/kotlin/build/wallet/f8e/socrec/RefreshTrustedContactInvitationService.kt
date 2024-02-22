package build.wallet.f8e.socrec

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.socrec.Invitation
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

interface RefreshTrustedContactInvitationService {
  suspend fun refreshInvitation(
    account: FullAccount,
    hardwareProofOfPossession: HwFactorProofOfPossession,
    relationshipId: String,
  ): Result<Invitation, NetworkingError>
}
