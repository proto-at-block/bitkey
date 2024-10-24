package build.wallet.relationships

import app.cash.sqldelight.coroutines.asFlow
import build.wallet.bitkey.relationships.*
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.relationships.Relationships
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrThrow
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class RelationshipsDaoImpl(
  databaseProvider: BitkeyDatabaseProvider,
) : RelationshipsDao {
  private val database by lazy { databaseProvider.database() }

  override fun relationships(): Flow<Result<Relationships, DbError>> {
    return combine(
      database.relationshipsQueries.getTrustedContacts().asFlow(),
      database.relationshipsQueries.getTrustedContactInvitations().asFlow(),
      database.relationshipsQueries.getProtectedCustomers().asFlow(),
      database.relationshipsQueries.getUnendorsedTrustedContacts().asFlow()
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

        Relationships(
          invitations = invitations,
          endorsedTrustedContacts = endorsedTrustedContacts,
          protectedCustomers = protectedProtectedCustomers.toImmutableList(),
          unendorsedTrustedContacts = unendorsedTrustedContacts
        )
      }
    }.distinctUntilChanged()
  }

  override suspend fun setRelationships(relationships: Relationships): Result<Unit, DbError> {
    return database.awaitTransactionWithResult {
      relationships.protectedCustomers.let { customers ->

        // Reset customers.
        relationshipsQueries.clearProtectedCustomers()
        customers.forEach { customer ->
          relationshipsQueries.insertProtectedCustomer(
            relationshipId = customer.relationshipId,
            alias = customer.alias,
            roles = customer.roles
          )
        }

        // Maintain the existing authentication states for the trusted contacts
        val currentAuthStates =
          relationshipsQueries.getTrustedContacts()
            .executeAsList()
            .associate { it.relationshipId to it.authenticationState }
        // Reset trusted contacts.
        relationshipsQueries.clearTrustedContacts()
        relationships.endorsedTrustedContacts.forEach { tc ->
          relationshipsQueries.insertTrustedContact(
            relationshipId = tc.relationshipId,
            trustedContactAlias = tc.trustedContactAlias,
            authenticationState = currentAuthStates[tc.relationshipId]
              ?: tc.authenticationState,
            certificate = tc.keyCertificate.encode().getOrThrow().base64,
            roles = tc.roles
          )
        }

        // Reset invitations
        relationshipsQueries.clearTrustedContactInvitations()
        relationships.invitations.forEach { invitation ->
          relationshipsQueries.insertTrustedContactInvitation(
            relationshipId = invitation.relationshipId,
            trustedContactAlias = invitation.trustedContactAlias,
            token = invitation.code,
            tokenBitLength = invitation.codeBitLength.toLong(),
            expiresAt = invitation.expiresAt,
            roles = invitation.roles
          )
        }

        // Maintain the existing authentication states for the unendorsed trusted contacts
        val currentUnendorsedAuthStates =
          relationshipsQueries.getUnendorsedTrustedContacts()
            .executeAsList()
            .associate { it.relationshipId to it.authenticationState }
        // Reset unendorsed trusted contacts
        relationshipsQueries.clearUnendorsedTrustedContacts()
        relationships.unendorsedTrustedContacts.forEach { contact ->
          relationshipsQueries.insertUnendorsedTrustedContact(
            relationshipId = contact.relationshipId,
            trustedContactAlias = contact.trustedContactAlias,
            enrollmentPakeKey = contact.enrollmentPakeKey,
            enrollmentKeyConfirmation = contact.enrollmentKeyConfirmation,
            sealedDelegatedDecryptionKey = contact.sealedDelegatedDecryptionKey.value,
            authenticationState =
              currentUnendorsedAuthStates[contact.relationshipId]
                ?: TrustedContactAuthenticationState.UNAUTHENTICATED,
            roles = contact.roles
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
      setUnendorsedTrustedContactAuthenticationState(
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
      setTrustedContactAuthenticationState(
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
