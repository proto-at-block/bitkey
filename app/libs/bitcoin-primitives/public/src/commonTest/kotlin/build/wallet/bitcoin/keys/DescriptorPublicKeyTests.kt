package build.wallet.bitcoin.keys

import build.wallet.bitcoin.keys.DescriptorPublicKey.Origin
import build.wallet.bitcoin.keys.DescriptorPublicKey.Wildcard.Hardened
import build.wallet.bitcoin.keys.DescriptorPublicKey.Wildcard.Unhardened
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DescriptorPublicKeyTests : FunSpec({
  test("parse unhardened no children") {
    DescriptorPublicKey(
      dpub = "[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVKDy1fgK/*"
    ).shouldBe(
      DescriptorPublicKey(
        origin = Origin("e5ff120e", "/84'/0'/0'"),
        xpub = "xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVKDy1fgK",
        derivationPath = "/*",
        wildcard = Unhardened
      )
    )
  }

  test("parse hardened with children") {
    DescriptorPublicKey(
      dpub = "[deadbeef/0h/1h/2h]xpub6ERApfZwUNrhLCkDtcHTcxd75RbzS1ed54G1LkBUHQVHQKqhMkhgbmJbZRkrgZw4koxb5JaHWkY4ALHY2grBGRjaDMzQLcgJvLJuZZvRcEL/3h/4h/5h/*h"
    ).shouldBe(
      DescriptorPublicKey(
        origin = Origin("deadbeef", "/0h/1h/2h"),
        xpub = "xpub6ERApfZwUNrhLCkDtcHTcxd75RbzS1ed54G1LkBUHQVHQKqhMkhgbmJbZRkrgZw4koxb5JaHWkY4ALHY2grBGRjaDMzQLcgJvLJuZZvRcEL",
        derivationPath = "/3h/4h/5h/*h",
        wildcard = Hardened
      )
    )
  }

  test("parse fails - missing origin") {
    shouldThrow<IllegalArgumentException> {
      DescriptorPublicKey(
        dpub = "xpub6ERApfZwUNrhLCkDtcHTcxd75RbzS1ed54G1LkBUHQVHQKqhMkhgbmJbZRkrgZw4koxb5JaHWkY4ALHY2grBGRjaDMzQLcgJvLJuZZvRcEL/3h/4h/5h/*h"
      )
    }
  }

  test("parse roundtrip") {
    listOf(
      "[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVKDy1fgK/*",
      "[deadbeef/0h/1h/2h]xpub6ERApfZwUNrhLCkDtcHTcxd75RbzS1ed54G1LkBUHQVHQKqhMkhgbmJbZRkrgZw4koxb5JaHWkY4ALHY2grBGRjaDMzQLcgJvLJuZZvRcEL/3h/4h/5h/*h"
    ).forEach { key ->
      DescriptorPublicKey(dpub = key).dpub.shouldBe(key)
    }
  }

  test("extractAccountIndex - apostrophe notation") {
    val dpub = DescriptorPublicKey(
      dpub = "[e5ff120e/84'/0'/2']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVKDy1fgK/*"
    )
    dpub.extractAccountIndex().shouldBe(2u)
  }

  test("extractAccountIndex - h notation") {
    val dpub = DescriptorPublicKey(
      dpub = "[deadbeef/84h/1h/5h]xpub6ERApfZwUNrhLCkDtcHTcxd75RbzS1ed54G1LkBUHQVHQKqhMkhgbmJbZRkrgZw4koxb5JaHWkY4ALHY2grBGRjaDMzQLcgJvLJuZZvRcEL/*"
    )
    dpub.extractAccountIndex().shouldBe(5u)
  }

  test("extractAccountIndex - H notation") {
    val dpub = DescriptorPublicKey(
      dpub = "[deadbeef/84H/0H/3H]xpub6ERApfZwUNrhLCkDtcHTcxd75RbzS1ed54G1LkBUHQVHQKqhMkhgbmJbZRkrgZw4koxb5JaHWkY4ALHY2grBGRjaDMzQLcgJvLJuZZvRcEL/*"
    )
    dpub.extractAccountIndex().shouldBe(3u)
  }

  test("extractAccountIndex - non-BIP84 path returns 0") {
    val dpub = DescriptorPublicKey(
      dpub = "[deadbeef/49'/0'/0']xpub6ERApfZwUNrhLCkDtcHTcxd75RbzS1ed54G1LkBUHQVHQKqhMkhgbmJbZRkrgZw4koxb5JaHWkY4ALHY2grBGRjaDMzQLcgJvLJuZZvRcEL/*"
    )
    dpub.extractAccountIndex().shouldBe(0u)
  }

  test("extractAccountIndex - account 0") {
    val dpub = DescriptorPublicKey(
      dpub = "[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVKDy1fgK/*"
    )
    dpub.extractAccountIndex().shouldBe(0u)
  }
})
