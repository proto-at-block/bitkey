package build.wallet.queueprocessor

import build.wallet.coroutines.turbine.turbines
import build.wallet.ktor.result.HttpError.NetworkError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ProcessQueueInBatchesTests : FunSpec({
  val senderSpy = ProcessorMock<Int>(turbines::create)

  beforeTest {
    senderSpy.reset()
  }

  test("removes after successful send") {
    senderSpy.processBatchReturnValues = listOf(Ok(Unit))

    val queue = MemoryQueueImpl<Int>()
    queue.append(1).unwrap()

    processQueueInBatches(queue, senderSpy, 1).shouldBe(Ok(Unit))

    queue.take(1).shouldBe(Ok(emptyList()))
    senderSpy.processBatchCalls.awaitItem().shouldBe(listOf(1))
  }

  test("doesn't remove after failed send") {
    val err = Err(NetworkError(Throwable("uh oh!")))
    senderSpy.processBatchReturnValues = listOf(err)

    val queue = MemoryQueueImpl<Int>()
    queue.append(1).unwrap()

    processQueueInBatches(queue, senderSpy, 1).shouldBe(err)

    queue.take(1).shouldBe(Ok(listOf(1)))
    senderSpy.processBatchCalls.awaitItem().shouldBe(listOf(1))
  }

  test("puts failed items on back of queue") {
    val err = Err(NetworkError(Throwable("uh oh!")))
    senderSpy.processBatchReturnValues = listOf(err)

    val queue = MemoryQueueImpl<Int>()
    queue.append(1).unwrap()
    queue.append(2).unwrap()

    processQueueInBatches(queue, senderSpy, 1).shouldBe(err)

    queue.take(2).shouldBe(Ok(listOf(2, 1)))
    senderSpy.processBatchCalls.awaitItem().shouldBe(listOf(1))
  }

  test("puts failed items on back of queue multiple") {
    val err = Err(NetworkError(Throwable("uh oh!")))
    senderSpy.processBatchReturnValues = listOf(err)

    val queue = MemoryQueueImpl<Int>()
    queue.append(1).unwrap()
    queue.append(2).unwrap()
    queue.append(3).unwrap()

    processQueueInBatches(queue, senderSpy, 2).shouldBe(err)

    queue.take(3).shouldBe(Ok(listOf(3, 1, 2)))
    senderSpy.processBatchCalls.awaitItem().shouldBe(listOf(1, 2))
  }

  test("puts failed items on back of queue and stops processing") {
    val err = Err(NetworkError(Throwable("uh oh!")))
    senderSpy.processBatchReturnValues = listOf(Ok(Unit), err)

    val queue = MemoryQueueImpl<Int>()
    queue.append(1).unwrap()
    queue.append(2).unwrap()
    queue.append(3).unwrap()

    processQueueInBatches(queue, senderSpy, 1).shouldBe(err)

    queue.take(2).shouldBe(Ok(listOf(3, 2)))
    senderSpy.processBatchCalls.awaitItem().shouldBe(listOf(1))
    senderSpy.processBatchCalls.awaitItem().shouldBe(listOf(2))
  }

  test("multiple sends if queue larger than batch size") {
    senderSpy.processBatchReturnValues = listOf(Ok(Unit), Ok(Unit))

    val queue = MemoryQueueImpl<Int>()
    queue.append(1).unwrap()
    queue.append(2).unwrap()

    processQueueInBatches(queue, senderSpy, 1).shouldBe(Ok(Unit))

    queue.take(1).shouldBe(Ok(emptyList()))
    senderSpy.processBatchCalls.awaitItem().shouldBe(listOf(1))
    senderSpy.processBatchCalls.awaitItem().shouldBe(listOf(2))
  }

  test("single send if batch size of queue") {
    senderSpy.processBatchReturnValues = listOf(Ok(Unit))

    val queue = MemoryQueueImpl<Int>()
    queue.append(1).unwrap()
    queue.append(2).unwrap()

    processQueueInBatches(queue, senderSpy, 2).shouldBe(Ok(Unit))

    queue.take(1).shouldBe(Ok(emptyList()))
    senderSpy.processBatchCalls.awaitItem().shouldBe(listOf(1, 2))
  }

  test("partial flush makes progress") {
    val err = Err(NetworkError(Throwable("uh oh!")))
    senderSpy.processBatchReturnValues = listOf(Ok(Unit), err)

    val queue = MemoryQueueImpl<Int>()
    queue.append(1).unwrap()
    queue.append(2).unwrap()

    processQueueInBatches(queue, senderSpy, 1).shouldBe(err)

    queue.take(1).shouldBe(Ok(listOf(2)))
    senderSpy.processBatchCalls.awaitItem().shouldBe(listOf(1))
    senderSpy.processBatchCalls.awaitItem().shouldBe(listOf(2))
  }

  test("no pending payloads results in no sends") {
    senderSpy.processBatchReturnValues = listOf(Ok(Unit))

    val queue = MemoryQueueImpl<Int>()

    processQueueInBatches(queue, senderSpy, 1).shouldBe(Ok(Unit))
  }
})
