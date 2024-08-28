package build.wallet.recovery.socrec

import build.wallet.bitkey.relationships.*
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.AWAITING_VERIFY
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.VERIFIED
import build.wallet.bitkey.socrec.TrustedContactKeyCertificateFake
import build.wallet.bitkey.socrec.TrustedContactKeyCertificateFake2
import build.wallet.compose.collections.immutableListOf
import build.wallet.crypto.PublicKey
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.socrec.SocRecRelationships
import build.wallet.sqldelight.inMemorySqlDriver
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import okio.ByteString.Companion.encodeUtf8

class SocRecRelationshipsDaoImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()

  lateinit var dao: SocRecRelationshipsDao

  val momKey = PublicKey<DelegatedDecryptionKey>("momKey")
  val sisKey = PublicKey<DelegatedDecryptionKey>("sisKey")

  val socRecRelationships =
    SocRecRelationships(
      invitations =
        listOf(
          Invitation(
            relationshipId = "c",
            trustedContactAlias = TrustedContactAlias("Dad"),
            code = "dadToken",
            codeBitLength = 10,
            expiresAt = Instant.DISTANT_FUTURE,
            roles = setOf(TrustedContactRole.SocialRecoveryContact)
          ),
          Invitation(
            relationshipId = "d",
            trustedContactAlias = TrustedContactAlias("Bro"),
            code = "broToken",
            codeBitLength = 10,
            expiresAt = Instant.DISTANT_FUTURE,
            roles = setOf(TrustedContactRole.SocialRecoveryContact)
          ),
          Invitation(
            relationshipId = "f",
            trustedContactAlias = TrustedContactAlias("Gramps"),
            code = "grampsToken",
            codeBitLength = 10,
            expiresAt = Instant.fromEpochMilliseconds(0),
            roles = setOf(TrustedContactRole.SocialRecoveryContact)
          )
        ),
      endorsedTrustedContacts =
        listOf(
          EndorsedTrustedContact(
            relationshipId = "a",
            trustedContactAlias = TrustedContactAlias("Mom"),
            authenticationState = AWAITING_VERIFY,
            keyCertificate = TrustedContactKeyCertificateFake.copy(delegatedDecryptionKey = momKey),
            roles = setOf(TrustedContactRole.SocialRecoveryContact)
          ),
          EndorsedTrustedContact(
            relationshipId = "b",
            trustedContactAlias = TrustedContactAlias("Sis"),
            authenticationState = AWAITING_VERIFY,
            keyCertificate = TrustedContactKeyCertificateFake2.copy(delegatedDecryptionKey = sisKey),
            roles = setOf(TrustedContactRole.SocialRecoveryContact)
          )
        ),
      protectedCustomers =
        immutableListOf(
          ProtectedCustomer(
            relationshipId = "d",
            alias = ProtectedCustomerAlias("Aunt"),
            roles = setOf(TrustedContactRole.SocialRecoveryContact)
          ),
          ProtectedCustomer(
            relationshipId = "e",
            alias = ProtectedCustomerAlias("Uncle"),
            roles = setOf(TrustedContactRole.SocialRecoveryContact)
          )
        ),
      unendorsedTrustedContacts =
        listOf(
          UnendorsedTrustedContact(
            relationshipId = "g",
            trustedContactAlias = TrustedContactAlias("Cousin"),
            enrollmentPakeKey =
              PublicKey("cousinEnrollmentKey"),
            enrollmentKeyConfirmation = "cousinEnrollmentKeyConfirmation".encodeUtf8(),
            sealedDelegatedDecryptionKey = XCiphertext("cipher"),
            roles = setOf(TrustedContactRole.SocialRecoveryContact)
          )
        )
    )

  beforeTest {
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    dao = SocRecRelationshipsDaoImpl(databaseProvider)
  }

  test("get empty social relationships") {
    dao.socRecRelationships().first().shouldBeEqual(
      Ok(SocRecRelationships.EMPTY)
    )
  }

  test("set social relationships") {
    dao.setSocRecRelationships(socRecRelationships)
    dao.socRecRelationships().first().shouldBeEqual(Ok(socRecRelationships))
  }

  test("set social relationships and handle accepted invites") {
    dao.setSocRecRelationships(socRecRelationships)

    val invitationToAccept = socRecRelationships.invitations[0]
    val newKey = PublicKey<DelegatedDecryptionKey>("newKey")
    val newEndorsedTrustedContact =
      EndorsedTrustedContact(
        relationshipId = invitationToAccept.relationshipId,
        trustedContactAlias = invitationToAccept.trustedContactAlias,
        authenticationState = AWAITING_VERIFY,
        keyCertificate = TrustedContactKeyCertificateFake.copy(delegatedDecryptionKey = newKey),
        roles = setOf(TrustedContactRole.SocialRecoveryContact)
      )

    val socRecRelationshipsWithAcceptedInvite =
      socRecRelationships.copy(
        invitations = socRecRelationships.invitations.filter { it != invitationToAccept },
        endorsedTrustedContacts = socRecRelationships.endorsedTrustedContacts + newEndorsedTrustedContact
      )

    dao.setSocRecRelationships(socRecRelationshipsWithAcceptedInvite)
    dao.socRecRelationships().first().shouldBeEqual(
      Ok(socRecRelationshipsWithAcceptedInvite)
    )
  }

  test("set unendorsed trusted contact does not overwrite authentication state") {
    dao.setSocRecRelationships(socRecRelationships)
    dao.socRecRelationships().first().shouldBeEqual(
      Ok(socRecRelationships)
    )

    val endorsedTc =
      socRecRelationships.unendorsedTrustedContacts[0].copy(
        authenticationState = VERIFIED
      )
    val relationshipsWithEndorsedTc =
      socRecRelationships.copy(
        unendorsedTrustedContacts = listOf(endorsedTc)
      )
    dao.setSocRecRelationships(relationshipsWithEndorsedTc)

    // The authentication state should not have changed.
    dao.socRecRelationships().first().shouldBeEqual(
      Ok(socRecRelationships)
    )

    dao.setUnendorsedTrustedContactAuthenticationState(
      endorsedTc.relationshipId,
      VERIFIED
    )
    // The authentication state should have changed after setting the state directly.
    dao.socRecRelationships().first().shouldBeEqual(
      Ok(relationshipsWithEndorsedTc)
    )
  }
})
