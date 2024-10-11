package build.wallet.inheritance

import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.keybox.KeyboxMock2
import build.wallet.bitkey.relationships.EndorsedTrustedContactFake1
import build.wallet.coroutines.turbine.turbines
import build.wallet.db.DbQueryError
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.InheritanceFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.keybox.KeyboxDaoMock
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class InheritanceSyncWorkerTest : FunSpec({
  val inheritanceService = InheritanceServiceMock(
    syncCalls = turbines.create("Sync Calls")
  )
  val inheritanceRelationshipsProvider = InheritanceRelationshipsProviderFake()
  val featureFlagDao = FeatureFlagDaoFake()
  val featureFlag = InheritanceFeatureFlag(featureFlagDao)
  val keyboxDao = KeyboxDaoMock(
    turbine = turbines::create
  )
  val worker = InheritanceSyncWorker(
    inheritanceService = inheritanceService,
    inheritanceRelationshipsProvider = inheritanceRelationshipsProvider,
    keyboxDao = keyboxDao,
    featureFlag = featureFlag
  )

  beforeTest {
    featureFlag.setFlagValue(true)
    keyboxDao.activeKeybox.value = Ok(KeyboxMock)
  }

  test("Inheritance Syncs when worker is started") {
    runTest {
      val workJob = launch { worker.executeWork() }
      runCurrent()
      inheritanceRelationshipsProvider.endorsedInheritanceContacts.emit(listOf(EndorsedTrustedContactFake1))
      inheritanceService.syncCalls.awaitItem()

      workJob.cancelAndJoin()
    }
  }

  test("Inheritance sync is not run without feature flag") {
    runTest {
      featureFlag.setFlagValue(false)

      val workJob = launch { worker.executeWork() }
      runCurrent()
      inheritanceRelationshipsProvider.endorsedInheritanceContacts.emit(listOf(EndorsedTrustedContactFake1))
      runCurrent()

      inheritanceService.syncCalls.expectNoEvents()

      workJob.cancelAndJoin()
    }
  }

  test("Inheritance sync is not run with inactive keybox") {
    runTest {
      keyboxDao.activeKeybox.value = Ok(null)

      val workJob = launch { worker.executeWork() }
      runCurrent()
      inheritanceRelationshipsProvider.endorsedInheritanceContacts.emit(listOf(EndorsedTrustedContactFake1))
      runCurrent()

      inheritanceService.syncCalls.expectNoEvents()

      workJob.cancelAndJoin()
    }
  }

  test("Inheritance sync is not run with keybox error") {
    runTest {
      keyboxDao.activeKeybox.value = Err(DbQueryError(null))

      val workJob = launch { worker.executeWork() }
      runCurrent()
      inheritanceRelationshipsProvider.endorsedInheritanceContacts.emit(listOf(EndorsedTrustedContactFake1))
      runCurrent()

      inheritanceService.syncCalls.expectNoEvents()

      workJob.cancelAndJoin()
    }
  }

  test("Inheritance Re-Syncs when keys change") {
    runTest {
      val workJob = launch { worker.executeWork() }
      runCurrent()
      inheritanceRelationshipsProvider.endorsedInheritanceContacts.emit(listOf(EndorsedTrustedContactFake1))
      inheritanceService.syncCalls.awaitItem().shouldBe(KeyboxMock)
      keyboxDao.activeKeybox.value = Ok(KeyboxMock2)
      inheritanceService.syncCalls.awaitItem().shouldBe(KeyboxMock2)

      workJob.cancelAndJoin()
    }
  }

  test("Inheritance Re-Syncs when contacts change") {
    runTest {
      val workJob = launch { worker.executeWork() }
      runCurrent()
      inheritanceRelationshipsProvider.endorsedInheritanceContacts.emit(listOf(EndorsedTrustedContactFake1))
      inheritanceService.syncCalls.awaitItem()
      inheritanceRelationshipsProvider.endorsedInheritanceContacts.emit(
        listOf(
          EndorsedTrustedContactFake1.copy(
            relationshipId = "new-contact"
          )
        )
      )
      inheritanceService.syncCalls.awaitItem()

      workJob.cancelAndJoin()
    }
  }

  test("Inheritance failures retry until successful") {
    runTest {
      inheritanceService.syncResult = Err(Error("Test Error"))

      val workJob = launch { worker.executeWork() }

      runCurrent()
      inheritanceRelationshipsProvider.endorsedInheritanceContacts.emit(listOf(EndorsedTrustedContactFake1))
      inheritanceService.syncCalls.awaitItem()

      runCurrent()
      inheritanceService.syncCalls.expectNoEvents()

      advanceTimeBy(1.minutes)
      inheritanceService.syncCalls.awaitItem()

      runCurrent()
      inheritanceService.syncCalls.expectNoEvents()

      workJob.cancelAndJoin()
    }
  }
})
