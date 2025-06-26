package build.wallet.inheritance

import app.cash.turbine.test
import build.wallet.bitkey.inheritance.*
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.sqldelight.inMemorySqlDriver
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class InheritanceClaimsDaoImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()

  lateinit var dao: InheritanceClaimsDaoImpl

  val inheritanceClaims = InheritanceClaims(
    benefactorClaims = listOf(
      BenefactorPendingClaimFake, BenefactorCanceledClaimFake, BenefactorLockedClaimFake
    ),
    beneficiaryClaims = listOf(
      BeneficiaryPendingClaimFake, BeneficiaryCanceledClaimFake, BeneficiaryLockedClaimBothDescriptorsFake
    )
  )

  beforeTest {
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    dao = InheritanceClaimsDaoImpl(databaseProvider)
  }

  test("insert and retrieve claims") {
    dao.setInheritanceClaims(inheritanceClaims)
    dao.pendingBeneficiaryClaims.test {
      awaitItem().shouldBe(Ok(listOf(BeneficiaryPendingClaimFake)))
    }
    dao.pendingBenefactorClaims.test {
      awaitItem().shouldBe(Ok(listOf(BenefactorPendingClaimFake)))
    }
  }

  test("clear dao") {
    dao.setInheritanceClaims(inheritanceClaims)
    dao.clear()
    dao.pendingBeneficiaryClaims.test {
      awaitItem().shouldBe(Ok(emptyList()))
    }
    dao.pendingBenefactorClaims.test {
      awaitItem().shouldBe(Ok(emptyList()))
    }
  }
})
