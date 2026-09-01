package build.wallet.relationships

import bitkey.account.AccountConfigService
import bitkey.relationships.Relationships
import build.wallet.account.AccountService
import build.wallet.account.AccountStatus.ActiveAccount
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.relationships.*
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.ensure
import build.wallet.f8e.relationships.EndorseTrustedContactsF8eClientProvider
import build.wallet.logging.logFailure
import build.wallet.logging.logInfo
import build.wallet.platform.app.AppSessionManager
import build.wallet.platform.app.AppSessionState.FOREGROUND
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@BitkeyInject(AppScope::class)
class EndorseTrustedContactsServiceImpl(
  private val accountService: AccountService,
  private val accountConfigService: AccountConfigService,
  private val relationshipsService: RelationshipsService,
  private val relationshipsDao: RelationshipsDao,
  private val relationshipsEnrollmentAuthenticationDao: RelationshipsEnrollmentAuthenticationDao,
  private val relationshipsCrypto: RelationshipsCrypto,
  private val endorseTrustedContactsF8eClientProvider: EndorseTrustedContactsF8eClientProvider,
  private val appSessionManager: AppSessionManager,
  private val placeholderRepairRetryDelay: Duration = 5.seconds,
  private val placeholderRepairMaxRetryDelay: Duration = 1.minutes,
) : EndorseTrustedContactsService, EndorseTrustedContactsWorker {
  companion object {
    // Allow time for eventually consistent relationship reads to converge.
    private const val PLACEHOLDER_REPAIR_SYNC_ATTEMPTS = 5
    private val PLACEHOLDER_REPAIR_SYNC_DELAY = 250.milliseconds
  }

  override suspend fun executeWork() {
    accountService.accountStatus()
      .collectLatest { result ->
        result.onSuccess { accountStatus ->
          if (accountStatus is ActiveAccount) {
            val account = accountStatus.account
            if (account is FullAccount) {
              processRelationships(account)
            }
          }
        }
      }
  }

  private suspend fun processRelationships(account: FullAccount) = coroutineScope {
    val triggers = Channel<Trigger>(capacity = Channel.CONFLATED)
    var retryJob: Job? = null
    var retryAttempt = 0

    launch {
      relationshipsService.relationships
        .filterNotNull()
        .distinctUntilChanged()
        .collect {
          retryJob?.cancel()
          retryJob = null
          retryAttempt = 0
          triggers.trySend(Trigger.RelationshipsChanged)
        }
    }

    for (trigger in triggers) {
      if (trigger == Trigger.RelationshipsChanged) retryAttempt = 0
      appSessionManager.appSessionState.first { it == FOREGROUND }
      if (processRelationshipAttempt(account)) {
        retryJob = scheduleRetry(triggers, retryJob, retryAttempt)
        retryAttempt++
      } else {
        retryAttempt = 0
      }
    }
  }

  private enum class Trigger {
    RelationshipsChanged,
    Retry,
  }

  private fun CoroutineScope.scheduleRetry(
    triggers: Channel<Trigger>,
    currentJob: Job?,
    retryAttempt: Int,
  ): Job? {
    if (currentJob?.isActive == true) return currentJob
    val retryDelay = minOf(
      placeholderRepairRetryDelay * 2.0.pow(retryAttempt),
      placeholderRepairMaxRetryDelay
    )
    return launch {
      delay(retryDelay)
      appSessionManager.appSessionState.first { it == FOREGROUND }
      triggers.trySend(Trigger.Retry)
    }
  }

  private suspend fun processRelationshipAttempt(account: FullAccount): Boolean {
    val syncResult = relationshipsService.syncAndVerifyRelationships(account)
      .logFailure { "Failed to refresh relationships for endorsement work" }
    return syncResult.fold(
      success = { relationships ->
        // F8e does not store unendorsed authentication state. Merge the locally persisted state
        // into the fresh server snapshot so terminal FAILED/PAKE_DATA_UNAVAILABLE contacts are not
        // retried while newly arrived relationships are still processed.
        val persistedAuthStates = relationshipsDao.relationships()
          .first()
          .getOrElse { return@fold relationshipsService.relationships.value?.hasPendingWork() == true }
          .unendorsedTrustedContacts
          .associate { it.id to it.authenticationState }
        val relationshipsWithPersistedAuthStates = relationships.copy(
          unendorsedTrustedContacts = relationships.unendorsedTrustedContacts.map { contact ->
            contact.copy(
              authenticationState = persistedAuthStates[contact.id] ?: contact.authenticationState
            )
          }
        )
        processPendingRelationships(relationshipsWithPersistedAuthStates, account)
      },
      failure = { relationshipsService.relationships.value?.hasPendingWork() == true }
    )
  }

  private suspend fun processPendingRelationships(
    relationships: Relationships,
    account: FullAccount,
  ): Boolean {
    val endorsementResult = authenticateAndEndorse(
      relationships.unendorsedTrustedContacts,
      account
    )
    val repairResult = repairPlaceholderEndorsements(
      relationships.endorsedTrustedContacts,
      account
    )
    val endorsementFailed = relationships.hasPendingEndorsement() && endorsementResult.isErr
    val repairFailed = relationships.hasPendingRepair() && repairResult.isErr
    return endorsementFailed || repairFailed
  }

  private fun Relationships.hasPendingWork(): Boolean =
    hasPendingEndorsement() || hasPendingRepair()

  private fun Relationships.hasPendingEndorsement(): Boolean =
    unendorsedTrustedContacts.any {
      it.authenticationState == TrustedContactAuthenticationState.UNAUTHENTICATED
    }

  private fun Relationships.hasPendingRepair(): Boolean =
    endorsedTrustedContacts.any { it.needsHwVerification }

  /** Repairs endorsed contacts with placeholder hardware signatures. */
  internal suspend fun repairPlaceholderEndorsements(
    contacts: List<EndorsedTrustedContact>,
    account: FullAccount,
  ): Result<Unit, Error> {
    // A real keybox signature is required to regenerate certificates.
    if (account.keybox.appGlobalAuthKeyHwSignature.isPlaceholder) return Ok(Unit)

    val placeholderContacts = contacts.filter { it.needsHwVerification }
    if (placeholderContacts.isEmpty()) return Ok(Unit)

    logInfo {
      "[socrec_placeholder_repair] Regenerating ${placeholderContacts.size} TC key " +
        "certificate(s) with placeholder HW endorsements using repaired keybox signature"
    }

    return coroutineBinding {
      authenticateRegenerateAndEndorse(
        accountId = account.accountId,
        contacts = placeholderContacts,
        oldAppGlobalAuthKey = account.keybox.activeAppKeyBundle.authKey,
        oldHwAuthKey = account.keybox.activeHwKeyBundle.authKey,
        newAppGlobalAuthKey = account.keybox.activeAppKeyBundle.authKey,
        newAppGlobalAuthKeyHwSignature = account.keybox.appGlobalAuthKeyHwSignature,
        newHwAuthKey = account.keybox.activeHwKeyBundle.authKey
      ).bind()

      // Wait for relationship reads to return the verified replacement certificates.
      val repairedIds = placeholderContacts.map { it.id }.toSet()
      var converged = false
      for (attempt in 0 until PLACEHOLDER_REPAIR_SYNC_ATTEMPTS) {
        val relationships = relationshipsService.syncAndVerifyRelationships(account).bind()
        converged = repairedIds.all { relationshipId ->
          relationships.endorsedTrustedContacts
            .singleOrNull { it.id == relationshipId }
            ?.let {
              it.authenticationState == TrustedContactAuthenticationState.VERIFIED &&
                !it.keyCertificate.appAuthGlobalKeyHwSignature.isPlaceholder
            } == true
        }
        if (converged) break
        if (attempt < PLACEHOLDER_REPAIR_SYNC_ATTEMPTS - 1) {
          delay(PLACEHOLDER_REPAIR_SYNC_DELAY)
        }
      }
      ensure(converged) {
        Error("Placeholder TC endorsement update did not converge after upload")
      }

      // syncAndVerifyRelationships persists the converged verification state.
      Unit
    }.logFailure { "[socrec_placeholder_repair] Failed to repair placeholder TC endorsements" }
  }

  override suspend fun authenticateRegenerateAndEndorse(
    accountId: FullAccountId,
    contacts: List<EndorsedTrustedContact>,
    oldAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>?,
    oldHwAuthKey: HwAuthPublicKey,
    newAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>,
    newAppGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    newHwAuthKey: HwAuthPublicKey,
    allowW3OnboardingPlaceholder: Boolean,
  ): Result<Unit, Error> =
    coroutineBinding {
      val endorsements = contacts.map { contact ->
        relationshipsCrypto.verifyAndRegenerateKeyCertificate(
          oldCertificate = contact.keyCertificate,
          oldAppGlobalAuthKey = oldAppGlobalAuthKey,
          oldHwAuthKey = oldHwAuthKey,
          newAppGlobalAuthKey = newAppGlobalAuthKey,
          newAppGlobalAuthKeyHwSignature = newAppGlobalAuthKeyHwSignature,
          newHwAuthKey = newHwAuthKey,
          allowW3OnboardingPlaceholder = allowW3OnboardingPlaceholder
        ).logFailure {
          "Failed to verify contact ${contact.id.value} key certificate for certificate regeneration."
        }.onFailure {
          relationshipsDao.setTrustedContactAuthenticationState(
            contact.id.value,
            TrustedContactAuthenticationState.TAMPERED
          ).logFailure {
            "Failed to set contact ${contact.id.value} authentication state to TAMPERED."
          }.bind()
        }.map { newCert ->
          TrustedContactEndorsement(
            relationshipId = contact.id,
            keyCertificate = newCert
          )
        }
      }.mapNotNull { it.get() }

      // Upload the new key certificates to f8e
      val f8eEnvironment = accountConfigService.activeOrDefaultConfig().value.f8eEnvironment
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
              "Unexpected application error handling key confirmation for ${it.id.value}. We did not get far enough to attempt PAKE authentication"
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
        relationshipsEnrollmentAuthenticationDao.getByRelationshipId(contact.id.value)
          .bind()
      if (pakeData == null) {
        relationshipsDao.setUnendorsedTrustedContactAuthenticationState(
          contact.id.value,
          TrustedContactAuthenticationState.PAKE_DATA_UNAVAILABLE
        ).bind()
        return@coroutineBinding null
      }

      // Make sure can authenticate with PAKE
      val delegatedDecryptionKey = authenticateKeys(contact, pakeData)
      if (delegatedDecryptionKey == null) {
        relationshipsDao.setUnendorsedTrustedContactAuthenticationState(
          contact.id.value,
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
        "[socrec_enrollment_pake_failure] Failed to authenticate keys for ${contact.id.value}"
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
            relationshipId = unendorsedTc.id,
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
