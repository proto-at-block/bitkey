package build.wallet.f8e.onboarding

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.keybox.AppKeyBundleMock
import build.wallet.bitkey.keybox.HwKeyBundleMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class UpgradeAccountF8eClientImplTests : FunSpec({
  test("Upgrade Account - Request Serialization") {
    val request =
      UpgradeAccountF8eClientImpl.RequestBody(
        appKeyBundle = AppKeyBundleMock,
        hardwareKeyBundle = HwKeyBundleMock,
        network = BitcoinNetworkType.BITCOIN
      )
    val result = Json.encodeToString(request)

    result.shouldBeEqual(
      """{"auth":{"app":"app-auth-dpub","hardware":"hw-auth-dpub"},"spending":{"app":"[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVappdpub/*","hardware":"[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQhardwaredpub/*","network":"bitcoin"}}"""
    )
  }

  test("Upgrade Account - Response Deserialization") {
    val accountId = "fake-account-id"
    val keysetId = "fake-keyset-id"
    val spending = "spending-keys"
    val spendingSig = "spending-sig"
    val response =
      """
      {
        "account_id":"$accountId",
        "keyset_id":"$keysetId",
        "spending":"$spending",
        "spending_sig":"$spendingSig"
      }
      """.trimIndent()

    val result: UpgradeAccountF8eClientImpl.ResponseBody = Json.decodeFromString(response)

    result.shouldBeEqual(
      UpgradeAccountF8eClientImpl.ResponseBody(
        accountId = accountId,
        keysetId = keysetId,
        spending = spending,
        spendingSig = spendingSig
      )
    )
  }
})
