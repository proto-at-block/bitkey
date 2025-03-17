package build.wallet.sqldelight.adapter

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant

private data class TestCase(val instantIso8601: String, val instant: Instant)

class InstantAsIso8601ColumnAdapterTests : FunSpec({

  val adapter = InstantAsIso8601ColumnAdapter

  // Define test cases as a list of TestCase objects
  val testCases = listOf(
    TestCase("2024-01-01T12:00:00Z", Instant.parse("2024-01-01T12:00:00Z")),
    TestCase("2024-01-01T12:00:00.123Z", Instant.parse("2024-01-01T12:00:00.123Z")),
    TestCase("2024-01-01T12:00:00.123456Z", Instant.parse("2024-01-01T12:00:00.123456Z")),
    TestCase("2024-01-01T12:00:00.123456789Z", Instant.parse("2024-01-01T12:00:00.123456789Z"))
  )

  test("encode Instant as ISO-8601 String") {
    testCases.forEach { testCase ->
      adapter.encode(testCase.instant).shouldBe(testCase.instantIso8601)
    }
  }

  test("decode ISO-8601 String as Instant") {
    testCases.forEach { testCase ->
      adapter.decode(testCase.instantIso8601).shouldBe(testCase.instant)
    }
  }
})
