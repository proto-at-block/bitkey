package build.wallet.relationships

import build.wallet.account.AccountService
import build.wallet.account.AccountStatus.ActiveAccount
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.relationships.*
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.relationships.EndorseTrustedContactsF8eClientProvider
import build.wallet.logging.logFailure
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

class EndorseTrustedContactsServiceImpl(
  private val accountService: AccountService,
  private val relationshipsService: RelationshipsService,
  private val relationshipsDao: RelationshipsDao,
  private val relationshipsEnrollmentAuthenticationDao: RelationshipsEnrollmentAuthenticationDao,
  private val relationshipsCrypto: RelationshipsCrypto,
  private val endorseTrustedContactsF8eClientProvider: EndorseTrustedContactsF8eClientProvider,
) : EndorseTrustedContactsService, EndorseTrustedContactsWorker {
  override suspend fun executeWork() {
    accountService.accountStatus()
      .collectLatest { result ->
        result.onSuccess { accountStatus ->
          if (accountStatus is ActiveAccount) {
            val account = accountStatus.account
            if (account is FullAccount) {
              relationshipsService.relationships
                .filterNotNull()
                .map { it.unendorsedTrustedContacts }
                .distinctUntilChanged()
                .collect { authenticateAndEndorse(it, account) }
            }
          }
        }
      }
  }

  override suspend fun authenticateRegenerateAndEndorse(
    accountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    contacts: List<EndorsedTrustedContact>,
    oldAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>?,
    oldHwAuthKey: HwAuthPublicKey,
    newAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>,
    newAppGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
  ): Result<Unit, Error> =
    coroutineBinding {
      val endorsements = contacts.map { contact ->
        relationshipsCrypto.verifyAndRegenerateKeyCertificate(
          oldCertificate = contact.keyCertificate,
          oldAppGlobalAuthKey = oldAppGlobalAuthKey,
          oldHwAuthKey = oldHwAuthKey,
          newAppGlobalAuthKey = newAppGlobalAuthKey,
          newAppGlobalAuthKeyHwSignature = newAppGlobalAuthKeyHwSignature
        ).logFailure {
          "Failed to verify contact ${contact.relationshipId} key certificate for certificate regeneration."
        }.onFailure {
          relationshipsDao.setTrustedContactAuthenticationState(
            contact.relationshipId,
            TrustedContactAuthenticationState.TAMPERED
          ).logFailure {
            "Failed to set contact ${contact.relationshipId} authentication state to TAMPERED."
          }.bind()
        }.map { newCert ->
          TrustedContactEndorsement(
            relationshipId = RelationshipId(contact.relationshipId),
            keyCertificate = newCert
          )
        }
      }.mapNotNull { it.get() }

      // Upload the new key certificates to f8e
      endorseTrustedContactsF8eClientProvider.get()
        .endorseTrustedContacts(accountId, f8eEnvironment, endorsements)
        .bind()
    }

  /**
   * Authenticates and endorses the given contacts, updating the database as necessary.
   * @return successfully authenticated contacts
   */
  internal suspend fun authenticateAndEndorse(
    contacts: List<UnendorsedTrustedContact>,
    fullAccount: FullAccount,
  ): Result<Unit, Throwable> =
    coroutineBinding<Unit, Throwable> {
      val authenticated = contacts
        // Only process contacts that haven't failed authentication
        .filter { it.authenticationState == TrustedContactAuthenticationState.UNAUTHENTICATED }
        .map {
          authenticate(it)
            .logFailure {
              "Unexpected application error handling key confirmation for ${it.relationshipId}. We did not get far enough to attempt PAKE authentication"
            }
        }
        // Any successful, non-null results are successful authentications
        .filterValues()
        .filterNotNull()
      if (authenticated.any()) {
        endorseAll(fullAccount, authenticated)
          .logFailure { "Failed to endorse trusted contacts" }
          .bind()

        // If any contacts were endorsed, sync relationships to update the endorsed contacts
        // and trigger a cloud backup refresh.
        relationshipsService.syncAndVerifyRelationships(fullAccount).bind()
      }
    }

  private suspend fun authenticate(
    contact: UnendorsedTrustedContact,
  ): Result<Pair<UnendorsedTrustedContact, PublicKey<DelegatedDecryptionKey>>?, Throwable> =
    coroutineBinding<Pair<UnendorsedTrustedContact, PublicKey<DelegatedDecryptionKey>>?, Throwable> {
      // Make sure PAKE data is available
      val pakeData =
        relationshipsEnrollmentAuthenticationDao.getByRelationshipId(contact.relationshipId)
          .bind()
      if (pakeData == null) {
        relationshipsDao.setUnendorsedTrustedContactAuthenticationState(
          contact.relationshipId,
          TrustedContactAuthenticationState.PAKE_DATA_UNAVAILABLE
        ).bind()
        return@coroutineBinding null
      }

      // Make sure can authenticate with PAKE
      val delegatedDecryptionKey = authenticateKeys(contact, pakeData)
      if (delegatedDecryptionKey == null) {
        relationshipsDao.setUnendorsedTrustedContactAuthenticationState(
          contact.relationshipId,
          TrustedContactAuthenticationState.FAILED
        ).bind()
        return@coroutineBinding null
      }

      // We do not need to set the authentication state to `ENDORSED` here. Once the certificate is
      // uploaded, the server will transition the contact into the `ENDORSED` state and return
      // it in a separate field ("endorsed_trusted_contact") in relationship sync.
      Pair(contact, delegatedDecryptionKey)
    }

  private fun authenticateKeys(
    contact: UnendorsedTrustedContact,
    pakeData: RelationshipsEnrollmentAuthenticationDao.RelationshipsEnrollmentAuthenticationRow,
  ): PublicKey<DelegatedDecryptionKey>? =
    pakeData.pakeCode.let {
      relationshipsCrypto.decryptDelegatedDecryptionKey(
        password = it,
        protectedCustomerEnrollmentPakeKey = pakeData.protectedCustomerEnrollmentPakeKey,
        encryptDelegatedDecryptionKeyOutput = EncryptDelegatedDecryptionKeyOutput(
          trustedContactEnrollmentPakeKey = contact.enrollmentPakeKey,
          keyConfirmation = contact.enrollmentKeyConfirmation,
          sealedDelegatedDecryptionKey = contact.sealedDelegatedDecryptionKey
        )
      )
    }
      // DO NOT REMOVE this log line. We alert on it.
      // See BKR-858
      .logFailure {
        "[socrec_enrollment_pake_failure] Failed to authenticate keys for ${contact.relationshipId}"
      }
      .get()

  private suspend fun endorseAll(
    fullAccount: FullAccount,
    authenticated: List<Pair<UnendorsedTrustedContact, PublicKey<DelegatedDecryptionKey>>>,
  ): Result<Unit, Throwable> =
    coroutineBinding {
      // Generate a key certificate for each authenticated contact
      val endorsements =
        authenticated.map { (unendorsedTc, tcKey) ->
          val keyCertificate = relationshipsCrypto
            .generateKeyCertificate(
              delegatedDecryptionKey = tcKey,
              hwAuthKey = fullAccount.keybox.activeHwKeyBundle.authKey,
              appGlobalAuthKey = fullAccount.keybox.activeAppKeyBundle.authKey,
              appGlobalAuthKeyHwSignature = fullAccount.keybox.appGlobalAuthKeyHwSignature
            )
            .bind()

          TrustedContactEndorsement(
            relationshipId = RelationshipId(unendorsedTc.relationshipId),
            keyCertificate = keyCertificate
          )
        }

      // Upload the key certificates to f8e
      endorseTrustedContactsF8eClientProvider.get()
        .endorseTrustedContacts(
          fullAccount.accountId,
          fullAccount.config.f8eEnvironment,
          endorsements
        )
        .bind()
    }
}
