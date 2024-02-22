package build.wallet.bitkey.account

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.Keybox

/**
 * Represents Full Account with 2-of-3 spending [Keybox].
 *
 * A Full Account essentially has full spending bitcoin
 * capabilities using Bitkey hardware device.
 *
 * Social Recovery roles:
 * A Full Account can have a role of Protected Customer - have other
 * [FullAccount]s or [LiteAccount]s as its Trusted Contact(s).
 * A Full Account can also have a role of Trusted Contact - be a
 * Trusted Contact for other [FullAccount]s.
 */
data class FullAccount(
  override val accountId: FullAccountId,
  override val config: FullAccountConfig,
  val keybox: Keybox,
) : Account
