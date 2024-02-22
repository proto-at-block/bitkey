package build.wallet.recovery.socrec

import app.cash.sqldelight.coroutines.asFlow
import build.wallet.bitkey.socrec.Invitation
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.TrustedContact
import build.wallet.bitkey.socrec.TrustedContactAuthenticationState
import build.wallet.bitkey.socrec.UnendorsedTrustedContact
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.f8e.socrec.SocRecRelationships
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class SocRecRelationshipsDaoImpl(
  databaseProvider: BitkeyDatabaseProvider,
) : SocRecRelationshipsDao {
  private val database by lazy { databaseProvider.database() }

  override fun socRecRelationships(): Flow<Result<SocRecRelationships, DbError>> {
    return database.socRecRelationshipsQueries.let { queries ->
      combine(
        queries.getSocRecTrustedContacts().asFlow(),
        queries.getSocRecTrustedContactInvitations().asFlow(),
        queries.getSocRecProtectedCustomers().asFlow(),
        queries.getSocRecUnendorsedTrustedContacts().asFlow()
      ) {
          trustedContactsQuery,
          trustedContactInvitationsQuery,
          protectedCustomersQueries,
          unendorsedTrustedContactsQueries,
        ->
        queries.awaitTransactionWithResult {
          val invitations =
            trustedContactInvitationsQuery.executeAsList().map { invitation ->
              Invitation(
                recoveryRelationshipId = invitation.recoveryRelationshipId,
                trustedContactAlias = invitation.trustedContactAlias,
                token = invitation.token,
                expiresAt = invitation.expiresAt
              )
            }
          val unendorsedTrustedContacts =
            unendorsedTrustedContactsQueries.executeAsList().map { contact ->
              UnendorsedTrustedContact(
                recoveryRelationshipId = contact.recoveryRelationshipId,
                trustedContactAlias = contact.trustedContactAlias,
                identityKey = contact.publicKey,
                identityPublicKeyMac = contact.identityPublicKeyMac,
                enrollmentKey = contact.enrollmentKey,
                enrollmentKeyConfirmation = contact.enrollmentKeyConfirmation,
                authenticationState = contact.authenticationState
              )
            }
          val trustedContacts =
            trustedContactsQuery.executeAsList().map { trustedContact ->
              TrustedContact(
                recoveryRelationshipId = trustedContact.recoveryRelationshipId,
                trustedContactAlias = trustedContact.trustedContactAlias,
                identityKey = trustedContact.publicKey
              )
            }
          val protectedProtectedCustomers =
            protectedCustomersQueries.executeAsList().map { customer ->
              ProtectedCustomer(
                recoveryRelationshipId = customer.recoveryRelationshipId,
                alias = customer.alias
              )
            }

          SocRecRelationships(
            invitations = invitations,
            trustedContacts = trustedContacts,
            protectedCustomers = protectedProtectedCustomers.toImmutableList(),
            unendorsedTrustedContacts = unendorsedTrustedContacts
          )
        }
      }.distinctUntilChanged()
    }
  }

  override suspend fun setSocRecRelationships(
    socRecRelationships: SocRecRelationships,
  ): Result<Unit, DbError> {
    return database.socRecRelationshipsQueries.awaitTransactionWithResult {
      socRecRelationships.protectedCustomers.let { customers ->

        // Reset customers.
        clearSocRecProtectedCustomers()
        customers.forEach { customer ->
          insertSocRecProtectedCustomer(
            recoveryRelationshipId = customer.recoveryRelationshipId,
            alias = customer.alias
          )
        }

        // Reset trusted contacts.
        clearSocRecTrustedContacts()
        socRecRelationships.trustedContacts.forEach { tc ->
          insertSocRecTrustedContact(
            recoveryRelationshipId = tc.recoveryRelationshipId,
            publicKey = tc.identityKey,
            trustedContactAlias = tc.trustedContactAlias
          )
        }

        // Reset invitations
        clearSocRecTrustedContactInvitations()
        socRecRelationships.invitations.forEach { invitation ->
          insertSocRecTrustedContactInvitation(
            recoveryRelationshipId = invitation.recoveryRelationshipId,
            trustedContactAlias = invitation.trustedContactAlias,
            token = invitation.token,
            expiresAt = invitation.expiresAt
          )
        }

        // Grab the existing authentication states for the unendorsed trusted contacts
        val currentAuthStates =
          getSocRecUnendorsedTrustedContacts()
            .executeAsList()
            .associate { it.recoveryRelationshipId to it.authenticationState }
        // Reset unendorsed trusted contacts
        clearSocRecUnendorsedTrustedContacts()
        socRecRelationships.unendorsedTrustedContacts.forEach { contact ->
          insertSocRecUnendorsedTrustedContact(
            recoveryRelationshipId = contact.recoveryRelationshipId,
            publicKey = contact.identityKey,
            trustedContactAlias = contact.trustedContactAlias,
            identityPublicKeyMac = contact.identityPublicKeyMac,
            enrollmentKey = contact.enrollmentKey,
            enrollmentKeyConfirmation = contact.enrollmentKeyConfirmation,
            authenticationState =
              currentAuthStates[contact.recoveryRelationshipId]
                ?: TrustedContactAuthenticationState.UNENDORSED
          )
        }
      }
    }
  }

  override suspend fun setUnendorsedTrustedContactAuthenticationState(
    recoveryRelationshipId: String,
    authenticationState: TrustedContactAuthenticationState,
  ): Result<Unit, DbError> {
    return database.socRecRelationshipsQueries.awaitTransactionWithResult {
      setSocRecUnendorsedTrustedContactAuthenticationState(
        recoveryRelationshipId = recoveryRelationshipId,
        authenticationState = authenticationState
      )
    }
  }

  override suspend fun clear() =
    database.awaitTransactionWithResult {
      socRecRelationshipsQueries.clear()
    }
}
