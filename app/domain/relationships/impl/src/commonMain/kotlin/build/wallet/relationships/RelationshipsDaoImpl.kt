package build.wallet.relationships

import app.cash.sqldelight.coroutines.asFlow
import bitkey.relationships.Relationships
import build.wallet.bitkey.relationships.*
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.encrypt.XCiphertext
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrThrow
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.*

@BitkeyInject(AppScope::class)
class RelationshipsDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : RelationshipsDao {
  override fun relationships(): Flow<Result<Relationships, DbError>> {
    return flow {
      val database = databaseProvider.database()
      combine(
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
      }
        .distinctUntilChanged()
        .collect(::emit)
    }
  }

  override suspend fun setRelationships(relationships: Relationships): Result<Unit, DbError> {
    return databaseProvider.database().awaitTransactionWithResult {
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

        // Maintain the existing authentication states for the Recovery Contacts
        val currentAuthStates =
          relationshipsQueries.getTrustedContacts()
            .executeAsList()
            .associate { it.relationshipId to it.authenticationState }
        // Reset Recovery Contacts.
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

        // Maintain the existing authentication states for the unendorsed Recovery Contacts
        val currentUnendorsedAuthStates =
          relationshipsQueries.getUnendorsedTrustedContacts()
            .executeAsList()
            .associate { it.relationshipId to it.authenticationState }
        // Reset unendorsed Recovery Contacts
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
    return databaseProvider.database()
      .relationshipsQueries
      .awaitTransactionWithResult {
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
    return databaseProvider.database()
      .relationshipsQueries
      .awaitTransactionWithResult {
        setTrustedContactAuthenticationState(
          relationshipId = recoveryRelationshipId,
          authenticationState = authenticationState
        )
      }
  }

  override suspend fun clear() =
    databaseProvider.database()
      .awaitTransactionWithResult {
        relationshipsQueries.clear()
      }
}
