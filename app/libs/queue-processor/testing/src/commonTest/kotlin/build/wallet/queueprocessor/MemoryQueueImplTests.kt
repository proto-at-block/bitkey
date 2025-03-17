package build.wallet.queueprocessor

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MemoryQueueImplTests : FunSpec({
  test("passing negative num to take throws") {
    val q = MemoryQueueImpl<Int>()

    shouldThrow<IllegalArgumentException> {
      q.take(-1).unwrap()
    }
  }

  test("negative value to removeFirst throws") {
    val q = MemoryQueueImpl<Int>()
    q.append(1).unwrap()

    shouldThrow<IllegalArgumentException> {
      q.removeFirst(-1).unwrap()
    }
  }

  test("pop empty is no op") {
    val q = MemoryQueueImpl<Int>()

    q.removeFirst(1).unwrap()
  }

  test("take sees appended value") {
    val q = MemoryQueueImpl<Int>()
    q.append(1).unwrap()

    q.take(1).shouldBe(Ok(listOf(1)))
  }

  test("take sees 2 appended value") {
    val q = MemoryQueueImpl<Int>()
    q.append(1).unwrap()
    q.append(2).unwrap()

    q.take(2).shouldBe(Ok(listOf(1, 2)))
  }

  test("take more than size returns entire") {
    val q = MemoryQueueImpl<Int>()
    q.append(1).unwrap()

    q.take(2).shouldBe(Ok(listOf(1)))
  }

  test("pop more than size clears") {
    val q = MemoryQueueImpl<Int>()
    q.append(1).unwrap()
    q.append(2).unwrap()
    q.append(3).unwrap()

    q.removeFirst(4).unwrap()

    q.take(1).shouldBe(Ok(emptyList()))
  }

  test("moveToEnd single") {
    val q = MemoryQueueImpl<Int>()
    q.append(1).unwrap()
    q.append(2).unwrap()
    q.append(3).unwrap()

    q.moveToEnd(1).unwrap()

    q.take(3).shouldBe(Ok(listOf(2, 3, 1)))
  }

  test("moveToEnd multiple") {
    val q = MemoryQueueImpl<Int>()
    q.append(1).unwrap()
    q.append(2).unwrap()
    q.append(3).unwrap()

    q.moveToEnd(2).unwrap()

    q.take(3).shouldBe(Ok(listOf(3, 1, 2)))
  }

  test("moveToEnd larger than size") {
    val q = MemoryQueueImpl<Int>()
    q.append(1).unwrap()
    q.append(2).unwrap()
    q.append(3).unwrap()

    q.moveToEnd(4).unwrap()

    q.take(3).shouldBe(Ok(listOf(1, 2, 3)))
  }
})
