@file:OptIn(ExperimentalSerializationApi::class)

package build.wallet.f8e.partnerships

import build.wallet.f8e.partnerships.GetPartnershipTransactionF8eClientImpl.PartnershipTransactionResponse
import build.wallet.money.currency.code.IsoCurrencyTextCode
import build.wallet.partnerships.PartnershipTransactionId
import build.wallet.partnerships.PartnershipTransactionStatus
import build.wallet.partnerships.PartnershipTransactionType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

class PartnershipSerializationTests : FunSpec({
  val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
  }

  test("Partnership Transaction Status Deserialization - Full object") {
    val jsonData = """
            {
                "transaction": {
                    "id": "test-id",
                    "type": "PURCHASE",
                    "status": "SUCCESS",
                    "context": "test-context",
                    "partner_info": {
                        "logo_url": "https://block.xyz/test.png",
                        "name": "Test Partner",
                        "partner": "test-partner"
                    },
                    "crypto_amount": 0.00024056,
                    "txid": "abcd1234",
                    "fiat_amount": 20.0,
                    "fiat_currency": "USD",
                    "payment_method": "CARD"
                }
            }
    """.trimIndent()

    val deserialized = json.decodeFromString<PartnershipTransactionResponse>(jsonData)

    deserialized.transaction.id.shouldBe(PartnershipTransactionId("test-id"))
    deserialized.transaction.type.shouldBe(PartnershipTransactionType.PURCHASE)
    deserialized.transaction.status.shouldBe(PartnershipTransactionStatus.SUCCESS)
    deserialized.transaction.context.shouldBe("test-context")
    deserialized.transaction.partnerInfo.logoUrl.shouldBe("https://block.xyz/test.png")
    deserialized.transaction.partnerInfo.name.shouldBe("Test Partner")
    deserialized.transaction.partnerInfo.partnerId.value.shouldBe("test-partner")
    deserialized.transaction.cryptoAmount.shouldBe(0.00024056)
    deserialized.transaction.txid.shouldBe("abcd1234")
    deserialized.transaction.fiatAmount.shouldBe(20.0)
    deserialized.transaction.fiatCurrency.shouldBe(IsoCurrencyTextCode("USD"))
    deserialized.transaction.paymentMethod.shouldBe("CARD")
  }

  test("Partnership Transaction Status Deserialization - Minimal object") {
    val jsonData = """
            {
                "transaction": {
                    "id": "test-id",
                    "type": "PURCHASE",
                    "status": "SUCCESS",
                    "partner_info": {
                        "name": "Test Partner",
                        "partner": "test-partner"
                    }
                }
            }
    """.trimIndent()

    val deserialized = json.decodeFromString<PartnershipTransactionResponse>(jsonData)

    deserialized.transaction.id.shouldBe(PartnershipTransactionId("test-id"))
    deserialized.transaction.type.shouldBe(PartnershipTransactionType.PURCHASE)
    deserialized.transaction.status.shouldBe(PartnershipTransactionStatus.SUCCESS)
    deserialized.transaction.context.shouldBeNull()
    deserialized.transaction.partnerInfo.logoUrl.shouldBeNull()
    deserialized.transaction.partnerInfo.name.shouldBe("Test Partner")
    deserialized.transaction.partnerInfo.partnerId.value.shouldBe("test-partner")
    deserialized.transaction.cryptoAmount.shouldBeNull()
    deserialized.transaction.txid.shouldBeNull()
    deserialized.transaction.fiatAmount.shouldBeNull()
    deserialized.transaction.fiatCurrency.shouldBeNull()
    deserialized.transaction.paymentMethod.shouldBeNull()
  }
})
