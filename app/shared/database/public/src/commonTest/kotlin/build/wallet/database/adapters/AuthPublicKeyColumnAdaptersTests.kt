package build.wallet.database.adapters

import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.auth.HwAuthSecp256k1PublicKeyMock
import build.wallet.database.adapters.bitkey.AppGlobalAuthPublicKeyColumnAdapter
import build.wallet.database.adapters.bitkey.HwAuthPublicKeyColumnAdapter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AuthPublicKeyColumnAdaptersTests : FunSpec({

  context("app auth public key") {
    val key = AppGlobalAuthPublicKeyMock

    test("decode key from raw pubkey string") {
      AppGlobalAuthPublicKeyColumnAdapter.decode(key.pubKey.value).shouldBe(key)
    }

    test("encode key as raw pubkey string") {
      AppGlobalAuthPublicKeyColumnAdapter.encode(key).shouldBe(key.pubKey.value)
    }
  }

  context("hardware auth public key") {
    val key = HwAuthSecp256k1PublicKeyMock

    test("decode key from raw pubkey string") {
      HwAuthPublicKeyColumnAdapter.decode(key.pubKey.value).shouldBe(key)
    }

    test("encode key as raw pubkey string") {
      HwAuthPublicKeyColumnAdapter.encode(key).shouldBe(key.pubKey.value)
    }
  }
})
