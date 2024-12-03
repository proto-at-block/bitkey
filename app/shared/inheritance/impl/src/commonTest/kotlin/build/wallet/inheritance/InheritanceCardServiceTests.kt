package build.wallet.inheritance

import app.cash.turbine.test
import build.wallet.bitkey.inheritance.BeneficiaryLockedClaimFake
import build.wallet.bitkey.inheritance.BeneficiaryPendingClaimFake
import build.wallet.bitkey.inheritance.InheritanceClaimId
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

  test("don't display dismissed pending claim card") {
    inheritanceService.pendingBeneficiaryClaims.value = listOf(BeneficiaryPendingClaimFake)
    inheritanceCardService.dismissPendingClaimCard("claim-benefactor-pending-id")

    inheritanceCardService.claimCardsToDisplay.test {
      awaitItem().shouldBe(emptyList())
      expectNoEvents()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("display pending claim card with one dismissed") {
    val pendingClaimTwo = BeneficiaryPendingClaimFake.copy(
      claimId = InheritanceClaimId("claim-benefactor-pending-id2"),
      delayEndTime = someInstant.plus(360.days)
    )
    inheritanceService.pendingBeneficiaryClaims.value = listOf(
      BeneficiaryPendingClaimFake,
      pendingClaimTwo
    )
    inheritanceCardService.dismissPendingClaimCard("claim-benefactor-pending-id")

    inheritanceCardService.claimCardsToDisplay.test {
      awaitItem().shouldBe(listOf(pendingClaimTwo))
    }
  }

  test("displays locked claim card even if pending claim card was dismissed") {
    inheritanceService.lockedBeneficiaryClaims.value = listOf(BeneficiaryLockedClaimFake)
    inheritanceCardService.dismissPendingClaimCard("claim-benefactor-pending-id")

    inheritanceCardService.claimCardsToDisplay.test {
      awaitItem().shouldBe(
        listOf(
          BeneficiaryLockedClaimFake
        )
      )
    }
  }
})
