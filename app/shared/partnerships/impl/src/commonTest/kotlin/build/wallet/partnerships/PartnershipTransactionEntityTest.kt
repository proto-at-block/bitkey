package build.wallet.partnerships

import build.wallet.database.sqldelight.PartnershipTransactionEntity
import build.wallet.money.currency.code.IsoCurrencyTextCode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant

class PartnershipTransactionEntityTest : FunSpec({
  test("Model Conversion") {
    val entity = PartnershipTransactionEntity(
      transactionId = PartnershipTransactionId("test-id"),
      partnerId = "test-partner",
      partnerName = "test-partner-name",
      partnerLogoUrl = "test-partner-logo-url",
      context = "test-context",
      type = PartnershipTransactionType.PURCHASE,
      status = PartnershipTransactionStatus.PENDING,
      cryptoAmount = 1.23,
      txid = "test-transaction-hash",
      fiatAmount = 3.21,
      fiatCurrency = IsoCurrencyTextCode("USD"),
      paymentMethod = "test-payment-method",
      created = Instant.fromEpochMilliseconds(248),
      updated = Instant.fromEpochMilliseconds(842)
    )

    val model = entity.toModel()

    model.id.value.shouldBe("test-id")
    model.partnerInfo.partner.shouldBe("test-partner")
    model.partnerInfo.name.shouldBe("test-partner-name")
    model.partnerInfo.logoUrl.shouldBe("test-partner-logo-url")
    model.context.shouldBe("test-context")
    model.type.shouldBe(PartnershipTransactionType.PURCHASE)
    model.status.shouldBe(PartnershipTransactionStatus.PENDING)
    model.cryptoAmount.shouldBe(1.23.plusOrMinus(1e-16))
    model.txid.shouldBe("test-transaction-hash")
    model.fiatAmount.shouldBe(3.21.plusOrMinus(1e-16))
    model.fiatCurrency?.code.shouldBe("USD")
    model.paymentMethod.shouldBe("test-payment-method")
    model.created.toEpochMilliseconds().shouldBe(248)
    model.updated.toEpochMilliseconds().shouldBe(842)
  }

  test("Entity Conversion") {
    val model = PartnershipTransaction(
      id = PartnershipTransactionId("test-id"),
      partnerInfo = PartnerInfo(
        partner = "test-partner",
        name = "test-partner-name",
        logoUrl = "test-partner-logo-url"
      ),
      context = "test-context",
      type = PartnershipTransactionType.PURCHASE,
      status = PartnershipTransactionStatus.PENDING,
      cryptoAmount = 1.23,
      txid = "test-transaction-hash",
      fiatAmount = 3.21,
      fiatCurrency = IsoCurrencyTextCode("USD"),
      paymentMethod = "test-payment-method",
      created = Instant.fromEpochMilliseconds(248),
      updated = Instant.fromEpochMilliseconds(842)
    )

    val entity = model.toEntity()

    entity.transactionId.value.shouldBe("test-id")
    entity.partnerId.shouldBe("test-partner")
    entity.partnerName.shouldBe("test-partner-name")
    entity.partnerLogoUrl.shouldBe("test-partner-logo-url")
    entity.context.shouldBe("test-context")
    entity.type.shouldBe(PartnershipTransactionType.PURCHASE)
    entity.status.shouldBe(PartnershipTransactionStatus.PENDING)
    entity.cryptoAmount.shouldBe(1.23.plusOrMinus(1e-16))
    entity.txid.shouldBe("test-transaction-hash")
    entity.fiatAmount.shouldBe(3.21.plusOrMinus(1e-16))
    entity.fiatCurrency?.code.shouldBe("USD")
    entity.paymentMethod.shouldBe("test-payment-method")
    entity.created.toEpochMilliseconds().shouldBe(248)
    entity.updated.toEpochMilliseconds().shouldBe(842)
  }
})
