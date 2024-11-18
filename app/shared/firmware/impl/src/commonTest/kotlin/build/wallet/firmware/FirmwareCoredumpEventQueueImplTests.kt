package build.wallet.firmware

import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.sqldelight.inMemorySqlDriver
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8

class FirmwareCoredumpEventQueueImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()
  lateinit var queue: FirmwareCoredumpEventQueue
  val items =
    mutableListOf<FirmwareCoredump>().apply {
      addAll(
        (0..9).map { index ->
          FirmwareCoredump(
            index.toString().encodeUtf8(),
            TelemetryIdentifiers(
              "serial_$index",
              "version_$index",
              "swType_$index",
              "hwRevision_$index"
            )
          )
        }
      )
    }

  beforeTest {
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    queue = FirmwareCoredumpEventQueueImpl(databaseProvider)
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

  test("insert item") {
    val item = items[0]
    queue.append(item).unwrap()
    queue.take(1).shouldBe(Ok(listOf(item)))
  }

  test("get all items") {
    items.forEachIndexed { _, item ->
      queue.append(item).unwrap()
    }
    queue.take(items.size).shouldBe(Ok(items))
  }

  test("batch should be subset of queue") {
    items.forEachIndexed { _, item ->
      queue.append(item).unwrap()
    }
    val itemsBatchLimit = (1 until items.size).random()
    queue.take(itemsBatchLimit).unwrap().shouldHaveSize(itemsBatchLimit)

    queue.take(itemsBatchLimit).unwrap().forEach { item ->
      queue.take(items.size).unwrap().shouldContain(item)
    }
  }

  test("take doesnt delete") {
    items.forEachIndexed { _, item ->
      queue.append(item).unwrap()
    }

    queue.take(items.size).shouldBe(Ok(items))
    queue.take(items.size).shouldBe(Ok(items))
  }

  test("remove") {
    queue.append(items[0]).unwrap()

    queue.removeFirst(1).unwrap()

    queue.take(1).shouldBe(Ok(emptyList()))
  }

  test("remove partial") {
    queue.append(items[0]).unwrap()
    queue.append(items[1]).unwrap()
    queue.append(items[3]).unwrap()

    queue.removeFirst(2).unwrap()

    queue.take(1).shouldBe(Ok(listOf(items[3])))
  }
})
