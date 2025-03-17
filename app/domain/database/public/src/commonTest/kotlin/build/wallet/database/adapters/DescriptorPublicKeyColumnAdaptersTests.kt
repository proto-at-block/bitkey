package build.wallet.database.adapters

import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.database.adapters.bitkey.AppSpendingPublicKeyColumnAdapter
import build.wallet.database.adapters.bitkey.F8eSpendingPublicKeyColumnAdapter
import build.wallet.database.adapters.bitkey.HwSpendingPublicKeyColumnAdapter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DescriptorPublicKeyColumnAdaptersTests : FunSpec({

  val descriptorPublicKey =
    DescriptorPublicKey(
      "[deadbeef/0h/1h/2h]xpub6ERApfZwUNrhLCkDtcHTcxd75RbzS1ed54G1LkBUHQVHQKqhMkhgbmJbZRkrgZw4koxb5JaHWkY4ALHY2grBGRjaDMzQLcgJvLJuZZvRcEL/3h/4h/5h/*h"
    )

  context("app spending key column adapter") {
    test("decode key from raw dpub string") {
      AppSpendingPublicKeyColumnAdapter
        .decode(descriptorPublicKey.dpub)
        .shouldBe(AppSpendingPublicKey(descriptorPublicKey))
    }

    test("encode key as raw dpub string") {
      AppSpendingPublicKeyColumnAdapter
        .encode(AppSpendingPublicKey(descriptorPublicKey))
        .shouldBe(descriptorPublicKey.dpub)
    }
  }

  context("hardware spending key column adapter") {
    test("decode key from raw dpub string") {
      HwSpendingPublicKeyColumnAdapter
        .decode(descriptorPublicKey.dpub)
        .shouldBe(HwSpendingPublicKey(descriptorPublicKey))
    }

    test("encode key as raw dpub string") {
      HwSpendingPublicKeyColumnAdapter
        .encode(HwSpendingPublicKey(descriptorPublicKey))
        .shouldBe(descriptorPublicKey.dpub)
    }
  }

  context("f8e spending key column adapter") {
    test("decode key from raw dpub string") {
      F8eSpendingPublicKeyColumnAdapter
        .decode(descriptorPublicKey.dpub)
        .shouldBe(F8eSpendingPublicKey(descriptorPublicKey.dpub))
    }

    test("encode key as raw dpub string") {
      F8eSpendingPublicKeyColumnAdapter
        .encode(F8eSpendingPublicKey(descriptorPublicKey))
        .shouldBe(descriptorPublicKey.dpub)
    }
  }
})
