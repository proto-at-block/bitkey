package build.wallet.recovery.socrec

import build.wallet.f8e.socrec.SocRecF8eClient

/**
 * Provides a [SocRecF8eClient] instance based on whether the account is configured to use
 * fakes.
 */
fun interface SocRecF8eClientProvider {
  fun get(): SocRecF8eClient
}
