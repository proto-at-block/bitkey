package build.wallet.onboarding

import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.sqldelight.inMemorySqlDriver
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant

class OnboardingCompletionDaoImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()
  lateinit var dao: OnboardingCompletionDao

  beforeTest {
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    dao = OnboardingCompletionDaoImpl(databaseProvider)
  }

  test("getCompletionTimestamp returns null when no entry exists") {
    dao.getCompletionTimestamp("test-id").value.shouldBe(null)
  }

  test("recordCompletion and getCompletionTimestamp") {
    val timestamp = Instant.fromEpochMilliseconds(1234567890000)

    // Record completion
    dao.recordCompletion("test-id", timestamp).shouldBe(Ok(Unit))

    // Verify timestamp was stored correctly
    dao.getCompletionTimestamp("test-id").value.shouldBe(timestamp)
  }

  test("recordCompletion overwrites existing timestamp") {
    val firstTimestamp = Instant.fromEpochMilliseconds(1234567890000)
    val secondTimestamp = Instant.fromEpochMilliseconds(1234567899999)

    // Record first completion
    dao.recordCompletion("test-id", firstTimestamp).shouldBe(Ok(Unit))
    dao.getCompletionTimestamp("test-id").value.shouldBe(firstTimestamp)

    // Record second completion
    dao.recordCompletion("test-id", secondTimestamp).shouldBe(Ok(Unit))
    dao.getCompletionTimestamp("test-id").value.shouldBe(secondTimestamp)
  }

  test("different IDs store separate timestamps") {
    val timestamp1 = Instant.fromEpochMilliseconds(1234567890000)
    val timestamp2 = Instant.fromEpochMilliseconds(1234567899999)

    // Record completions for different IDs
    dao.recordCompletion("id1", timestamp1).shouldBe(Ok(Unit))
    dao.recordCompletion("id2", timestamp2).shouldBe(Ok(Unit))

    // Verify timestamps were stored separately
    dao.getCompletionTimestamp("id1").value.shouldBe(timestamp1)
    dao.getCompletionTimestamp("id2").value.shouldBe(timestamp2)
  }

  test("recordCompletionIfNotExists saves timestamp when no record exists") {
    val timestamp = Instant.fromEpochMilliseconds(1234567890000)

    // Verify no record exists initially
    dao.getCompletionTimestamp("test-id").value.shouldBe(null)

    // Record completion
    dao.recordCompletionIfNotExists("test-id", timestamp).shouldBe(Ok(Unit))

    // Verify timestamp was stored
    dao.getCompletionTimestamp("test-id").value.shouldBe(timestamp)
  }

  test("recordCompletionIfNotExists preserves existing timestamp") {
    val existingTimestamp = Instant.fromEpochMilliseconds(1234567890000)
    val newTimestamp = Instant.fromEpochMilliseconds(1234567899999)

    // Record initial completion
    dao.recordCompletion("test-id", existingTimestamp).shouldBe(Ok(Unit))
    dao.getCompletionTimestamp("test-id").value.shouldBe(existingTimestamp)

    // Attempt to record new completion
    dao.recordCompletionIfNotExists("test-id", newTimestamp).shouldBe(Ok(Unit))

    // Verify original timestamp is preserved
    dao.getCompletionTimestamp("test-id").value.shouldBe(existingTimestamp)
  }
})
