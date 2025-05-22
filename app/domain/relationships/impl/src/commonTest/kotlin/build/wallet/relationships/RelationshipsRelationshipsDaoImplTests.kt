package build.wallet.relationships

import bitkey.relationships.Relationships
import build.wallet.bitkey.relationships.*
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.AWAITING_VERIFY
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.VERIFIED
import build.wallet.compose.collections.immutableListOf
import build.wallet.crypto.PublicKey
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.encrypt.XCiphertext
import build.wallet.sqldelight.inMemorySqlDriver
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import okio.ByteString.Companion.encodeUtf8

class RelationshipsRelationshipsDaoImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()

  lateinit var dao: RelationshipsDao

  val momKey = PublicKey<DelegatedDecryptionKey>("momKey")
  val sisKey = PublicKey<DelegatedDecryptionKey>("sisKey")

  val relationships =
    Relationships(
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
    dao = RelationshipsDaoImpl(databaseProvider)
  }

  test("get empty social relationships") {
    dao.relationships().first().shouldBeEqual(
      Ok(Relationships.EMPTY)
    )
  }

  test("set social relationships") {
    dao.setRelationships(relationships)
    dao.relationships().first().shouldBeEqual(Ok(relationships))
  }

  test("set social relationships and handle accepted invites") {
    dao.setRelationships(relationships)

    val invitationToAccept = relationships.invitations[0]
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
      relationships.copy(
        invitations = relationships.invitations.filter { it != invitationToAccept },
        endorsedTrustedContacts = relationships.endorsedTrustedContacts + newEndorsedTrustedContact
      )

    dao.setRelationships(socRecRelationshipsWithAcceptedInvite)
    dao.relationships().first().shouldBeEqual(
      Ok(socRecRelationshipsWithAcceptedInvite)
    )
  }

  test("set unendorsed Recovery Contact does not overwrite authentication state") {
    dao.setRelationships(relationships)
    dao.relationships().first().shouldBeEqual(
      Ok(relationships)
    )

    val endorsedTc =
      relationships.unendorsedTrustedContacts[0].copy(
        authenticationState = VERIFIED
      )
    val relationshipsWithEndorsedTc =
      relationships.copy(
        unendorsedTrustedContacts = listOf(endorsedTc)
      )
    dao.setRelationships(relationshipsWithEndorsedTc)

    // The authentication state should not have changed.
    dao.relationships().first().shouldBeEqual(
      Ok(relationships)
    )

    dao.setUnendorsedTrustedContactAuthenticationState(
      endorsedTc.relationshipId,
      VERIFIED
    )
    // The authentication state should have changed after setting the state directly.
    dao.relationships().first().shouldBeEqual(
      Ok(relationshipsWithEndorsedTc)
    )
  }
})
