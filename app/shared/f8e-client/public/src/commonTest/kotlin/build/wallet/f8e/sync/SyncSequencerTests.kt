package build.wallet.f8e.sync

import build.wallet.bitkey.f8e.FullAccountId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

@OptIn(ExperimentalStdlibApi::class, ExperimentalCoroutinesApi::class)
class SyncSequencerTests : FunSpec({
  val account1 = FullAccountId("1")
  val account2 = FullAccountId("2")

  test(
    "second call throws if first call is running and account IDs differ"
  ).config(coroutineTestScope = true) {
    val channel = Channel<Int>()

    val sync = F8eSyncSequencer()
    launch {
      sync.run(account1) { channel.send(1) }
    }
    testCoroutineScheduler.runCurrent()

    shouldThrow<IllegalStateException> {
      sync.run(account2) { channel.send(2) }
    }

    channel.receive().shouldBeEqual(1)
  }

  test(
    "second call queues if first call is running and account IDs match"
  ).config(coroutineTestScope = true) {
    val channel = Channel<Int>()

    val sync = F8eSyncSequencer()
    launch {
      sync.run(account1) { channel.send(1) }
    }
    testCoroutineScheduler.runCurrent()

    launch {
      sync.run(account1) { channel.send(2) }
    }
    testCoroutineScheduler.runCurrent()

    channel.receive().shouldBeEqual(1)
    channel.receive().shouldBeEqual(2)
  }

  test("can run second call after first completes").config(coroutineTestScope = true) {
    val channel = Channel<Int>()
    val scope = this

    val sync = F8eSyncSequencer()
    launch {
      sync.run(account1) { channel.send(1) }
    }
    testCoroutineScheduler.runCurrent()
    channel.receive().shouldBeEqual(1)

    launch {
      sync.run(account2) { channel.send(2) }
    }
    channel.receive().shouldBeEqual(2)
  }
})
