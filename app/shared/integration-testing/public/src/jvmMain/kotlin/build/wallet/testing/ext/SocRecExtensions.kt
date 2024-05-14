package build.wallet.testing.ext

import build.wallet.bitkey.socrec.IncomingInvitation
import build.wallet.bitkey.socrec.Invitation
import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.bitkey.socrec.TrustedContactAuthenticationState
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.recovery.socrec.syncAndVerifyRelationships
import build.wallet.testing.AppTester
import com.github.michaelbull.result.getOrThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.maps.shouldHaveKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

/**
 * Attempts to do PAKE confirmation on all unendorsed TCs and then endorses and verifies
 * the key certificates for any that succeed.
 *
 * The app must already be logged into the cloud and have an existing cloud backup,
 * or method will fail.
 *
 * @param relationshipId the relationship that must be endorsed and verified, or this method
 *   will fail the test.
 */
suspend fun AppTester.endorseAndVerifyTc(relationshipId: String) =
  withClue("endorse and verify TC") {
    val account = getActiveFullAccount()
    // PAKE confirmation and endorsement
    val unendorsedTcs = app.socRecRelationshipsRepository.syncAndVerifyRelationships(
      account
    ).getOrThrow()
      .unendorsedTrustedContacts
    unendorsedTcs.first { it.recoveryRelationshipId == relationshipId }
    app.trustedContactKeyAuthenticator
      .authenticateAndEndorse(unendorsedTcs, account)
      .getOrThrow()

    // Verify endorsement
    app.socRecRelationshipsRepository.syncAndVerifyRelationships(account).getOrThrow()
      .endorsedTrustedContacts
      .first { it.recoveryRelationshipId == relationshipId }
      .authenticationState
      .shouldBe(TrustedContactAuthenticationState.VERIFIED)

    app.bestEffortFullAccountCloudBackupUploader.createAndUploadCloudBackup(account).getOrThrow()
    app.cloudBackupDao.get(account.accountId.serverId).getOrThrow().shouldNotBeNull()
      .shouldBeTypeOf<CloudBackupV2>()
      .fullAccountFields.shouldNotBeNull()
      .socRecSealedDekMap
      .shouldHaveKey(relationshipId)
  }

/**
 * Full Account creates a Trusted Contact [Invitation].
 */
suspend fun AppTester.createTcInvite(tcName: String): TrustedContactFullInvite {
  val account = getActiveFullAccount()
  val hwPop = getHardwareFactorProofOfPossession(account.keybox)
  val invitation = app.socRecRelationshipsRepository
    .createInvitation(
      account = account,
      trustedContactAlias = TrustedContactAlias(tcName),
      hardwareProofOfPossession = hwPop
    )
    .getOrThrow()
  val pakeData = app.socRecEnrollmentAuthenticationDao
    .getByRelationshipId(invitation.invitation.recoveryRelationshipId)
    .getOrThrow()
    .shouldNotBeNull()
  return TrustedContactFullInvite(
    invitation.inviteCode,
    IncomingInvitation(
      invitation.invitation.recoveryRelationshipId,
      invitation.invitation.code,
      pakeData.protectedCustomerEnrollmentPakeKey.publicKey
    )
  )
}

data class TrustedContactFullInvite(
  val inviteCode: String,
  val invitation: IncomingInvitation,
)
