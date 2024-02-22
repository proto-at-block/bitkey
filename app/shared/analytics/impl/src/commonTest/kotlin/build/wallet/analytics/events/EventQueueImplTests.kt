package build.wallet.analytics.events

import build.wallet.analytics.v1.Event
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.f8e.F8eEnvironment.Production
import build.wallet.queueprocessor.Queue
import build.wallet.sqldelight.inMemorySqlDriver
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class EventQueueImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()
  lateinit var eventQueue: Queue<QueueAnalyticsEvent>
  val events =
    mutableListOf<QueueAnalyticsEvent>().apply {
      addAll(
        (0..9)
          .map(Int::toString)
          .map {
            QueueAnalyticsEvent(
              f8eEnvironment = Production,
              event = Event(it)
            )
          }
      )
    }

  beforeTest {
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    eventQueue = EventQueueImpl(databaseProvider)
  }

  test("passing negative num to take throws") {
    shouldThrow<IllegalArgumentException> {
      eventQueue.take(-1).unwrap()
    }
  }

  test("passing negative num to removeFirst throws") {
    shouldThrow<IllegalArgumentException> {
      eventQueue.removeFirst(-1).unwrap()
    }
  }

  test("insert event") {
    eventQueue.append(events[0]).unwrap()
    eventQueue.take(1).shouldBe(Ok(listOf(events[0])))
  }

  test("get all events") {
    events.forEachIndexed { _, event ->
      eventQueue.append(event).unwrap()
    }
    eventQueue.take(events.size).shouldBe(Ok(events))
  }

  test("batch should be subset of queue") {
    events.forEachIndexed { _, event ->
      eventQueue.append(event).unwrap()
    }
    val eventsBatchLimit = (1 until events.size).random()
    eventQueue.take(eventsBatchLimit).unwrap().shouldHaveSize(eventsBatchLimit)

    eventQueue.take(eventsBatchLimit).unwrap().forEach { event ->
      eventQueue.take(events.size).unwrap().shouldContain(event)
    }
  }

  test("take doesnt delete") {
    events.forEachIndexed { _, event ->
      eventQueue.append(event).unwrap()
    }

    eventQueue.take(events.size).shouldBe(Ok(events))
    eventQueue.take(events.size).shouldBe(Ok(events))
  }

  test("remove") {
    eventQueue.append(events[0]).unwrap()

    eventQueue.removeFirst(1).unwrap()

    eventQueue.take(1).shouldBe(Ok(emptyList()))
  }

  test("remove partial") {
    eventQueue.append(events[0]).unwrap()
    eventQueue.append(events[1]).unwrap()
    eventQueue.append(events[3]).unwrap()

    eventQueue.removeFirst(2).unwrap()

    eventQueue.take(1).shouldBe(Ok(listOf(events[3])))
  }

  test("out of order event appended to end") {
    eventQueue.append(events[1]).unwrap()

    eventQueue.take(1).shouldBe(Ok(listOf(events[1])))

    eventQueue.append(events[0]).unwrap()

    eventQueue.removeFirst(1).unwrap()

    eventQueue.take(1).shouldBe(Ok(listOf(events[0])))
  }
})
