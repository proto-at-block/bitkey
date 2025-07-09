package build.wallet.f8e.relationships

import bitkey.relationships.Relationships
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

interface GetRelationshipsF8eClient {
  /**
   * Retrieves relationships that the caller is part of: trusted contact invitations
   * they’ve created (the caller is a Full Account), established trusted contacts
   * (if the caller is a Full Account), and established customers they’re protecting
   * (if the caller is either type of account).
   */
  suspend fun getRelationships(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<Relationships, NetworkingError>
}

suspend fun GetRelationshipsF8eClient.getRelationships(
  account: Account,
): Result<Relationships, NetworkingError> =
  getRelationships(account.accountId, account.config.f8eEnvironment)
