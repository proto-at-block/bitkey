package build.wallet.inheritance

import app.cash.turbine.test
import build.wallet.bitkey.inheritance.*
import build.wallet.compose.collections.immutableListOf
import build.wallet.coroutines.createBackgroundScope
import build.wallet.coroutines.turbine.turbines
import build.wallet.store.KeyValueStoreFactoryFake
import build.wallet.time.someInstant
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.days

class InheritanceCardServiceTests : FunSpec({
  val inheritanceService = InheritanceServiceMock(
    syncCalls = turbines.create("Sync Calls")
  )
  val keyValueStore = KeyValueStoreFactoryFake()

  beforeTest {
    inheritanceService.reset()
    keyValueStore.clear()
  }

  test("don't display dismissed pending beneficiary claim card") {
    val inheritanceCardService = InheritanceCardServiceImpl(
      coroutineScope = createBackgroundScope(),
      inheritanceService = inheritanceService,
      keyValueStoreFactory = keyValueStore
    )

    inheritanceService.claimsSnapshot.value = ClaimsSnapshot(
      timestamp = someInstant,
      claims = InheritanceClaims(
        benefactorClaims = immutableListOf(),
        beneficiaryClaims = immutableListOf(
          BeneficiaryPendingClaimFake
        )
      )
    )
    inheritanceCardService.dismissPendingBeneficiaryClaimCard(InheritanceClaimId("claim-benefactor-pending-id"))

    inheritanceCardService.cardsToDisplay.test {
      awaitItem().shouldBe(emptyList())
      expectNoEvents()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("display pending beneficiary claim card with one dismissed") {
    val inheritanceCardService = InheritanceCardServiceImpl(
      coroutineScope = createBackgroundScope(),
      inheritanceService = inheritanceService,
      keyValueStoreFactory = keyValueStore
    )

    val pendingClaimTwo = BeneficiaryPendingClaimFake.copy(
      claimId = InheritanceClaimId("claim-benefactor-pending-id2"),
      delayEndTime = someInstant.plus(360.days)
    )
    inheritanceService.claimsSnapshot.value = ClaimsSnapshot(
      timestamp = someInstant,
      claims = InheritanceClaims(
        benefactorClaims = immutableListOf(),
        beneficiaryClaims = immutableListOf(
          BeneficiaryPendingClaimFake,
          pendingClaimTwo
        )
      )
    )
    inheritanceCardService.dismissPendingBeneficiaryClaimCard(InheritanceClaimId("claim-benefactor-pending-id"))

    inheritanceCardService.cardsToDisplay.test {
      awaitItem().shouldBe(listOf(pendingClaimTwo))
    }
  }

  test("displays locked beneficiary claim card even if pending beneficiary claim card was dismissed") {
    val inheritanceCardService = InheritanceCardServiceImpl(
      coroutineScope = createBackgroundScope(),
      inheritanceService = inheritanceService,
      keyValueStoreFactory = keyValueStore
    )

    inheritanceService.claimsSnapshot.value = ClaimsSnapshot(
      timestamp = someInstant,
      claims = InheritanceClaims(
        benefactorClaims = immutableListOf(),
        beneficiaryClaims = immutableListOf(
          BeneficiaryLockedClaimBothDescriptorsFake
        )
      )
    )
    inheritanceCardService.dismissPendingBeneficiaryClaimCard(InheritanceClaimId("claim-benefactor-pending-id"))

    inheritanceCardService.cardsToDisplay.test {
      awaitItem().shouldBe(
        listOf(
          BeneficiaryLockedClaimBothDescriptorsFake
        )
      )
    }
  }

  test("displays pending benefactor claim warning card") {
    val inheritanceCardService = InheritanceCardServiceImpl(
      coroutineScope = createBackgroundScope(),
      inheritanceService = inheritanceService,
      keyValueStoreFactory = keyValueStore
    )

    inheritanceService.claimsSnapshot.value = ClaimsSnapshot(
      timestamp = someInstant,
      claims = InheritanceClaims(
        benefactorClaims = immutableListOf(
          BenefactorPendingClaimFake
        ),
        beneficiaryClaims = immutableListOf()
      )
    )

    inheritanceCardService.cardsToDisplay.test {
      awaitItem().shouldBe(
        listOf(
          BenefactorPendingClaimFake
        )
      )
    }
  }

  test("displays locked/complete benefactor claim card when complete") {
    val inheritanceCardService = InheritanceCardServiceImpl(
      coroutineScope = createBackgroundScope(),
      inheritanceService = inheritanceService,
      keyValueStoreFactory = keyValueStore
    )

    inheritanceService.claimsSnapshot.value = ClaimsSnapshot(
      timestamp = someInstant,
      claims = InheritanceClaims(
        benefactorClaims = immutableListOf(
          BenefactorCompleteClaim
        ),
        beneficiaryClaims = immutableListOf()
      )
    )

    inheritanceCardService.cardsToDisplay.test {
      awaitItem().shouldBe(
        listOf(
          BenefactorCompleteClaim
        )
      )
    }
  }

  test("displays locked/compelte benefactor claim card when locked") {
    val inheritanceCardService = InheritanceCardServiceImpl(
      coroutineScope = createBackgroundScope(),
      inheritanceService = inheritanceService,
      keyValueStoreFactory = keyValueStore
    )

    inheritanceService.claimsSnapshot.value = ClaimsSnapshot(
      timestamp = someInstant,
      claims = InheritanceClaims(
        benefactorClaims = immutableListOf(
          BenefactorLockedClaimFake
        ),
        beneficiaryClaims = immutableListOf()
      )
    )

    inheritanceCardService.cardsToDisplay.test {
      awaitItem().shouldBe(
        listOf(
          BenefactorLockedClaimFake
        )
      )
    }
  }
})
