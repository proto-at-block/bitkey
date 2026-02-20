package build.wallet.auth

import bitkey.auth.AccessToken
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe in-memory cache for signed access tokens.
 *
 * Uses a Mutex to protect concurrent access to the underlying map,
 * ensuring safe access from multiple coroutines.
 */
@BitkeyInject(AppScope::class)
class SignedAccessTokenCacheImpl : SignedAccessTokenCache {
  private val cache = mutableMapOf<CacheKey, String>()
  private val mutex = Mutex()

  override suspend fun get(
    accessToken: AccessToken,
    publicKey: PublicKey<out AppAuthKey>,
  ): String? =
    mutex.withLock {
      val key = CacheKey(accessToken.raw, publicKey.value)
      cache[key]
    }

  override suspend fun put(
    accessToken: AccessToken,
    publicKey: PublicKey<out AppAuthKey>,
    signature: String,
  ) = mutex.withLock {
    val key = CacheKey(accessToken.raw, publicKey.value)
    cache[key] = signature
  }

  override suspend fun clear() =
    mutex.withLock {
      cache.clear()
    }

  private data class CacheKey(
    val tokenRaw: String,
    val publicKeyValue: String,
  )
}
