package build.wallet.f8e.sync

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock2
import build.wallet.bitkey.keybox.LiteAccountMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class SyncSequencerTests : FunSpec({
  // TODO(W-10571): use real dispatcher.
  coroutineTestScope = true

  val fullAccount1 = FullAccountMock
  val fullAccount2 = FullAccountMock.copy(
    accountId = FullAccountId("server-id-2"),
    keybox = KeyboxMock2
  )
  val fullAccount1Copy = fullAccount1.copy()
  val liteAccount = LiteAccountMock

  test("second call is not executed if first call is running and account IDs differ") {
    val channel = Channel<Int>()

    val sync = F8eSyncSequencer()
    launch {
      sync.run(fullAccount1) { channel.send(1) }
    }
    testCoroutineScheduler.runCurrent()

    launch {
      sync.run(fullAccount2) { channel.send(2) }
    }
    testCoroutineScheduler.runCurrent()

    channel.receive().shouldBeEqual(1)
  }

  test("second call is not executed if first call is running and account types differ") {
    val channel = Channel<Int>()

    val sync = F8eSyncSequencer()
    launch {
      sync.run(fullAccount1) { channel.send(1) }
    }
    testCoroutineScheduler.runCurrent()

    launch {
      sync.run(liteAccount) { channel.send(2) }
    }
    testCoroutineScheduler.runCurrent()

    channel.receive().shouldBeEqual(1)
  }

  test("second call queues if first call is running and accounts match") {
    val channel = Channel<Int>()

    val sync = F8eSyncSequencer()
    launch {
      sync.run(fullAccount1) { channel.send(1) }
    }
    testCoroutineScheduler.runCurrent()

    launch {
      sync.run(fullAccount1Copy) { channel.send(2) }
    }
    testCoroutineScheduler.runCurrent()

    channel.receive().shouldBeEqual(1)
    channel.receive().shouldBeEqual(2)
  }

  test("can run second call after first completes") {
    val channel = Channel<Int>()

    val sync = F8eSyncSequencer()
    launch {
      sync.run(fullAccount1) { channel.send(1) }
    }
    testCoroutineScheduler.runCurrent()
    channel.receive().shouldBeEqual(1)

    launch {
      sync.run(fullAccount2) { channel.send(2) }
    }
    channel.receive().shouldBeEqual(2)
  }
})
