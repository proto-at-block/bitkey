package build.wallet.recovery.socrec

import app.cash.sqldelight.coroutines.asFlow
import build.wallet.bitkey.socrec.EncodedTrustedContactKeyCertificate
import build.wallet.bitkey.socrec.Invitation
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.TrustedContact
import build.wallet.bitkey.socrec.TrustedContactAuthenticationState
import build.wallet.bitkey.socrec.UnendorsedTrustedContact
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
      database.socRecRelationshipsQueries.getSocRecTrustedContacts().asFlow(),
      database.socRecRelationshipsQueries.getSocRecTrustedContactInvitations().asFlow(),
      database.socRecRelationshipsQueries.getSocRecProtectedCustomers().asFlow(),
      database.socRecRelationshipsQueries.getSocRecUnendorsedTrustedContacts().asFlow()
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
              recoveryRelationshipId = invitation.recoveryRelationshipId,
              trustedContactAlias = invitation.trustedContactAlias,
              code = invitation.token,
              codeBitLength = invitation.tokenBitLength.toInt(),
              expiresAt = invitation.expiresAt
            )
          }
        val unendorsedTrustedContacts =
          unendorsedTrustedContactsQueries.executeAsList().map { contact ->
            UnendorsedTrustedContact(
              recoveryRelationshipId = contact.recoveryRelationshipId,
              trustedContactAlias = contact.trustedContactAlias,
              enrollmentPakeKey = contact.enrollmentPakeKey,
              enrollmentKeyConfirmation = contact.enrollmentKeyConfirmation,
              sealedDelegatedDecryptionKey = XCiphertext(contact.sealedDelegatedDecryptionKey),
              authenticationState = contact.authenticationState
            )
          }
        val trustedContacts =
          trustedContactsQuery.executeAsList().map { trustedContact ->
            TrustedContact(
              recoveryRelationshipId = trustedContact.recoveryRelationshipId,
              trustedContactAlias = trustedContact.trustedContactAlias,
              authenticationState = trustedContact.authenticationState,
              keyCertificate = EncodedTrustedContactKeyCertificate(trustedContact.certificate)
                .deserialize()
                .getOrThrow()
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

  override suspend fun setSocRecRelationships(
    socRecRelationships: SocRecRelationships,
  ): Result<Unit, DbError> {
    return database.awaitTransactionWithResult {
      socRecRelationships.protectedCustomers.let { customers ->

        // Reset customers.
        socRecRelationshipsQueries.clearSocRecProtectedCustomers()
        customers.forEach { customer ->
          socRecRelationshipsQueries.insertSocRecProtectedCustomer(
            recoveryRelationshipId = customer.recoveryRelationshipId,
            alias = customer.alias
          )
        }

        // Maintain the existing authentication states for the trusted contacts
        val currentAuthStates =
          socRecRelationshipsQueries.getSocRecTrustedContacts()
            .executeAsList()
            .associate { it.recoveryRelationshipId to it.authenticationState }
        // Reset trusted contacts.
        socRecRelationshipsQueries.clearSocRecTrustedContacts()
        socRecRelationships.trustedContacts.forEach { tc ->
          socRecRelationshipsQueries.insertSocRecTrustedContact(
            recoveryRelationshipId = tc.recoveryRelationshipId,
            trustedContactAlias = tc.trustedContactAlias,
            authenticationState = currentAuthStates[tc.recoveryRelationshipId]
              ?: tc.authenticationState,
            certificate = tc.keyCertificate.encode().getOrThrow().base64
          )
        }

        // Reset invitations
        socRecRelationshipsQueries.clearSocRecTrustedContactInvitations()
        socRecRelationships.invitations.forEach { invitation ->
          socRecRelationshipsQueries.insertSocRecTrustedContactInvitation(
            recoveryRelationshipId = invitation.recoveryRelationshipId,
            trustedContactAlias = invitation.trustedContactAlias,
            token = invitation.code,
            tokenBitLength = invitation.codeBitLength.toLong(),
            expiresAt = invitation.expiresAt
          )
        }

        // Maintain the existing authentication states for the unendorsed trusted contacts
        val currentUnendorsedAuthStates =
          socRecRelationshipsQueries.getSocRecUnendorsedTrustedContacts()
            .executeAsList()
            .associate { it.recoveryRelationshipId to it.authenticationState }
        // Reset unendorsed trusted contacts
        socRecRelationshipsQueries.clearSocRecUnendorsedTrustedContacts()
        socRecRelationships.unendorsedTrustedContacts.forEach { contact ->
          socRecRelationshipsQueries.insertSocRecUnendorsedTrustedContact(
            recoveryRelationshipId = contact.recoveryRelationshipId,
            trustedContactAlias = contact.trustedContactAlias,
            enrollmentPakeKey = contact.enrollmentPakeKey,
            enrollmentKeyConfirmation = contact.enrollmentKeyConfirmation,
            sealedDelegatedDecryptionKey = contact.sealedDelegatedDecryptionKey.value,
            authenticationState =
              currentUnendorsedAuthStates[contact.recoveryRelationshipId]
                ?: TrustedContactAuthenticationState.UNAUTHENTICATED
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

  override suspend fun setTrustedContactAuthenticationState(
    recoveryRelationshipId: String,
    authenticationState: TrustedContactAuthenticationState,
  ): Result<Unit, DbError> {
    return database.socRecRelationshipsQueries.awaitTransactionWithResult {
      setSocRecTrustedContactAuthenticationState(
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
