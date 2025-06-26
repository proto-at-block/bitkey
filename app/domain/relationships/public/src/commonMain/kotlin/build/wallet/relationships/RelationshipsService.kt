package build.wallet.relationships

import bitkey.auth.AuthTokenScope
import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.AcceptTrustedContactInvitationErrorCode
import bitkey.f8e.error.code.F8eClientErrorCode
import bitkey.f8e.error.code.RetrieveTrustedContactInvitationErrorCode
import bitkey.relationships.Relationships
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.promotions.PromotionCode
import build.wallet.bitkey.relationships.*
import build.wallet.crypto.PublicKey
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain service for managing Recovery Contact relationships. These relationships
 * could be leveraged for social recovery or inheritance.
 */
@Suppress("TooManyFunctions")
interface RelationshipsService {
  /**
   * Immediately fetches latest relationships from f8e, verifies TCs and stores them in db.
   */
  suspend fun syncAndVerifyRelationships(
    accountId: AccountId,
    appAuthKey: PublicKey<AppGlobalAuthKey>?,
    hwAuthPublicKey: HwAuthPublicKey?,
  ): Result<Relationships, Error>

  /**
   * Emits latest [Relationships] stored in the database. Sync with f8e is performed by:
   *   - [syncLoop] as long as the [syncLoop] is in scope.
   *   - when [syncAndVerifyRelationships] called.
   *
   * For [FullAccount], the relationships are always verified before being emitted.
   *
   * Emits `null` on initial loading.
   * Emits [Relationships.EMPTY] if there was an error loading relationships from the database.
   */
  val relationships: StateFlow<Relationships?>

  /** Get [Relationships] but do not persist to the DB or emit to listeners. */
  suspend fun getRelationshipsWithoutSyncing(accountId: AccountId): Result<Relationships, Error>

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
    hardwareProofOfPossession: HwFactorProofOfPossession?,
    authTokenScope: AuthTokenScope,
    relationshipId: String,
  ): Result<Unit, Error>

  /**
   * Create an invitation to add a new Recovery Contact.
   */
  suspend fun createInvitation(
    account: FullAccount,
    trustedContactAlias: TrustedContactAlias,
    hardwareProofOfPossession: HwFactorProofOfPossession,
    roles: Set<TrustedContactRole>,
  ): Result<OutgoingInvitation, Error>

  /**
   * Update an invitation for an existing Recovery Contact.
   */
  suspend fun refreshInvitation(
    account: FullAccount,
    relationshipId: String,
    hardwareProofOfPossession: HwFactorProofOfPossession,
  ): Result<OutgoingInvitation, Error>

  /**
   * Retrieves invitation data for a potential Recovery Contact given a code.
   * Note: [AccountId] can be for either a Full or Lite Customer
   */
  suspend fun retrieveInvitation(
    account: Account,
    invitationCode: String,
  ): Result<IncomingInvitation, RetrieveInvitationCodeError>

  /**
   * Accept an invitation to become a Recovery Contact. Can be done both by a
   * Full Account and Lite Account.
   *
   * @param account the active Account which accepts the invite.
   * @param invitation the invitation data for becoming a RC.
   * @param protectedCustomerAlias alias/name of the Protected Customer - so that
   * RC remembers whose wallet they are protecting.
   */
  suspend fun acceptInvitation(
    account: Account,
    invitation: IncomingInvitation,
    protectedCustomerAlias: ProtectedCustomerAlias,
    delegatedDecryptionKey: PublicKey<DelegatedDecryptionKey>,
    inviteCode: String,
  ): Result<ProtectedCustomer, AcceptInvitationCodeError>

  suspend fun retrieveInvitationPromotionCode(
    account: Account,
    invitationCode: String,
  ): Result<PromotionCode?, RetrieveInvitationPromotionCodeError>

  /**
   * Clear all local social relationship data.
   */
  suspend fun clear(): Result<Unit, Error>
}

suspend fun RelationshipsService.syncAndVerifyRelationships(
  account: Account,
): Result<Relationships, Error> =
  syncAndVerifyRelationships(
    accountId = account.accountId,
    appAuthKey = (account as? FullAccount)?.keybox?.activeAppKeyBundle?.authKey,
    hwAuthPublicKey = (account as? FullAccount)?.keybox?.activeHwKeyBundle?.authKey
  )

sealed interface RetrieveInvitationCodeError {
  val cause: Error

  data class ExpiredInvitationCode(
    override val cause: Error,
  ) : RetrieveInvitationCodeError

  data class InvalidInvitationCode(
    override val cause: Error,
  ) : RetrieveInvitationCodeError

  data class InvitationCodeVersionMismatch(
    override val cause: Error,
  ) : RetrieveInvitationCodeError

  data class F8ePropagatedError(val error: F8eError<RetrieveTrustedContactInvitationErrorCode>) :
    RetrieveInvitationCodeError {
    override val cause: Error = error.error
  }
}

sealed interface AcceptInvitationCodeError {
  data object InvalidInvitationCode : AcceptInvitationCodeError

  data class CryptoError(val cause: RelationshipsCryptoError) : AcceptInvitationCodeError

  data class F8ePropagatedError(val error: F8eError<AcceptTrustedContactInvitationErrorCode>) :
    AcceptInvitationCodeError
}

sealed interface RetrieveInvitationPromotionCodeError {
  val cause: Error

  data class InvalidInvitationCode(
    override val cause: Error,
  ) : RetrieveInvitationPromotionCodeError

  data class F8ePropagatedError(val error: F8eError<F8eClientErrorCode>) :
    RetrieveInvitationPromotionCodeError {
    override val cause: Error = error.error
  }
}
