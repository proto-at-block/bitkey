package build.wallet.f8e.relationships

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.ProtectedCustomerEnrollmentPakeKey
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.bitkey.socrec.InvitationFake
import build.wallet.crypto.PublicKey
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class RelationshipsF8eClientFake : RelationshipsF8eClient {
  var invitation: Invitation? = InvitationFake

  override suspend fun createRelationship(
    account: FullAccount,
    hardwareProofOfPossession: HwFactorProofOfPossession,
    trustedContactAlias: TrustedContactAlias,
    protectedCustomerEnrollmentPakeKey: PublicKey<ProtectedCustomerEnrollmentPakeKey>,
    roles: Set<TrustedContactRole>,
  ): Result<Invitation, NetworkingError> {
    return when (invitation) {
      null -> Err(HttpError.NetworkError(Exception()))
      else -> Ok(invitation!!)
    }
  }
}
