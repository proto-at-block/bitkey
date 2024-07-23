package build.wallet.bitkey.account

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.f8e.SoftwareAccountId
import build.wallet.crypto.PublicKey

/**
 * Represents a Software Account that has been created but has not yet
 * completed onboarding.
 *
 * An [OnboardingSoftwareAccount] does not any associated spending wallets.
 */
data class OnboardingSoftwareAccount(
  override val accountId: SoftwareAccountId,
  override val config: SoftwareAccountConfig,
  val appGlobalAuthKey: PublicKey<AppGlobalAuthKey>,
  val recoveryAuthKey: PublicKey<AppRecoveryAuthKey>,
) : Account
