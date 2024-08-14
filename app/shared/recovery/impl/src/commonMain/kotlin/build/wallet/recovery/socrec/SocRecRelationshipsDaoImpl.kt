package build.wallet.recovery.socrec

import app.cash.sqldelight.coroutines.asFlow
import build.wallet.bitkey.relationships.*
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.socrec.SocRecRelationships
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrThrow
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class SocRecRelationshipsDaoImpl(
  databaseProvider: BitkeyDatabaseProvider,
) : SocRecRelationshipsDao {
  private val database by lazy { databaseProvider.database() }

  override fun socRecRelationships(): Flow<Result<SocRecRelationships, DbError>> {
    return combine(
      database.relationshipsQueries.getSocRecTrustedContacts().asFlow(),
      database.relationshipsQueries.getSocRecTrustedContactInvitations().asFlow(),
      database.relationshipsQueries.getSocRecProtectedCustomers().asFlow(),
      database.relationshipsQueries.getSocRecUnendorsedTrustedContacts().asFlow()
    ) {
        trustedContactsQuery,
        trustedContactInvitationsQuery,
        protectedCustomersQueries,
        unendorsedTrustedContactsQueries,
      ->
      database.awaitTransactionWithResult {
        val invitations =
          trustedContactInvitationsQuery.executeAsList().map { invitation ->
            Invitation(
              relationshipId = invitation.relationshipId,
              trustedContactAlias = invitation.trustedContactAlias,
              code = invitation.token,
              codeBitLength = invitation.tokenBitLength.toInt(),
              expiresAt = invitation.expiresAt,
              roles = invitation.roles
            )
          }
        val unendorsedTrustedContacts =
          unendorsedTrustedContactsQueries.executeAsList().map { contact ->
            UnendorsedTrustedContact(
              relationshipId = contact.relationshipId,
              trustedContactAlias = contact.trustedContactAlias,
              enrollmentPakeKey = contact.enrollmentPakeKey,
              enrollmentKeyConfirmation = contact.enrollmentKeyConfirmation,
              sealedDelegatedDecryptionKey = XCiphertext(contact.sealedDelegatedDecryptionKey),
              authenticationState = contact.authenticationState,
              roles = contact.roles
            )
          }
        val endorsedTrustedContacts =
          trustedContactsQuery.executeAsList().map { trustedContact ->
            EndorsedTrustedContact(
              relationshipId = trustedContact.relationshipId,
              trustedContactAlias = trustedContact.trustedContactAlias,
              authenticationState = trustedContact.authenticationState,
              keyCertificate = EncodedTrustedContactKeyCertificate(trustedContact.certificate)
                .deserialize()
                .getOrThrow(),
              roles = trustedContact.roles
            )
          }
        val protectedProtectedCustomers =
          protectedCustomersQueries.executeAsList().map { customer ->
            ProtectedCustomer(
              relationshipId = customer.relationshipId,
              alias = customer.alias,
              roles = customer.roles
            )
          }

        SocRecRelationships(
          invitations = invitations,
          endorsedTrustedContacts = endorsedTrustedContacts,
          protectedCustomers = protectedProtectedCustomers.toImmutableList(),
          unendorsedTrustedContacts = unendorsedTrustedContacts
        )
      }
    }.distinctUntilChanged()
  }

  override suspend fun setSocRecRelationships(
    socRecRelationships: SocRecRelationships,
  ): Result<Unit, DbError> {
    return database.awaitTransactionWithResult {
      socRecRelationships.protectedCustomers.let { customers ->

        // Reset customers.
        relationshipsQueries.clearSocRecProtectedCustomers()
        customers.forEach { customer ->
          relationshipsQueries.insertSocRecProtectedCustomer(
            relationshipId = customer.relationshipId,
            alias = customer.alias,
            roles = setOf(TrustedContactRole.SocialRecoveryContact)
          )
        }

        // Maintain the existing authentication states for the trusted contacts
        val currentAuthStates =
          relationshipsQueries.getSocRecTrustedContacts()
            .executeAsList()
            .associate { it.relationshipId to it.authenticationState }
        // Reset trusted contacts.
        relationshipsQueries.clearSocRecTrustedContacts()
        socRecRelationships.endorsedTrustedContacts.forEach { tc ->
          relationshipsQueries.insertSocRecTrustedContact(
            relationshipId = tc.relationshipId,
            trustedContactAlias = tc.trustedContactAlias,
            authenticationState = currentAuthStates[tc.relationshipId]
              ?: tc.authenticationState,
            certificate = tc.keyCertificate.encode().getOrThrow().base64,
            roles = setOf(TrustedContactRole.SocialRecoveryContact)
          )
        }

        // Reset invitations
        relationshipsQueries.clearSocRecTrustedContactInvitations()
        socRecRelationships.invitations.forEach { invitation ->
          relationshipsQueries.insertSocRecTrustedContactInvitation(
            relationshipId = invitation.relationshipId,
            trustedContactAlias = invitation.trustedContactAlias,
            token = invitation.code,
            tokenBitLength = invitation.codeBitLength.toLong(),
            expiresAt = invitation.expiresAt,
            roles = setOf(TrustedContactRole.SocialRecoveryContact)
          )
        }

        // Maintain the existing authentication states for the unendorsed trusted contacts
        val currentUnendorsedAuthStates =
          relationshipsQueries.getSocRecUnendorsedTrustedContacts()
            .executeAsList()
            .associate { it.relationshipId to it.authenticationState }
        // Reset unendorsed trusted contacts
        relationshipsQueries.clearSocRecUnendorsedTrustedContacts()
        socRecRelationships.unendorsedTrustedContacts.forEach { contact ->
          relationshipsQueries.insertSocRecUnendorsedTrustedContact(
            relationshipId = contact.relationshipId,
            trustedContactAlias = contact.trustedContactAlias,
            enrollmentPakeKey = contact.enrollmentPakeKey,
            enrollmentKeyConfirmation = contact.enrollmentKeyConfirmation,
            sealedDelegatedDecryptionKey = contact.sealedDelegatedDecryptionKey.value,
            authenticationState =
              currentUnendorsedAuthStates[contact.relationshipId]
                ?: TrustedContactAuthenticationState.UNAUTHENTICATED,
            roles = setOf(TrustedContactRole.SocialRecoveryContact)
          )
        }
      }
    }
  }

  override suspend fun setUnendorsedTrustedContactAuthenticationState(
    recoveryRelationshipId: String,
    authenticationState: TrustedContactAuthenticationState,
  ): Result<Unit, DbError> {
    return database.relationshipsQueries.awaitTransactionWithResult {
      setSocRecUnendorsedTrustedContactAuthenticationState(
        relationshipId = recoveryRelationshipId,
        authenticationState = authenticationState
      )
    }
  }

  override suspend fun setTrustedContactAuthenticationState(
    recoveryRelationshipId: String,
    authenticationState: TrustedContactAuthenticationState,
  ): Result<Unit, DbError> {
    return database.relationshipsQueries.awaitTransactionWithResult {
      setSocRecTrustedContactAuthenticationState(
        relationshipId = recoveryRelationshipId,
        authenticationState = authenticationState
      )
    }
  }

  override suspend fun clear() =
    database.awaitTransactionWithResult {
      relationshipsQueries.clear()
    }
}
