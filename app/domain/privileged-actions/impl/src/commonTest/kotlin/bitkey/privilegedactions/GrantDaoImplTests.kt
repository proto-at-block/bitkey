package bitkey.privilegedactions

import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.grants.Grant
import build.wallet.grants.GrantAction
import build.wallet.grants.GrantTestHelpers
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GrantDaoImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()

  lateinit var dao: GrantDao

  val clock = ClockFake()

  // Test data
  val grant1 = Grant(
    version = 1,
    serializedRequest = GrantTestHelpers.createMockSerializedGrantRequest(GrantAction.FINGERPRINT_RESET),
    signature = ByteArray(64) { it.toByte() }
  )

  val grant2 = Grant(
    version = 1,
    serializedRequest = GrantTestHelpers.createMockSerializedGrantRequest(GrantAction.TRANSACTION_VERIFICATION),
    signature = ByteArray(64) { (it + 64).toByte() }
  )

  beforeTest {
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    dao = GrantDaoImpl(databaseProvider, clock)
  }

  test("save and retrieve grant by action type") {
    // Initially no grant should exist
    dao.getGrantByAction(GrantAction.FINGERPRINT_RESET).shouldBe(Ok(null))

    // Save grant
    dao.saveGrant(grant1).shouldBe(Ok(Unit))

    // Retrieve grant by action type
    dao.getGrantByAction(GrantAction.FINGERPRINT_RESET).shouldBe(Ok(grant1))
  }

  test("save grant overwrites existing grant of same action type") {
    // Save first grant
    dao.saveGrant(grant1).shouldBe(Ok(Unit))
    dao.getGrantByAction(GrantAction.FINGERPRINT_RESET).shouldBe(Ok(grant1))

    // Create a different grant of the same action type
    val newFingerprintGrant = Grant(
      version = 2,
      serializedRequest = GrantTestHelpers.createMockSerializedGrantRequest(GrantAction.FINGERPRINT_RESET),
      signature = ByteArray(64) { (it + 100).toByte() }
    )

    // Save different grant with same action type - should overwrite
    dao.saveGrant(newFingerprintGrant).shouldBe(Ok(Unit))

    // Should have the newer grant
    dao.getGrantByAction(GrantAction.FINGERPRINT_RESET).shouldBe(Ok(newFingerprintGrant))
  }

  test("delete grant by action type") {
    // Save grant first
    dao.saveGrant(grant1).shouldBe(Ok(Unit))
    dao.getGrantByAction(GrantAction.FINGERPRINT_RESET).shouldBe(Ok(grant1))

    // Delete grant by action type
    dao.deleteGrantByAction(GrantAction.FINGERPRINT_RESET).shouldBe(Ok(Unit))

    // Verify it's deleted
    dao.getGrantByAction(GrantAction.FINGERPRINT_RESET).shouldBe(Ok(null))
  }

  test("multiple grants with different action types") {
    // Save two grants with different action types
    dao.saveGrant(grant1).shouldBe(Ok(Unit))
    dao.saveGrant(grant2).shouldBe(Ok(Unit))

    // Both should be retrievable independently
    dao.getGrantByAction(GrantAction.FINGERPRINT_RESET).shouldBe(Ok(grant1))
    dao.getGrantByAction(GrantAction.TRANSACTION_VERIFICATION).shouldBe(Ok(grant2))

    // Delete one should not affect the other
    dao.deleteGrantByAction(GrantAction.FINGERPRINT_RESET).shouldBe(Ok(Unit))
    dao.getGrantByAction(GrantAction.FINGERPRINT_RESET).shouldBe(Ok(null))
    dao.getGrantByAction(GrantAction.TRANSACTION_VERIFICATION).shouldBe(Ok(grant2))
  }

  test("only one grant per action type is allowed") {
    // Save first grant
    dao.saveGrant(grant1).shouldBe(Ok(Unit))
    dao.getGrantByAction(GrantAction.FINGERPRINT_RESET).shouldBe(Ok(grant1))

    // Create another grant of the same action type but different content
    val anotherFingerprintGrant = Grant(
      version = 2,
      serializedRequest = GrantTestHelpers.createMockSerializedGrantRequest(GrantAction.FINGERPRINT_RESET),
      signature = ByteArray(64) { (it + 200).toByte() }
    )

    // Save it - should replace the existing one
    dao.saveGrant(anotherFingerprintGrant).shouldBe(Ok(Unit))

    // Should only have the latest grant
    dao.getGrantByAction(GrantAction.FINGERPRINT_RESET).shouldBe(Ok(anotherFingerprintGrant))
  }

  test("nonexistent grant operations") {
    // Get nonexistent grant
    dao.getGrantByAction(GrantAction.FINGERPRINT_RESET).shouldBe(Ok(null))

    // Delete nonexistent grant (should succeed without error)
    dao.deleteGrantByAction(GrantAction.FINGERPRINT_RESET).shouldBe(Ok(Unit))
  }
})
