package build.wallet.inheritance

import app.cash.turbine.test
import build.wallet.bitkey.inheritance.*
import build.wallet.coroutines.turbine.turbines
import build.wallet.store.KeyValueStoreFactoryFake
import build.wallet.time.someInstant
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope
import kotlin.time.Duration.Companion.days

class InheritanceCardServiceTests : FunSpec({

  val inheritanceService = InheritanceServiceMock(
    syncCalls = turbines.create("Sync Calls")
  )
  val keyValueStore = KeyValueStoreFactoryFake()
  val inheritanceCardService = InheritanceCardServiceImpl(
    coroutineScope = TestScope(),
    inheritanceService = inheritanceService,
    keyValueStoreFactory = keyValueStore
  )

  beforeTest {
    inheritanceService.reset()
    keyValueStore.clear()
  }

  test("don't display dismissed pending beneficiary claim card") {
    inheritanceService.claims.value = listOf(BeneficiaryPendingClaimFake)
    inheritanceCardService.dismissPendingBeneficiaryClaimCard("claim-benefactor-pending-id")

    inheritanceCardService.cardsToDisplay.test {
      awaitItem().shouldBe(emptyList())
      expectNoEvents()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("display pending beneficiary claim card with one dismissed") {
    val pendingClaimTwo = BeneficiaryPendingClaimFake.copy(
      claimId = InheritanceClaimId("claim-benefactor-pending-id2"),
      delayEndTime = someInstant.plus(360.days)
    )
    inheritanceService.claims.value = listOf(
      BeneficiaryPendingClaimFake,
      pendingClaimTwo
    )
    inheritanceCardService.dismissPendingBeneficiaryClaimCard("claim-benefactor-pending-id")

    inheritanceCardService.cardsToDisplay.test {
      awaitItem().shouldBe(listOf(pendingClaimTwo))
    }
  }

  test("displays locked beneficiary claim card even if pending beneficiary claim card was dismissed") {
    inheritanceService.claims.value = listOf(BeneficiaryLockedClaimFake)
    inheritanceCardService.dismissPendingBeneficiaryClaimCard("claim-benefactor-pending-id")

    inheritanceCardService.cardsToDisplay.test {
      awaitItem().shouldBe(
        listOf(
          BeneficiaryLockedClaimFake
        )
      )
    }
  }

  test("displays pending benefactor claim warning card") {
    inheritanceService.claims.value = listOf(BenefactorPendingClaimFake)

    inheritanceCardService.cardsToDisplay.test {
      awaitItem().shouldBe(
        listOf(
          BenefactorPendingClaimFake
        )
      )
    }
  }

  test("displays locked/complete benefactor claim card when complete") {
    inheritanceService.claims.value = listOf(BenefactorCompleteClaim)

    inheritanceCardService.cardsToDisplay.test {
      awaitItem().shouldBe(
        listOf(
          BenefactorCompleteClaim
        )
      )
    }
  }

  test("displays locked/compelte benefactor claim card when locked") {
    inheritanceService.claims.value = listOf(BenefactorLockedClaimFake)

    inheritanceCardService.cardsToDisplay.test {
      awaitItem().shouldBe(
        listOf(
          BenefactorLockedClaimFake
        )
      )
    }
  }
})
