package build.wallet.bitkey.account

import build.wallet.bitkey.f8e.SoftwareAccountId
import build.wallet.bitkey.keybox.SoftwareKeybox

/**
 * Represents a Software Account with an active FROST spending descriptor.
 *
 * A Software Account has most app capabilities, but does not use hardware.
 */
data class SoftwareAccount(
  override val accountId: SoftwareAccountId,
  override val config: SoftwareAccountConfig,
  val keybox: SoftwareKeybox,
) : Account
