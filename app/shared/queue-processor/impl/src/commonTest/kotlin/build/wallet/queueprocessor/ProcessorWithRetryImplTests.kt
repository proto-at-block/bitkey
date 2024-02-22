package build.wallet.queueprocessor

import build.wallet.coroutines.turbine.turbines
import build.wallet.ktor.result.HttpError.NetworkError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

class ProcessorWithRetryImplTests : FunSpec({

  lateinit var q: Queue<Int>
  val processor = ProcessorMock<Int>(turbines::create)
  lateinit var retryer: Processor<Int>

  beforeTest {
    q = MemoryQueueImpl()
    retryer = ProcessorImpl(q, processor, 0.seconds, 0)
    processor.reset()
  }

  test("no append on success") {
    processor.processBatchReturnValues = listOf(Ok(Unit))

    retryer.processBatch(listOf(1))

    processor.processBatchCalls.awaitItem().shouldBe(listOf(1))
    q.take(1).shouldBe(Ok(emptyList()))
  }

  test("append single on failure") {
    val error = Err(NetworkError(Throwable("Uh oh!")))
    processor.processBatchReturnValues = listOf(error)

    retryer.processBatch(listOf(1))

    processor.processBatchCalls.awaitItem().shouldBe(listOf(1))
    q.take(1).shouldBe(Ok(listOf(1)))
  }

  test("append batch on failure") {
    val error = Err(NetworkError(Throwable("Uh oh!")))
    processor.processBatchReturnValues = listOf(error)

    retryer.processBatch(listOf(1, 2, 3))

    processor.processBatchCalls.awaitItem().shouldBe(listOf(1, 2, 3))
    q.take(3).shouldBe(Ok(listOf(1, 2, 3)))
  }
})
