package build.wallet.recovery.socrec

import build.wallet.f8e.socrec.SocialRecoveryService

/**
 * Provides a [SocialRecoveryService] instance based on whether the account is configured to use
 * fakes.
 */
fun interface SocialRecoveryServiceProvider {
  suspend fun get(): SocialRecoveryService
}
