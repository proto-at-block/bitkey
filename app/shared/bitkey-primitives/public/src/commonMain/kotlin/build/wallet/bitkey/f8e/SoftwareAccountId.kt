package build.wallet.bitkey.f8e

/**
 * Represents ID of Software Only type.
 */
data class SoftwareAccountId(
  override val serverId: String,
) : AccountId
