package build.wallet.f8e.onboarding

import bitkey.account.HardwareType
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock
import build.wallet.bitkey.keybox.AppKeyBundleMock
import build.wallet.bitkey.keybox.HwKeyBundleMock
import build.wallet.f8e.onboarding.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CreateAccountF8eClientImplTests : FunSpec({
  test("Create Account - Lite - Request Serialization") {
    val request =
      LiteCreateAccountRequestBody(
        appRecoveryAuthKey = AppRecoveryAuthPublicKeyMock,
        isTestAccount = null
      )
    val result = Json.encodeToString(request)

    result.shouldBeEqual(
      """{"auth":{"recovery":"app-recovery-auth-dpub"},"is_test_account":null}"""
    )
  }

  test("Create Account - Lite - Response Deserialization") {
    val accountId = "fake-id"
    val response =
      """
      {
        "account_id":"$accountId"
      }
      """.trimIndent()

    val result: LiteCreateAccountResponseBody = Json.decodeFromString(response)

    result.shouldBeEqual(
      LiteCreateAccountResponseBody(accountId = accountId)
    )
  }

  test("Create Account - Full - Request Serialization") {
    val request =
      FullCreateAccountRequestBody(
        appKeyBundle = AppKeyBundleMock,
        hardwareKeyBundle = HwKeyBundleMock,
        network = BitcoinNetworkType.BITCOIN,
        isTestAccount = null
      )
    val result = Json.encodeToString(request)

    result.shouldBeEqual(
      """{"auth":{"app":"app-auth-dpub","hardware":"hw-auth-dpub","recovery":"app-recovery-auth-dpub"},"is_test_account":null,"spending":{"app":"[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVappdpub/*","hardware":"[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQhardwaredpub/*","network":"bitcoin"}}"""
    )
  }

  test("Create Account - Full - Response Deserialization") {
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

    val result: FullCreateAccountResponseBody = Json.decodeFromString(response)

    result.shouldBeEqual(
      FullCreateAccountResponseBody(
        accountId = accountId,
        keysetId = keysetId,
        spending = spending,
        spendingSig = spendingSig
      )
    )
  }

  test("Create Account - Private - Request Serialization") {
    val request = CreateAccountV2RequestBody(
      auth = FullCreateAccountV2AuthKeys(
        appGlobalAuthPublicKey = "app-global-auth-pubkey",
        hardwareAuthPublicKey = "hardware-auth-pubkey",
        recoveryAuthPublicKey = "recovery-auth-pubkey",
        hardwareType = HardwareType.W1
      ),
      spend = FullCreateAccountV2SpendingKeys(
        app = "app-spending-pubkey",
        hardware = "hardware-spending-pubkey",
        network = "bitcoin"
      ),
      isTestAccount = true
    )
    val result = Json.encodeToString(request)

    result.shouldBeEqual(
      """{"auth":{"app_pub":"app-global-auth-pubkey","hardware_pub":"hardware-auth-pubkey","recovery_pub":"recovery-auth-pubkey","hardware_type":"W1"},"is_test_account":true,"spend":{"app_pub":"app-spending-pubkey","hardware_pub":"hardware-spending-pubkey","network":"bitcoin"}}"""
    )
  }

  test("Create Account - Private - W3 HW - Request Serialization") {
    val request = CreateAccountV2RequestBody(
      auth = FullCreateAccountV2AuthKeys(
        appGlobalAuthPublicKey = "app-global-auth-pubkey",
        hardwareAuthPublicKey = "hardware-auth-pubkey",
        recoveryAuthPublicKey = "recovery-auth-pubkey",
        hardwareType = HardwareType.W3
      ),
      spend = FullCreateAccountV2SpendingKeys(
        app = "app-spending-pubkey",
        hardware = "hardware-spending-pubkey",
        network = "bitcoin"
      ),
      isTestAccount = true
    )
    val result = Json.encodeToString(request)

    result.shouldBeEqual(
      """{"auth":{"app_pub":"app-global-auth-pubkey","hardware_pub":"hardware-auth-pubkey","recovery_pub":"recovery-auth-pubkey","hardware_type":"W3"},"is_test_account":true,"spend":{"app_pub":"app-spending-pubkey","hardware_pub":"hardware-spending-pubkey","network":"bitcoin"}}"""
    )
  }

  test("Create Account - Private - Request Serialization - with hardware attestation") {
    val request = CreateAccountV2RequestBody(
      auth = FullCreateAccountV2AuthKeys(
        appGlobalAuthPublicKey = "app-global-auth-pubkey",
        hardwareAuthPublicKey = "hardware-auth-pubkey",
        recoveryAuthPublicKey = "recovery-auth-pubkey",
        hardwareType = HardwareType.W1
      ),
      spend = FullCreateAccountV2SpendingKeys(
        app = "app-spending-pubkey",
        hardware = "hardware-spending-pubkey",
        network = "bitcoin",
        hardwareAttestation = HardwareAttestationBody(
          signature = "sig-base64",
          certChain = listOf("identity-cert-base64", "batch-cert-base64")
        )
      ),
      isTestAccount = true
    )
    val result = Json.encodeToString(request)

    result.shouldBeEqual(
      """{"auth":{"app_pub":"app-global-auth-pubkey","hardware_pub":"hardware-auth-pubkey","recovery_pub":"recovery-auth-pubkey","hardware_type":"W1"},"is_test_account":true,"spend":{"app_pub":"app-spending-pubkey","hardware_pub":"hardware-spending-pubkey","network":"bitcoin","hardware_attestation":{"signature":"sig-base64","cert_chain":["identity-cert-base64","batch-cert-base64"]}}}"""
    )
  }

  test("Create Account - Private - Response Deserialization") {
    val accountId = "private-account-id"
    val keysetId = "private-keyset-id"
    val serverPub = "03774eec7a3d550d18e9f89414152025b3b0ad6a342b19481f702d843cff06dba7"
    val serverPubIntegritySig = "server-pub-integrity-sig"
    val response =
      """
      {
        "account_id":"$accountId",
        "keyset_id":"$keysetId",
        "server_pub":"$serverPub",
        "server_pub_integrity_sig":"$serverPubIntegritySig"
      }
      """.trimIndent()

    val result: CreateAccountV2ResponseBody = Json.decodeFromString(response)

    result.shouldBeEqual(
      CreateAccountV2ResponseBody(
        accountId = accountId,
        keysetId = keysetId,
        serverPub = serverPub,
        serverPubIntegritySig = serverPubIntegritySig
      )
    )
  }
})
