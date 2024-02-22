package build.wallet.recovery.socrec

import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.socrec.Invitation
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.ProtectedCustomerAlias
import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.bitkey.socrec.TrustedContactIdentityKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.AcceptTrustedContactInvitationErrorCode
import build.wallet.f8e.error.code.RetrieveTrustedContactInvitationErrorCode
import build.wallet.f8e.socrec.SocRecRelationships
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for managing protected customers and trusted contacts for Social Recovery.
 */
interface SocRecRelationshipsRepository {
  /**
   * Launches a non-blocking coroutine to periodically sync relationships with f8e.
   */
  suspend fun syncLoop(account: Account)

  /**
   * Immediately sync relationships with f8e and store the result.
   */
  suspend fun syncRelationships(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<SocRecRelationships, Error>

  /**
   * Get list of customers you are protecting and list of your trusted contacts (pending and
   * accepted invites). Emits latest value stored in database if any. The database value is synced
   * by [syncLoop] as long as [syncLoop] is in scope.
   */
  val relationships: StateFlow<SocRecRelationships>

  /**
   * Remove a recovery relationship that the caller ([accountId]) is part of.
   */
  suspend fun removeRelationship(
    account: Account,
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
  ): Result<Invitation, Error>

  /**
   * Update an invitation for an existing Trusted Contact.
   */
  suspend fun refreshInvitation(
    account: FullAccount,
    relationshipId: String,
    hardwareProofOfPossession: HwFactorProofOfPossession,
  ): Result<Invitation, Error>

  /**
   * Retrieves invitation data for a potential Trusted Contact given a code.
   * Note: [AccountId] can be for either a Full or Lite Customer
   */
  suspend fun retrieveInvitation(
    account: Account,
    invitationCode: String,
  ): Result<Invitation, F8eError<RetrieveTrustedContactInvitationErrorCode>>

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
    invitation: Invitation,
    protectedCustomerAlias: ProtectedCustomerAlias,
    trustedContactIdentityKey: TrustedContactIdentityKey,
  ): Result<ProtectedCustomer, F8eError<AcceptTrustedContactInvitationErrorCode>>

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
