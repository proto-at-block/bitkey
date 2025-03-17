@file:OptIn(DelicateCoroutinesApi::class)

package build.wallet.inheritance

import app.cash.turbine.test
import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus
import build.wallet.bitkey.inheritance.BenefactorPendingClaimFake
import build.wallet.bitkey.inheritance.BeneficiaryLockedClaimFake
import build.wallet.bitkey.inheritance.BeneficiaryPendingClaimFake
import build.wallet.bitkey.inheritance.InheritanceClaims
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.awaitNoEvents
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.f8e.inheritance.RetrieveInheritanceClaimsF8EClientFake
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.InheritanceFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Ok
import io.kotest.assertions.withClue
import io.kotest.core.coroutines.backgroundScope
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class InheritanceClaimsRepositoryTests : FunSpec({
  // TODO(W-10571): use real dispatcher. There's currently a race condition in the sync
  //                implementation which fails tests when using real dispatcher (likely actual race condition bug).
  coroutineTestScope = true
  val accountService = AccountServiceFake()
  val retrieveInheritanceClaimsF8eClient = RetrieveInheritanceClaimsF8EClientFake()
  val featureFlagDao = FeatureFlagDaoFake()
  val featureFlag = InheritanceFeatureFlag(featureFlagDao)
  val inheritanceClaimsDao = InheritanceClaimsDaoFake()
  val syncFrequency = 100.milliseconds

  beforeTest {
    accountService.reset()
    retrieveInheritanceClaimsF8eClient.reset()
    featureFlagDao.reset()
    featureFlag.setFlagValue(true)
    inheritanceClaimsDao.clear()
  }

  test("Inheritance Claims load from database") {
    val databaseClaims = InheritanceClaims(
      beneficiaryClaims = listOf(BeneficiaryPendingClaimFake),
      benefactorClaims = listOf(BenefactorPendingClaimFake)
    )
    inheritanceClaimsDao.setInheritanceClaims(databaseClaims)
    val repository = InheritanceClaimsRepositoryImpl(
      accountService = accountService,
      inheritanceClaimsDao = inheritanceClaimsDao,
      retrieveInheritanceClaimsF8eClient = retrieveInheritanceClaimsF8eClient,
      stateScope = backgroundScope,
      inheritanceFeatureFlag = featureFlag,
      inheritanceSyncFrequency = InheritanceSyncFrequency(syncFrequency)
    )

    repository.claims.test {
      awaitItem().shouldBeOk(databaseClaims)
    }
  }

  test("Inheritance Claims load from F8e after database") {
    inheritanceClaimsDao.setInheritanceClaims(InheritanceClaims.EMPTY)
    val updatedClaimsList = InheritanceClaims(
      beneficiaryClaims = listOf(BeneficiaryPendingClaimFake),
      benefactorClaims = listOf(BenefactorPendingClaimFake)
    )
    accountService.accountState.value = Ok(AccountStatus.ActiveAccount(FullAccountMock))
    val repository = InheritanceClaimsRepositoryImpl(
      accountService = accountService,
      inheritanceClaimsDao = inheritanceClaimsDao,
      retrieveInheritanceClaimsF8eClient = retrieveInheritanceClaimsF8eClient,
      stateScope = backgroundScope,
      inheritanceFeatureFlag = featureFlag,
      inheritanceSyncFrequency = InheritanceSyncFrequency(syncFrequency)
    )

    repository.claims.test {
      withClue("Initial database result") {
        awaitItem().shouldBeOk(InheritanceClaims.EMPTY)
      }

      retrieveInheritanceClaimsF8eClient.response = Ok(updatedClaimsList)

      withClue("Update from F8E") {
        awaitUntil(Ok(updatedClaimsList))
      }

      withClue("F8e continues to sync while subscribed") {
        val secondUpdate = InheritanceClaims(
          beneficiaryClaims = listOf(),
          benefactorClaims = listOf(BenefactorPendingClaimFake)
        )
        retrieveInheritanceClaimsF8eClient.response = Ok(secondUpdate)
        awaitItem().shouldBeOk(secondUpdate)
      }
    }
  }

  test("Inheritance Claims updated locally") {
    val initialClaimsList = InheritanceClaims(
      beneficiaryClaims = listOf(BeneficiaryPendingClaimFake),
      benefactorClaims = listOf(BenefactorPendingClaimFake)
    )
    accountService.accountState.value = Ok(AccountStatus.ActiveAccount(FullAccountMock))
    val repository = InheritanceClaimsRepositoryImpl(
      accountService = accountService,
      inheritanceClaimsDao = inheritanceClaimsDao,
      retrieveInheritanceClaimsF8eClient = retrieveInheritanceClaimsF8eClient,
      stateScope = backgroundScope,
      inheritanceFeatureFlag = featureFlag,
      inheritanceSyncFrequency = InheritanceSyncFrequency(syncFrequency)
    )

    repository.claims.test {
      withClue("Initial database result") {
        awaitUntil(Ok(InheritanceClaims.EMPTY))
      }
      retrieveInheritanceClaimsF8eClient.response = Ok(initialClaimsList)

      withClue("Initial Update from F8E") {
        awaitItem().shouldBeOk(initialClaimsList)
      }

      withClue("Update claim to locked state locally") {
        val updatedClaim = BeneficiaryLockedClaimFake.copy(
          claimId = BeneficiaryPendingClaimFake.claimId
        )
        repository.updateSingleClaim(updatedClaim)
        awaitItem().shouldBeOk(
          InheritanceClaims(
            beneficiaryClaims = listOf(updatedClaim),
            benefactorClaims = listOf(BenefactorPendingClaimFake)
          )
        )
      }
    }
  }

  test("feature flag test") {
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
      stateScope = backgroundScope,
      inheritanceFeatureFlag = featureFlag,
      inheritanceSyncFrequency = InheritanceSyncFrequency(syncFrequency)
    )

    repository.claims.test {
      delay(syncFrequency)
      awaitNoEvents()
    }
  }
})
