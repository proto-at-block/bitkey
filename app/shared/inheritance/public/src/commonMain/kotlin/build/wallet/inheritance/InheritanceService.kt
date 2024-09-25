package build.wallet.inheritance

import build.wallet.bitkey.relationships.OutgoingInvitation
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Result

/**
 * Service for managing inheritance relationships and executing inheritance operations.
 */
interface InheritanceService {
  /**
   * Creates an invitation for a trusted contact to become a beneficiary
   *
   * @param hardwareProofOfPossession the hardware proof of possession for creating the invitation
   * @param trustedContactAlias the alias of the beneficiary
   *
   * @return result either an [OutgoingInvitation] or an [Error]
   */
  suspend fun createInheritanceInvitation(
    hardwareProofOfPossession: HwFactorProofOfPossession,
    trustedContactAlias: TrustedContactAlias,
  ): Result<OutgoingInvitation, Error>
}
