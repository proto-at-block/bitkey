package build.wallet.queueprocessor

import build.wallet.coroutines.turbine.turbines
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

class BatcherProcessorImplTests : FunSpec({
  val q = QueueMock<Int>(turbines::create)
  val processor = BatcherProcessorImpl(q, ProcessorMock(turbines::create), 0.seconds, 0)

  beforeTest {
    q.reset()
  }

  test("empty batch puts nothing on queue") {
    processor.processBatch(emptyList()).shouldBe(Ok(Unit))
  }

  test("batch of 1 puts on queue") {
    q.appendReturnValues = listOf(Ok(Unit))
    processor.process(1).shouldBe(Ok(Unit))
    q.appendCalls.awaitItem().shouldBe(1)
  }

  test("batch of multiple puts all on queue") {
    q.appendReturnValues = listOf(Ok(Unit), Ok(Unit))
    processor.processBatch(listOf(1, 2)).shouldBe(Ok(Unit))
    q.appendCalls.awaitItem().shouldBe(1)
    q.appendCalls.awaitItem().shouldBe(2)
  }

  test("failure on first append returns failure") {
    val error = Err(Error(Throwable("Uh oh!")))
    q.appendReturnValues = listOf(error)
    processor.processBatch(listOf(1)).shouldBe(error)
    q.appendCalls.awaitItem().shouldBe(1)
  }

  test("failure on subsequent append returns failure") {
    val error = Err(Error(Throwable("Uh oh!")))
    q.appendReturnValues = listOf(Ok(Unit), error)
    processor.processBatch(listOf(1, 2)).shouldBe(error)
    q.appendCalls.awaitItem().shouldBe(1)
    q.appendCalls.awaitItem().shouldBe(2)
  }
})
