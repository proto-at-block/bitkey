package build.wallet.partnerships

import build.wallet.db.DbTransactionError
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.datetime.Instant

class PartnershipTransactionsRepositoryTest : FunSpec({
  test("Create Transaction") {
    val savedTransactions = mutableListOf<PartnershipTransaction>()
    val daoSpy = object : PartnershipTransactionsDao by PartnershipTransactionsDaoDummy() {
      override suspend fun save(
        transaction: PartnershipTransaction,
      ): Result<Unit, DbTransactionError> {
        savedTransactions += transaction

        return Ok(Unit)
      }
    }
    val clock = ClockFake(Instant.fromEpochMilliseconds(123))
    val repository = PartnershipTransactionsRepositoryImpl(
      dao = daoSpy,
      uuidGenerator = { "test-uuid" },
      clock = clock
    )

    val result = repository.create(
      partnerInfo = PartnerInfo(
        partner = "test-partner",
        name = "test-partner-name",
        logoUrl = "test-partner-logo-url"
      ),
      type = PartnershipTransactionType.PURCHASE
    )

    savedTransactions.size.shouldBe(1)
    result.shouldBeInstanceOf<Ok<PartnershipTransaction>>()
      .should { (result) ->
        savedTransactions.single().shouldBeSameInstanceAs(result)
        result.id.value.shouldBe("test-uuid")
        result.type.shouldBe(PartnershipTransactionType.PURCHASE)
        result.status.shouldBeNull()
        result.partnerInfo.partner.shouldBe("test-partner")
        result.partnerInfo.name.shouldBe("test-partner-name")
        result.partnerInfo.logoUrl.shouldBe("test-partner-logo-url")
        result.cryptoAmount.shouldBeNull()
        result.txid.shouldBeNull()
        result.fiatAmount.shouldBeNull()
        result.fiatCurrency.shouldBeNull()
        result.paymentMethod.shouldBeNull()
        result.created.toEpochMilliseconds().shouldBe(123)
        result.updated.toEpochMilliseconds().shouldBe(123)
      }
  }
})
