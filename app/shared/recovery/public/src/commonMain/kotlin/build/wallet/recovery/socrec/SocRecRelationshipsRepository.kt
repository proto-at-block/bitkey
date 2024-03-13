package build.wallet.recovery.socrec

import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.socrec.DelegatedDecryptionKey
import build.wallet.bitkey.socrec.IncomingInvitation
import build.wallet.bitkey.socrec.OutgoingInvitation
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.ProtectedCustomerAlias
import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.AcceptTrustedContactInvitationErrorCode
import build.wallet.f8e.error.code.RetrieveTrustedContactInvitationErrorCode
import build.wallet.f8e.socrec.SocRecRelationships
import com.github.michaelbull.result.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing protected customers and trusted contacts for Social Recovery.
 */
@Suppress("TooManyFunctions")
interface SocRecRelationshipsRepository {
  /**
   * Launches a non-blocking coroutine to periodically sync relationships with f8e.
   *
   * If the [account] is a [FullAccount]. also verifies the TCs.
   */
  fun syncLoop(
    scope: CoroutineScope,
    account: Account,
  )

  /**
   * Immediately fetches latest relationships from f8e and stores them in db without performing any
   * verification.
   *
   * This method is only useful in the context when there's no [FullAccount] available
   * to perform verification of TCs.
   */
  suspend fun syncRelationshipsWithoutVerification(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<SocRecRelationships, Error>

  /**
   * Immediately fetches latest relationships from f8e, verifies TCs and stores them in db.
   */
  suspend fun syncAndVerifyRelationships(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
    hardwareProofOfPossession: HwFactorProofOfPossession?,
    appAuthKey: AppGlobalAuthPublicKey?,
    hwAuthPublicKey: HwAuthPublicKey?,
  ): Result<SocRecRelationships, Error>

  /**
   * Emits latest [SocRecRelationships] stored in the database. Sync is performed by:
   *   - [syncLoop] as long as the [syncLoop] is in scope.
   *   - [syncAndVerifyRelationships] and [syncRelationshipsWithoutVerification] when called.
   *
   * For [FullAccount], the relationships are always verified before being emitted.
   */
  val relationships: Flow<SocRecRelationships>

  /** Get [SocRecRelationships] but do not persist to the DB or emit to listeners. */
  suspend fun getRelationshipsWithoutSyncing(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
  ): SocRecRelationships

  /**
   * Remove a recovery relationship that the caller ([account]) is part of.
   */
  suspend fun removeRelationship(
    account: Account,
    hardwareProofOfPossession: HwFactorProofOfPossession?,
    authTokenScope: AuthTokenScope,
    relationshipId: String,
  ): Result<Unit, Error>

  /** Like [removeRelationship] but without syncing relationships afterward. */
  suspend fun removeRelationshipWithoutSyncing(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
    hardwareProofOfPossession: HwFactorProofOfPossession?,
    authTokenScope: AuthTokenScope,
    relationshipId: String,
  ): Result<Unit, Error>

  /**
   * Create an invitation to add a new Trusted Contact.
   */
  suspend fun createInvitation(
    account: FullAccount,
    trustedContactAlias: TrustedContactAlias,
    hardwareProofOfPossession: HwFactorProofOfPossession,
  ): Result<OutgoingInvitation, Error>

  /**
   * Update an invitation for an existing Trusted Contact.
   */
  suspend fun refreshInvitation(
    account: FullAccount,
    relationshipId: String,
    hardwareProofOfPossession: HwFactorProofOfPossession,
  ): Result<OutgoingInvitation, Error>

  /**
   * Retrieves invitation data for a potential Trusted Contact given a code.
   * Note: [AccountId] can be for either a Full or Lite Customer
   */
  suspend fun retrieveInvitation(
    account: Account,
    invitationCode: String,
  ): Result<IncomingInvitation, RetrieveInvitationCodeError>

  /**
   * Accept an invitation to become a Trusted Contact. Can be done both by a
   * Full Account and Lite Account.
   *
   * @param account the active Account which accepts the invite.
   * @param invitation the invitation data for becoming a TC.
   * @param protectedCustomerAlias alias/name of the Protected Customer - so that
   * TC remembers whose wallet they are protecting.
   */
  suspend fun acceptInvitation(
    account: Account,
    invitation: IncomingInvitation,
    protectedCustomerAlias: ProtectedCustomerAlias,
    delegatedDecryptionKey: DelegatedDecryptionKey,
    inviteCode: String,
  ): Result<ProtectedCustomer, AcceptInvitationCodeError>

  /**
   * Clear all local social relationship data.
   */
  suspend fun clear(): Result<Unit, Error>

  /**
   * Create a [SocRecLiteAccountActions] by currying the Account into [SocRecRelationshipsRepository].
   */
  fun toActions(account: Account) = SocRecLiteAccountActions(this, account)

  /**
   * Create a [SocRecFullAccountActions] by currying the Account into [SocRecRelationshipsRepository].
   */
  fun toActions(account: FullAccount) = SocRecFullAccountActions(this, account)
}

suspend fun SocRecRelationshipsRepository.syncAndVerifyRelationships(
  account: Account,
): Result<SocRecRelationships, Error> =
  syncAndVerifyRelationships(
    accountId = account.accountId,
    f8eEnvironment = account.config.f8eEnvironment,
    hardwareProofOfPossession = null,
    appAuthKey = (account as? FullAccount)?.keybox?.activeAppKeyBundle?.authKey,
    hwAuthPublicKey = (account as? FullAccount)?.keybox?.activeHwKeyBundle?.authKey
  )

sealed interface RetrieveInvitationCodeError {
  data object InvalidInvitationCode : RetrieveInvitationCodeError

  data object InvitationCodeVersionMismatch : RetrieveInvitationCodeError

  data class F8ePropagatedError(val error: F8eError<RetrieveTrustedContactInvitationErrorCode>) :
    RetrieveInvitationCodeError
}

sealed interface AcceptInvitationCodeError {
  data object InvalidInvitationCode : AcceptInvitationCodeError

  data class CryptoError(val cause: SocRecCryptoError) : AcceptInvitationCodeError

  data class F8ePropagatedError(val error: F8eError<AcceptTrustedContactInvitationErrorCode>) :
    AcceptInvitationCodeError
}
