package build.wallet.firmware

import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.queueprocessor.Queue
import build.wallet.sqldelight.inMemorySqlDriver
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8

class FirmwareTelemetryQueueImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()
  lateinit var queue: Queue<FirmwareTelemetryEvent>
  val events =
    mutableListOf<FirmwareTelemetryEvent>().apply {
      addAll(
        (0..9).map { index ->
          FirmwareTelemetryEvent(index.toString(), "event_$index".encodeUtf8())
        }
      )
    }

  beforeTest {
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    queue = FirmwareTelemetryQueueImpl(databaseProvider)
  }

  test("passing negative num to take throws") {
    shouldThrow<IllegalArgumentException> {
      queue.take(-1).unwrap()
    }
  }

  test("passing negative num to removeFirst throws") {
    shouldThrow<IllegalArgumentException> {
      queue.removeFirst(-1).unwrap()
    }
  }

  test("insert event") {
    val event = events[0]
    queue.append(event).unwrap()
    queue.take(1).shouldBe(Ok(listOf(event)))
  }

  test("get all events") {
    events.forEachIndexed { _, event ->
      queue.append(event).unwrap()
    }
    queue.take(events.size).shouldBe(Ok(events))
  }

  test("batch should be subset of queue") {
    events.forEachIndexed { _, event ->
      queue.append(event).unwrap()
    }
    val eventsBatchLimit = (1 until events.size).random()
    queue.take(eventsBatchLimit).unwrap().shouldHaveSize(eventsBatchLimit)

    queue.take(eventsBatchLimit).unwrap().forEach { event ->
      queue.take(events.size).unwrap().shouldContain(event)
    }
  }

  test("take doesnt delete") {
    events.forEachIndexed { _, event ->
      queue.append(event).unwrap()
    }

    queue.take(events.size).shouldBe(Ok(events))
    queue.take(events.size).shouldBe(Ok(events))
  }

  test("remove") {
    queue.append(events[0]).unwrap()

    queue.removeFirst(1).unwrap()

    queue.take(1).shouldBe(Ok(emptyList()))
  }

  test("remove partial") {
    queue.append(events[0]).unwrap()
    queue.append(events[1]).unwrap()
    queue.append(events[3]).unwrap()

    queue.removeFirst(2).unwrap()

    queue.take(1).shouldBe(Ok(listOf(events[3])))
  }
})
