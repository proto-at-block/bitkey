package build.wallet.inheritance

import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.keybox.KeyboxMock2
import build.wallet.bitkey.relationships.EndorsedTrustedContactFake1
import build.wallet.coroutines.turbine.turbines
import build.wallet.db.DbQueryError
import build.wallet.keybox.KeyboxDaoMock
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.coroutines.backgroundScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class InheritanceMaterialSyncWorkerTest : FunSpec({
  // TODO(W-10571): use real dispatcher. There's currently a race condition in the sync
  //                implementation which fails tests when using real dispatcher (likely actual race condition bug).
  coroutineTestScope = true
  val inheritanceService = InheritanceServiceMock(
    syncCalls = turbines.create("Sync Calls")
  )
  val inheritanceRelationshipsProvider = InheritanceRelationshipsProviderFake()
  val keyboxDao = KeyboxDaoMock(turbine = turbines::create)
  val syncFrequency = 100.milliseconds
  val worker = InheritanceMaterialSyncWorkerImpl(
    inheritanceService = inheritanceService,
    inheritanceRelationshipsProvider = inheritanceRelationshipsProvider,
    keyboxDao = keyboxDao,
    inheritanceSyncFrequency = InheritanceSyncFrequency(syncFrequency)
  )

  beforeTest {
    keyboxDao.activeKeybox.value = Ok(KeyboxMock)
  }

  test("Inheritance Syncs when worker is started") {
    val workJob = launch { worker.executeWork() }
    testCoroutineScheduler.runCurrent()
    inheritanceRelationshipsProvider.endorsedInheritanceContacts.emit(
      listOf(
        EndorsedTrustedContactFake1
      )
    )
    inheritanceService.syncCalls.awaitItem()

    workJob.cancelAndJoin()
  }

  test("Inheritance sync is not run with inactive keybox") {
    keyboxDao.activeKeybox.value = Ok(null)

    backgroundScope.launch { worker.executeWork() }
    testCoroutineScheduler.runCurrent()
    inheritanceRelationshipsProvider.endorsedInheritanceContacts.emit(
      listOf(
        EndorsedTrustedContactFake1
      )
    )
    testCoroutineScheduler.runCurrent()

    inheritanceService.syncCalls.expectNoEvents()
  }

  test("Inheritance sync is not run with keybox error") {
    keyboxDao.activeKeybox.value = Err(DbQueryError(null))

    backgroundScope.launch { worker.executeWork() }
    testCoroutineScheduler.runCurrent()
    inheritanceRelationshipsProvider.endorsedInheritanceContacts.emit(
      listOf(
        EndorsedTrustedContactFake1
      )
    )
    testCoroutineScheduler.runCurrent()

    inheritanceService.syncCalls.expectNoEvents()
  }

  test("Inheritance Re-Syncs when keys change") {
    backgroundScope.launch { worker.executeWork() }
    testCoroutineScheduler.runCurrent()
    inheritanceRelationshipsProvider.endorsedInheritanceContacts.emit(
      listOf(
        EndorsedTrustedContactFake1
      )
    )
    inheritanceService.syncCalls.awaitItem().shouldBe(KeyboxMock)
    keyboxDao.activeKeybox.value = Ok(KeyboxMock2)
    inheritanceService.syncCalls.awaitItem().shouldBe(KeyboxMock2)
  }

  test("Inheritance Re-Syncs when contacts change") {
    backgroundScope.launch { worker.executeWork() }
    testCoroutineScheduler.runCurrent()
    inheritanceRelationshipsProvider.endorsedInheritanceContacts.emit(
      listOf(
        EndorsedTrustedContactFake1
      )
    )
    inheritanceService.syncCalls.awaitItem()
    inheritanceRelationshipsProvider.endorsedInheritanceContacts.emit(
      listOf(
        EndorsedTrustedContactFake1.copy(
          relationshipId = "new-contact"
        )
      )
    )
    inheritanceService.syncCalls.awaitItem()
  }

  test("Inheritance failures retry until successful") {
    inheritanceService.syncResult = Err(Error("Test Error"))

    backgroundScope.launch { worker.executeWork() }

    testCoroutineScheduler.runCurrent()
    inheritanceRelationshipsProvider.endorsedInheritanceContacts.emit(
      listOf(
        EndorsedTrustedContactFake1
      )
    )
    inheritanceService.syncCalls.awaitItem()

    testCoroutineScheduler.runCurrent()
    inheritanceService.syncCalls.expectNoEvents()

    testCoroutineScheduler.advanceTimeBy(syncFrequency)
    inheritanceService.syncCalls.awaitItem()

    testCoroutineScheduler.runCurrent()
    inheritanceService.syncCalls.expectNoEvents()

    testCoroutineScheduler.advanceTimeBy(syncFrequency)
    inheritanceService.syncCalls.awaitItem()
  }
})
