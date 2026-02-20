package build.wallet.auth

import bitkey.auth.AccessToken
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock2
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class SignedAccessTokenCacheImplTests : FunSpec({

  val cache = SignedAccessTokenCacheImpl()
  val accessToken = AccessToken("test-token")
  val publicKey = AppGlobalAuthPublicKeyMock
  val signature = "test-signature"

  beforeTest {
    cache.clear()
  }

  test("get returns null for uncached token") {
    cache.get(accessToken, publicKey).shouldBeNull()
  }

  test("get returns cached signature after put") {
    cache.put(accessToken, publicKey, signature)
    cache.get(accessToken, publicKey).shouldBe(signature)
  }

  test("different tokens are cached separately") {
    val token1 = AccessToken("token-1")
    val token2 = AccessToken("token-2")
    val signature1 = "signature-1"
    val signature2 = "signature-2"

    cache.put(token1, publicKey, signature1)
    cache.put(token2, publicKey, signature2)

    cache.get(token1, publicKey).shouldBe(signature1)
    cache.get(token2, publicKey).shouldBe(signature2)
  }

  test("different public keys are cached separately") {
    val key1 = AppGlobalAuthPublicKeyMock
    val key2 = AppGlobalAuthPublicKeyMock2
    val signature1 = "signature-1"
    val signature2 = "signature-2"

    cache.put(accessToken, key1, signature1)
    cache.put(accessToken, key2, signature2)

    cache.get(accessToken, key1).shouldBe(signature1)
    cache.get(accessToken, key2).shouldBe(signature2)
  }

  test("clear removes all cached signatures") {
    val token1 = AccessToken("token-1")
    val token2 = AccessToken("token-2")

    cache.put(token1, publicKey, "signature-1")
    cache.put(token2, publicKey, "signature-2")

    cache.clear()

    cache.get(token1, publicKey).shouldBeNull()
    cache.get(token2, publicKey).shouldBeNull()
  }
})
