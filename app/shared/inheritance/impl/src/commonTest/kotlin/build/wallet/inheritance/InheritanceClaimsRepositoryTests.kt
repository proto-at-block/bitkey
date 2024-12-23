package build.wallet.inheritance

import app.cash.turbine.test
import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus
import build.wallet.bitkey.inheritance.BenefactorPendingClaimFake
import build.wallet.bitkey.inheritance.BeneficiaryLockedClaimFake
import build.wallet.bitkey.inheritance.BeneficiaryPendingClaimFake
import build.wallet.bitkey.inheritance.InheritanceClaims
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.f8e.inheritance.RetrieveInheritanceClaimsF8EClientFake
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.InheritanceFeatureFlag
import build.wallet.feature.setFlagValue
import com.github.michaelbull.result.Ok
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.time.Duration.Companion.minutes

class InheritanceClaimsRepositoryTests : FunSpec({
  val testScope = TestScope()
  val accountService = AccountServiceFake()
  val retrieveInheritanceClaimsF8eClient = RetrieveInheritanceClaimsF8EClientFake()
  val featureFlagDao = FeatureFlagDaoFake()
  val featureFlag = InheritanceFeatureFlag(featureFlagDao)

  beforeSpec {
    featureFlag.setFlagValue(true)
  }

  test("Inheritance Claims load from database") {
    val inheritanceClaimsDao = InheritanceClaimsDaoFake()
    val databaseClaims = InheritanceClaims(
      beneficiaryClaims = listOf(BeneficiaryPendingClaimFake),
      benefactorClaims = listOf(BenefactorPendingClaimFake)
    )
    inheritanceClaimsDao.setInheritanceClaims(databaseClaims)
    val repository = InheritanceClaimsRepositoryImpl(
      accountService = accountService,
      inheritanceClaimsDao = inheritanceClaimsDao,
      retrieveInheritanceClaimsF8eClient = retrieveInheritanceClaimsF8eClient,
      stateScope = testScope,
      inheritanceFeatureFlag = featureFlag
    )

    repository.claims.test {
      testScope.runCurrent()
      val claims = awaitItem()
      claims.isOk.shouldBeTrue()
      claims.value.shouldBe(databaseClaims)
    }
  }

  test("Inheritance Claims load from F8e after database") {
    val inheritanceClaimsDao = InheritanceClaimsDaoFake()
    inheritanceClaimsDao.setInheritanceClaims(InheritanceClaims.EMPTY)
    val updatedClaimsList = InheritanceClaims(
      beneficiaryClaims = listOf(BeneficiaryPendingClaimFake),
      benefactorClaims = listOf(BenefactorPendingClaimFake)
    )
    accountService.accountState.value = Ok(AccountStatus.ActiveAccount(FullAccountMock))
    retrieveInheritanceClaimsF8eClient.response = Ok(updatedClaimsList)
    val repository = InheritanceClaimsRepositoryImpl(
      accountService = accountService,
      inheritanceClaimsDao = inheritanceClaimsDao,
      retrieveInheritanceClaimsF8eClient = retrieveInheritanceClaimsF8eClient,
      stateScope = testScope,
      inheritanceFeatureFlag = featureFlag
    )

    repository.claims.test {
      testScope.runCurrent()
      withClue("Initial database result") {
        val claims = awaitItem()
        claims.isOk.shouldBeTrue()
        claims.value.shouldBe(InheritanceClaims.EMPTY)
      }

      withClue("Update from F8E") {
        val claims = awaitItem()
        claims.isOk.shouldBeTrue()
        claims.value.shouldBe(updatedClaimsList)
      }

      withClue("F8e continues to sync while subscribed") {
        val secondUpdate = InheritanceClaims(
          beneficiaryClaims = listOf(),
          benefactorClaims = listOf(BenefactorPendingClaimFake)
        )
        retrieveInheritanceClaimsF8eClient.response = Ok(secondUpdate)
        testScope.advanceTimeBy(2.minutes)
        val claims = awaitItem()
        claims.isOk.shouldBeTrue()
        claims.value.shouldBe(secondUpdate)
      }
    }
  }

  test("Inheritance Claims updated locally") {
    val inheritanceClaimsDao = InheritanceClaimsDaoFake()
    val initialClaimsList = InheritanceClaims(
      beneficiaryClaims = listOf(BeneficiaryPendingClaimFake),
      benefactorClaims = listOf(BenefactorPendingClaimFake)
    )
    accountService.accountState.value = Ok(AccountStatus.ActiveAccount(FullAccountMock))
    retrieveInheritanceClaimsF8eClient.response = Ok(initialClaimsList)
    val repository = InheritanceClaimsRepositoryImpl(
      accountService = accountService,
      inheritanceClaimsDao = inheritanceClaimsDao,
      retrieveInheritanceClaimsF8eClient = retrieveInheritanceClaimsF8eClient,
      stateScope = testScope,
      inheritanceFeatureFlag = featureFlag
    )

    repository.claims.test {
      testScope.runCurrent()
      withClue("Initial database result") {
        val claims = awaitItem()
        claims.isOk.shouldBeTrue()
        claims.value.shouldBe(InheritanceClaims.EMPTY)
      }

      withClue("Initial Update from F8E") {
        val claims = awaitItem()
        claims.isOk.shouldBeTrue()
        claims.value.shouldBe(initialClaimsList)
      }

      withClue("Update claim to locked state locally") {
        val updatedClaim = BeneficiaryLockedClaimFake.copy(
          claimId = BeneficiaryPendingClaimFake.claimId
        )
        repository.updateSingleClaim(updatedClaim)
        testScope.runCurrent()
        val claims = awaitItem()
        claims.isOk.shouldBeTrue()
        claims.value.shouldBe(
          InheritanceClaims(
            beneficiaryClaims = listOf(updatedClaim),
            benefactorClaims = listOf(BenefactorPendingClaimFake)
          )
        )
      }
    }
  }

  test("feature flag test") {
    val inheritanceClaimsDao = InheritanceClaimsDaoFake()
    val initialClaimsList = InheritanceClaims(
      beneficiaryClaims = listOf(BeneficiaryPendingClaimFake),
      benefactorClaims = listOf(BenefactorPendingClaimFake)
    )
    featureFlag.setFlagValue(false)
    accountService.accountState.value = Ok(AccountStatus.ActiveAccount(FullAccountMock))
    retrieveInheritanceClaimsF8eClient.response = Ok(initialClaimsList)
    val repository = InheritanceClaimsRepositoryImpl(
      accountService = accountService,
      inheritanceClaimsDao = inheritanceClaimsDao,
      retrieveInheritanceClaimsF8eClient = retrieveInheritanceClaimsF8eClient,
      stateScope = testScope,
      inheritanceFeatureFlag = featureFlag
    )

    repository.claims.test {
      testScope.runCurrent()
      expectNoEvents()
    }
  }
})
