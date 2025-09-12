@file:OptIn(DelicateCoroutinesApi::class)

package build.wallet.inheritance

import app.cash.turbine.test
import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus
import build.wallet.bitkey.inheritance.BenefactorPendingClaimFake
import build.wallet.bitkey.inheritance.BeneficiaryLockedClaimBothDescriptorsFake
import build.wallet.bitkey.inheritance.BeneficiaryPendingClaimFake
import build.wallet.bitkey.inheritance.InheritanceClaims
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.f8e.inheritance.RetrieveInheritanceClaimsF8EClientFake
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import io.kotest.assertions.withClue
import io.kotest.core.coroutines.backgroundScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.DelicateCoroutinesApi

class InheritanceClaimsRepositoryTests : FunSpec({
  // TODO(W-10571): use real dispatcher. There's currently a race condition in the sync
  //                implementation which fails tests when using real dispatcher (likely actual race condition bug).
  coroutineTestScope = true
  val accountService = AccountServiceFake()
  val retrieveInheritanceClaimsF8eClient = RetrieveInheritanceClaimsF8EClientFake()
  val inheritanceClaimsDao = InheritanceClaimsDaoFake()

  beforeTest {
    accountService.reset()
    retrieveInheritanceClaimsF8eClient.reset()
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
      stateScope = backgroundScope
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
      stateScope = backgroundScope
    )

    repository.claims.test {
      withClue("Initial database result") {
        awaitItem().shouldBeOk(InheritanceClaims.EMPTY)
      }

      retrieveInheritanceClaimsF8eClient.response = Ok(updatedClaimsList)

      withClue("Update from F8E") {
        repository.syncServerClaims()
        awaitUntil(Ok(updatedClaimsList))
      }

      withClue("2nd update from F8e") {
        val secondUpdate = InheritanceClaims(
          beneficiaryClaims = listOf(),
          benefactorClaims = listOf(BenefactorPendingClaimFake)
        )
        retrieveInheritanceClaimsF8eClient.response = Ok(secondUpdate)
        repository.syncServerClaims()
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
      stateScope = backgroundScope
    )

    repository.claims.test {
      withClue("Initial database result") {
        awaitUntil(Ok(InheritanceClaims.EMPTY))
      }
      retrieveInheritanceClaimsF8eClient.response = Ok(initialClaimsList)
      repository.syncServerClaims()

      withClue("Initial Update from F8E") {
        awaitItem().shouldBeOk(initialClaimsList)
      }

      withClue("Update claim to locked state locally") {
        val updatedClaim = BeneficiaryLockedClaimBothDescriptorsFake.copy(
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

  test("sync server claims updates the local caches appropriately") {
    val serverClaimsList = InheritanceClaims(
      beneficiaryClaims = listOf(BeneficiaryPendingClaimFake),
      benefactorClaims = listOf(BenefactorPendingClaimFake)
    )

    accountService.accountState.value = Ok(AccountStatus.ActiveAccount(FullAccountMock))
    val repository = InheritanceClaimsRepositoryImpl(
      accountService = accountService,
      inheritanceClaimsDao = inheritanceClaimsDao,
      retrieveInheritanceClaimsF8eClient = retrieveInheritanceClaimsF8eClient,
      stateScope = backgroundScope
    )

    repository.claims.test {
      awaitUntil(Ok(InheritanceClaims.EMPTY))
      inheritanceClaimsDao.pendingBenefactorClaims.value.get().shouldBeEmpty()
      inheritanceClaimsDao.pendingBeneficiaryClaims.value.get().shouldBeEmpty()

      retrieveInheritanceClaimsF8eClient.response = Ok(serverClaimsList)
      repository.syncServerClaims()

      awaitUntil(Ok(serverClaimsList))
      inheritanceClaimsDao.pendingBenefactorClaims.value.get().shouldContainExactly(BenefactorPendingClaimFake)
      inheritanceClaimsDao.pendingBeneficiaryClaims.value.get().shouldContainExactly(BeneficiaryPendingClaimFake)
    }
  }
})
