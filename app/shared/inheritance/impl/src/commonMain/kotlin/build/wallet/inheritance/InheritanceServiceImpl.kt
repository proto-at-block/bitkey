package build.wallet.inheritance

import build.wallet.account.AccountService
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.relationships.OutgoingInvitation
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.ensure
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.relationships.RelationshipsF8eClient
import build.wallet.recovery.socrec.InviteCodeParts.Schema
import build.wallet.recovery.socrec.SocRecCrypto
import build.wallet.recovery.socrec.SocRecEnrollmentAuthenticationDao
import build.wallet.recovery.socrec.SocialRecoveryCodeBuilder
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.flow.first
import kotlin.random.Random

// TODO W-9128 rename "soc rec" classes so they are agnostic to the relationship type
class InheritanceServiceImpl(
  private val socRecCrypto: SocRecCrypto,
  private val accountService: AccountService,
  private val relationshipsF8eClient: RelationshipsF8eClient,
  private val socialRecoveryCodeBuilder: SocialRecoveryCodeBuilder,
  private val socRecEnrollmentAuthenticationDao: SocRecEnrollmentAuthenticationDao,
) : InheritanceService {
  override suspend fun createInheritanceInvitation(
    hardwareProofOfPossession: HwFactorProofOfPossession,
    trustedContactAlias: TrustedContactAlias,
  ): Result<OutgoingInvitation, Error> =
    coroutineBinding {
      val enrollmentPakeCode = Schema.maskPakeData(Random.nextBytes(Schema.pakeByteArraySize()))
      val protectedCustomerEnrollmentPakeKey =
        socRecCrypto.generateProtectedCustomerEnrollmentPakeKey(enrollmentPakeCode)
          .mapError { Error("Error creating pake key: ${it.message}", it) }
          .bind()

      val account = accountService.activeAccount().first()
      ensure(account is FullAccount) { Error("No active full account present.") }

      val invitation = relationshipsF8eClient.createRelationship(
        account = account,
        hardwareProofOfPossession = hardwareProofOfPossession,
        trustedContactAlias = trustedContactAlias,
        protectedCustomerEnrollmentPakeKey = protectedCustomerEnrollmentPakeKey.publicKey,
        roles = setOf(TrustedContactRole.Beneficiary)
      ).mapError { Error("Error creating relationship with f8e: ${it.message}", it) }
        .bind()

      socRecEnrollmentAuthenticationDao.insert(
        invitation.relationshipId,
        protectedCustomerEnrollmentPakeKey,
        enrollmentPakeCode
      ).mapError { Error("Error persisting relationship in db: ${it.message}", it) }
        .bind()

      val inviteCode = socialRecoveryCodeBuilder.buildInviteCode(
        serverPart = invitation.code,
        serverBits = invitation.codeBitLength,
        pakePart = enrollmentPakeCode
      ).mapError { Error("Error building invite code: ${it.message}", it) }
        .bind()

      OutgoingInvitation(
        invitation = invitation,
        inviteCode = inviteCode
      )
    }
}
