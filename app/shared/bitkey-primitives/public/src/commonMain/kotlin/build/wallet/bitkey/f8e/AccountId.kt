package build.wallet.bitkey.f8e

/**
 * Represents ID of certain Account type.
 */
sealed interface AccountId {
  /**
   * Identifier used by F8e to identify this account.
   */
  val serverId: String
}
