package build.wallet.auth

import bitkey.auth.AccessToken
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.crypto.PublicKey

class SignedAccessTokenCacheFake : SignedAccessTokenCache {
  private val cache = mutableMapOf<CacheKey, String>()

  fun reset() {
    cache.clear()
  }

  override suspend fun get(
    accessToken: AccessToken,
    publicKey: PublicKey<out AppAuthKey>,
  ): String? {
    val key = CacheKey(accessToken.raw, publicKey.value)
    return cache[key]
  }

  override suspend fun put(
    accessToken: AccessToken,
    publicKey: PublicKey<out AppAuthKey>,
    signature: String,
  ) {
    val key = CacheKey(accessToken.raw, publicKey.value)
    cache[key] = signature
  }

  override suspend fun clear() {
    cache.clear()
  }

  private data class CacheKey(
    val tokenRaw: String,
    val publicKeyValue: String,
  )
}
