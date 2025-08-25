package build.wallet.logging.prod

import build.wallet.coroutines.createBackgroundScope
import build.wallet.logging.LogLevel
import build.wallet.logging.dev.LogStore
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.datetime.Clock

class BoundedInMemoryLogStoreImplTests : FunSpec({
  test("log writer initialization succeeds") {
    BoundedInMemoryLogStoreImpl(createBackgroundScope())
  }

  test("emitting flow events on record not supported") {
    val store = BoundedInMemoryLogStoreImpl(createBackgroundScope())
    store.record(LogStore.Entity(Clock.System.now(), LogLevel.Debug, "tag", null, "message"))

    store.logs(LogLevel.Debug, null).collect {
      it.size shouldBe 1
      it[0].message shouldBe "Dynamic logs not supported, use another LogStore"
    }
  }

  test("log writer record then get") {
    val scope = TestScope()
    val store = BoundedInMemoryLogStoreImpl(scope)
    store.record(LogStore.Entity(Clock.System.now(), LogLevel.Debug, "tag", null, "message"))

    scope.advanceUntilIdle()

    store.getCurrentLogs(LogLevel.Debug, null).size shouldBe 1
  }

  test("log writer record then get higher verbosity level") {
    val scope = TestScope()
    val store = BoundedInMemoryLogStoreImpl(scope)
    store.record(LogStore.Entity(Clock.System.now(), LogLevel.Debug, "tag", null, "debug message"))
    store.record(LogStore.Entity(Clock.System.now(), LogLevel.Warn, "tag", null, "warn message"))

    scope.advanceUntilIdle()

    store.getCurrentLogs(LogLevel.Info, null).size shouldBe 1
  }

  test("log writer record then get by tag") {
    val scope = TestScope()
    val store = BoundedInMemoryLogStoreImpl(scope)
    store.record(LogStore.Entity(Clock.System.now(), LogLevel.Debug, "tag1", null, "debug message"))
    store.record(LogStore.Entity(Clock.System.now(), LogLevel.Debug, "tag2", null, "warn message"))

    scope.advanceUntilIdle()

    store.getCurrentLogs(LogLevel.Debug, null).size shouldBe 2
    store.getCurrentLogs(LogLevel.Debug, "tag1").size shouldBe 1
  }

  test("log only grows to max entries") {
    val scope = TestScope()
    val store = BoundedInMemoryLogStoreImpl(scope)
    store.maxLogEntries = 2

    store.record(LogStore.Entity(Clock.System.now(), LogLevel.Debug, "tag", null, "0"))
    store.record(LogStore.Entity(Clock.System.now(), LogLevel.Debug, "tag", null, "1"))

    scope.advanceUntilIdle()

    store.getCurrentLogs(LogLevel.Debug, null).size shouldBe 2

    // add entry 2, ensure entry 0 is removed
    store.record(LogStore.Entity(Clock.System.now(), LogLevel.Debug, "tag", null, "2"))

    scope.advanceUntilIdle()

    val entities = store.getCurrentLogs(LogLevel.Debug, null)
    entities.size shouldBe 2
    entities.first().message shouldBe "1"
    entities.last().message shouldBe "2"
  }
})
