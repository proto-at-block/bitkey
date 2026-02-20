package build.wallet.auth

import bitkey.auth.AccessToken
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.crypto.PublicKey

/**
 * Cache for signed access tokens to avoid redundant cryptographic signing operations.
 *
 * The cache stores signatures keyed by (access token, public key) pairs and automatically
 * invalidates entries when tokens change.
 *
 * All operations are thread-safe and can be called concurrently from multiple coroutines.
 */
interface SignedAccessTokenCache {
  /**
   * Get a cached signature for the given access token and public key.
   *
   * @return The cached signature, or null if not cached or invalidated
   */
  suspend fun get(
    accessToken: AccessToken,
    publicKey: PublicKey<out AppAuthKey>,
  ): String?

  /**
   * Store a signature for the given access token and public key.
   */
  suspend fun put(
    accessToken: AccessToken,
    publicKey: PublicKey<out AppAuthKey>,
    signature: String,
  )

  /**
   * Clear all cached signatures.
   */
  suspend fun clear()
}
