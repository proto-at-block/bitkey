package build.wallet.database

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingPublicKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.crypto.PublicKey
import build.wallet.database.sqldelight.BitkeyDatabase
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.sqldelight.inMemorySqlDriver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Tests to verify foreign key constraints and cascading deletes work correctly
 * in the hierarchical database structure: Account 1:N Keybox 1:N KeyBundle/Keyset
 */
class ForeignKeyConstraintsTests : FunSpec({

  val sqlDriver = inMemorySqlDriver()
  lateinit var database: BitkeyDatabase

  beforeTest {
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    database = databaseProvider.database()
  }

  context("Account-Keybox relationship") {
    test("deleting account cascades to delete all keyboxes") {
      // Insert test account
      database.fullAccountQueries.insertFullAccount(FullAccountId("test-account-123"))

      // Insert keybox for this account
      database.keyboxQueries.insertKeybox(
        id = "keybox-1",
        accountId = FullAccountId("test-account-123"),
        networkType = BitcoinNetworkType.BITCOIN,
        fakeHardware = false,
        f8eEnvironment = F8eEnvironment.Development,
        isTestAccount = true,
        isUsingSocRecFakes = false,
        delayNotifyDuration = null,
        appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature("signature-123"),
        canUseKeyboxKeysets = true
      )

      // Verify keybox exists
      database.keyboxQueries.countKeyboxes().executeAsOne() shouldBe 1

      // Delete account - should cascade delete keybox
      database.fullAccountQueries.deleteAccount(FullAccountId("test-account-123"))

      // Verify keybox was deleted due to cascade
      database.keyboxQueries.countKeyboxes().executeAsOne() shouldBe 0
      database.fullAccountQueries.countAccounts().executeAsOne() shouldBe 0
    }

    test("inserting keybox with non-existent account fails") {
      // Attempt to insert keybox without corresponding account
      val result = runCatching {
        database.keyboxQueries.insertKeybox(
          id = "orphan-keybox",
          accountId = FullAccountId("non-existent-account"),
          networkType = BitcoinNetworkType.BITCOIN,
          fakeHardware = false,
          f8eEnvironment = F8eEnvironment.Development,
          isTestAccount = true,
          isUsingSocRecFakes = false,
          delayNotifyDuration = null,
          appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature("signature-123"),
          canUseKeyboxKeysets = true
        )
      }

      // Should fail due to foreign key constraint
      result.isFailure shouldBe true
      result.exceptionOrNull()?.message?.contains("FOREIGN KEY constraint failed") shouldBe true
    }
  }

  context("Keybox-KeyBundle relationships") {
    test("deleting keybox cascades to delete all key bundles") {
      // Insert test data
      database.fullAccountQueries.insertFullAccount(FullAccountId("test-account"))

      database.keyboxQueries.insertKeybox(
        id = "keybox-1",
        accountId = FullAccountId("test-account"),
        networkType = BitcoinNetworkType.BITCOIN,
        fakeHardware = false,
        f8eEnvironment = F8eEnvironment.Development,
        isTestAccount = true,
        isUsingSocRecFakes = false,
        delayNotifyDuration = null,
        appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature("signature-123"),
        canUseKeyboxKeysets = true
      )

      // Insert key bundles using proper query methods
      database.appKeyBundleQueries.insertKeyBundle(
        id = "app-bundle-1",
        keyboxId = "keybox-1",
        globalAuthKey = PublicKey("global-auth-key"),
        spendingKey = AppSpendingPublicKey(DescriptorPublicKeyMock("spending-key")),
        recoveryAuthKey = PublicKey("recovery-auth-key"),
        isActive = true
      )

      database.hwKeyBundleQueries.insertKeyBundle(
        id = "hw-bundle-1",
        keyboxId = "keybox-1",
        spendingKey = HwSpendingPublicKey(DescriptorPublicKeyMock("hw-spending-key")),
        authKey = HwAuthPublicKey(Secp256k1PublicKey("hw-auth-key")),
        isActive = true
      )

      // Verify bundles exist
      database.appKeyBundleQueries.countAppKeyBundles().executeAsOne() shouldBe 1
      database.hwKeyBundleQueries.countHwKeyBundles().executeAsOne() shouldBe 1

      // Delete keybox - should cascade delete all key bundles
      database.keyboxQueries.deleteKeybox("keybox-1")

      // Verify key bundles were deleted due to cascade
      database.appKeyBundleQueries.countAppKeyBundles().executeAsOne() shouldBe 0
      database.hwKeyBundleQueries.countHwKeyBundles().executeAsOne() shouldBe 0
    }

    test("inserting key bundles with non-existent keybox fails") {
      // Attempt to insert app key bundle without corresponding keybox
      val appBundleResult = runCatching {
        database.appKeyBundleQueries.insertKeyBundle(
          id = "orphan-app-bundle",
          keyboxId = "non-existent-keybox",
          globalAuthKey = PublicKey("global-auth-key"),
          spendingKey = AppSpendingPublicKey(DescriptorPublicKeyMock("spending-key")),
          recoveryAuthKey = PublicKey("recovery-auth-key"),
          isActive = true
        )
      }

      // Attempt to insert hw key bundle without corresponding keybox
      val hwBundleResult = runCatching {
        database.hwKeyBundleQueries.insertKeyBundle(
          id = "orphan-hw-bundle",
          keyboxId = "non-existent-keybox",
          spendingKey = HwSpendingPublicKey(DescriptorPublicKeyMock("hw-spending-key")),
          authKey = HwAuthPublicKey(Secp256k1PublicKey("hw-auth-key")),
          isActive = true
        )
      }

      // Both should fail due to foreign key constraints
      appBundleResult.isFailure shouldBe true
      appBundleResult.exceptionOrNull()?.message?.contains("FOREIGN KEY constraint failed") shouldBe true

      hwBundleResult.isFailure shouldBe true
      hwBundleResult.exceptionOrNull()?.message?.contains("FOREIGN KEY constraint failed") shouldBe true
    }
  }

  context("Keybox-Keyset relationship") {
    test("deleting keybox cascades to delete spending keysets") {
      // Insert test data
      database.fullAccountQueries.insertFullAccount(FullAccountId("test-account"))

      database.keyboxQueries.insertKeybox(
        id = "keybox-1",
        accountId = FullAccountId("test-account"),
        networkType = BitcoinNetworkType.BITCOIN,
        fakeHardware = false,
        f8eEnvironment = F8eEnvironment.Development,
        isTestAccount = true,
        isUsingSocRecFakes = false,
        delayNotifyDuration = null,
        appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature("signature-123"),
        canUseKeyboxKeysets = true
      )

      // Insert spending keyset using proper query method
      database.spendingKeysetQueries.insertKeyset(
        id = "keyset-1",
        keyboxId = "keybox-1",
        serverId = "server-id",
        appKey = AppSpendingPublicKey(DescriptorPublicKeyMock("app-key-unique")),
        hardwareKey = HwSpendingPublicKey(DescriptorPublicKeyMock("hw-key-unique")),
        serverKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-key-unique")),
        isActive = true
      )

      // Verify keyset exists
      database.spendingKeysetQueries.countSpendingKeysets().executeAsOne() shouldBe 1

      // Delete keybox - should cascade delete keyset
      database.keyboxQueries.deleteKeybox("keybox-1")

      // Verify keyset was deleted due to cascade
      database.spendingKeysetQueries.countSpendingKeysets().executeAsOne() shouldBe 0
    }

    test("inserting keyset with non-existent keybox fails") {
      // Attempt to insert keyset without corresponding keybox
      val result = runCatching {
        database.spendingKeysetQueries.insertKeyset(
          id = "orphan-keyset",
          keyboxId = "non-existent-keybox",
          serverId = "server-id",
          appKey = AppSpendingPublicKey(DescriptorPublicKeyMock("app-key-unique")),
          hardwareKey = HwSpendingPublicKey(DescriptorPublicKeyMock("hw-key-unique")),
          serverKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-key-unique")),
          isActive = true
        )
      }

      // Should fail due to foreign key constraint
      result.isFailure shouldBe true
      result.exceptionOrNull()?.message?.contains("FOREIGN KEY constraint failed") shouldBe true
    }
  }

  context("Complete hierarchy cascade delete") {
    test("deleting account deletes entire hierarchy") {
      // Insert complete hierarchy
      database.fullAccountQueries.insertFullAccount(FullAccountId("test-account"))

      database.keyboxQueries.insertKeybox(
        id = "keybox-1",
        accountId = FullAccountId("test-account"),
        networkType = BitcoinNetworkType.BITCOIN,
        fakeHardware = false,
        f8eEnvironment = F8eEnvironment.Development,
        isTestAccount = true,
        isUsingSocRecFakes = false,
        delayNotifyDuration = null,
        appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature("signature-123"),
        canUseKeyboxKeysets = true
      )

      // Insert all child entities using proper query methods
      database.spendingKeysetQueries.insertKeyset(
        id = "keyset-1",
        keyboxId = "keybox-1",
        serverId = "server-id",
        appKey = AppSpendingPublicKey(DescriptorPublicKeyMock("app-key-unique")),
        hardwareKey = HwSpendingPublicKey(DescriptorPublicKeyMock("hw-key-unique")),
        serverKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-key-unique")),
        isActive = true
      )

      database.appKeyBundleQueries.insertKeyBundle(
        id = "app-bundle-1",
        keyboxId = "keybox-1",
        globalAuthKey = PublicKey("global-auth-key"),
        spendingKey = AppSpendingPublicKey(DescriptorPublicKeyMock("spending-key")),
        recoveryAuthKey = PublicKey("recovery-auth-key"),
        isActive = true
      )

      database.hwKeyBundleQueries.insertKeyBundle(
        id = "hw-bundle-1",
        keyboxId = "keybox-1",
        spendingKey = HwSpendingPublicKey(DescriptorPublicKeyMock("hw-spending-key")),
        authKey = HwAuthPublicKey(Secp256k1PublicKey("hw-auth-key")),
        isActive = true
      )

      // Verify all entities exist
      database.fullAccountQueries.countAccounts().executeAsOne() shouldBe 1
      database.keyboxQueries.countKeyboxes().executeAsOne() shouldBe 1
      database.spendingKeysetQueries.countSpendingKeysets().executeAsOne() shouldBe 1
      database.appKeyBundleQueries.countAppKeyBundles().executeAsOne() shouldBe 1
      database.hwKeyBundleQueries.countHwKeyBundles().executeAsOne() shouldBe 1

      // Delete account - should cascade delete entire hierarchy
      database.fullAccountQueries.deleteAccount(FullAccountId("test-account"))

      // Verify entire hierarchy was deleted
      database.fullAccountQueries.countAccounts().executeAsOne() shouldBe 0
      database.keyboxQueries.countKeyboxes().executeAsOne() shouldBe 0
      database.spendingKeysetQueries.countSpendingKeysets().executeAsOne() shouldBe 0
      database.appKeyBundleQueries.countAppKeyBundles().executeAsOne() shouldBe 0
      database.hwKeyBundleQueries.countHwKeyBundles().executeAsOne() shouldBe 0
    }
  }

  context("isActive constraints") {
    test("only one active keyset per keybox is enforced") {
      // Insert test data
      database.fullAccountQueries.insertFullAccount(FullAccountId("test-account"))

      database.keyboxQueries.insertKeybox(
        id = "keybox-1",
        accountId = FullAccountId("test-account"),
        networkType = BitcoinNetworkType.BITCOIN,
        fakeHardware = false,
        f8eEnvironment = F8eEnvironment.Development,
        isTestAccount = true,
        isUsingSocRecFakes = false,
        delayNotifyDuration = null,
        appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature("signature-123"),
        canUseKeyboxKeysets = true
      )

      // Insert first active keyset
      database.spendingKeysetQueries.insertKeyset(
        id = "keyset-1",
        keyboxId = "keybox-1",
        serverId = "server-id-1",
        appKey = AppSpendingPublicKey(DescriptorPublicKeyMock("app-key-1")),
        hardwareKey = HwSpendingPublicKey(DescriptorPublicKeyMock("hw-key-1")),
        serverKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-key-1")),
        isActive = true
      )

      // Attempt to insert second active keyset for same keybox - should fail due to unique constraint
      val result = runCatching {
        database.spendingKeysetQueries.insertKeyset(
          id = "keyset-2",
          keyboxId = "keybox-1",
          serverId = "server-id-2",
          appKey = AppSpendingPublicKey(DescriptorPublicKeyMock("app-key-2")),
          hardwareKey = HwSpendingPublicKey(DescriptorPublicKeyMock("hw-key-2")),
          serverKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-key-2")),
          isActive = true
        )
      }

      // Should fail due to unique constraint on (keyboxId) WHERE isActive = 1
      result.isFailure shouldBe true
      result.exceptionOrNull()?.message?.contains("UNIQUE constraint failed") shouldBe true
    }
  }

  context("Selective cascade deletion") {
    test("deleting one account only cascades to delete that account's data") {
      // Create first account with complete hierarchy
      database.fullAccountQueries.insertFullAccount(FullAccountId("account-1"))

      database.keyboxQueries.insertKeybox(
        id = "keybox-1",
        accountId = FullAccountId("account-1"),
        networkType = BitcoinNetworkType.BITCOIN,
        fakeHardware = false,
        f8eEnvironment = F8eEnvironment.Development,
        isTestAccount = true,
        isUsingSocRecFakes = false,
        delayNotifyDuration = null,
        appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature("signature-1"),
        canUseKeyboxKeysets = true
      )

      database.spendingKeysetQueries.insertKeyset(
        id = "keyset-1",
        keyboxId = "keybox-1",
        serverId = "server-id-1",
        appKey = AppSpendingPublicKey(DescriptorPublicKeyMock("app-key-1")),
        hardwareKey = HwSpendingPublicKey(DescriptorPublicKeyMock("hw-key-1")),
        serverKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-key-1")),
        isActive = true
      )

      database.appKeyBundleQueries.insertKeyBundle(
        id = "app-bundle-1",
        keyboxId = "keybox-1",
        globalAuthKey = PublicKey("global-auth-key-1"),
        spendingKey = AppSpendingPublicKey(DescriptorPublicKeyMock("spending-key-1")),
        recoveryAuthKey = PublicKey("recovery-auth-key-1"),
        isActive = true
      )

      database.hwKeyBundleQueries.insertKeyBundle(
        id = "hw-bundle-1",
        keyboxId = "keybox-1",
        spendingKey = HwSpendingPublicKey(DescriptorPublicKeyMock("hw-spending-key-1")),
        authKey = HwAuthPublicKey(Secp256k1PublicKey("hw-auth-key-1")),
        isActive = true
      )

      // Create second account with complete hierarchy
      database.fullAccountQueries.insertFullAccount(FullAccountId("account-2"))

      database.keyboxQueries.insertKeybox(
        id = "keybox-2",
        accountId = FullAccountId("account-2"),
        networkType = BitcoinNetworkType.SIGNET,
        fakeHardware = true,
        f8eEnvironment = F8eEnvironment.Staging,
        isTestAccount = false,
        isUsingSocRecFakes = true,
        delayNotifyDuration = null,
        appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature("signature-2"),
        canUseKeyboxKeysets = true
      )

      database.spendingKeysetQueries.insertKeyset(
        id = "keyset-2",
        keyboxId = "keybox-2",
        serverId = "server-id-2",
        appKey = AppSpendingPublicKey(DescriptorPublicKeyMock("app-key-2")),
        hardwareKey = HwSpendingPublicKey(DescriptorPublicKeyMock("hw-key-2")),
        serverKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("server-key-2")),
        isActive = true
      )

      database.appKeyBundleQueries.insertKeyBundle(
        id = "app-bundle-2",
        keyboxId = "keybox-2",
        globalAuthKey = PublicKey("global-auth-key-2"),
        spendingKey = AppSpendingPublicKey(DescriptorPublicKeyMock("spending-key-2")),
        recoveryAuthKey = PublicKey("recovery-auth-key-2"),
        isActive = true
      )

      database.hwKeyBundleQueries.insertKeyBundle(
        id = "hw-bundle-2",
        keyboxId = "keybox-2",
        spendingKey = HwSpendingPublicKey(DescriptorPublicKeyMock("hw-spending-key-2")),
        authKey = HwAuthPublicKey(Secp256k1PublicKey("hw-auth-key-2")),
        isActive = true
      )

      // Verify both complete hierarchies exist
      database.fullAccountQueries.countAccounts().executeAsOne() shouldBe 2
      database.keyboxQueries.countKeyboxes().executeAsOne() shouldBe 2
      database.spendingKeysetQueries.countSpendingKeysets().executeAsOne() shouldBe 2
      database.appKeyBundleQueries.countAppKeyBundles().executeAsOne() shouldBe 2
      database.hwKeyBundleQueries.countHwKeyBundles().executeAsOne() shouldBe 2

      // Delete only account-1 - should cascade delete only account-1's hierarchy
      database.fullAccountQueries.deleteAccount(FullAccountId("account-1"))

      // Verify account-1's hierarchy was deleted
      database.fullAccountQueries.countAccounts().executeAsOne() shouldBe 1
      database.keyboxQueries.countKeyboxes().executeAsOne() shouldBe 1
      database.spendingKeysetQueries.countSpendingKeysets().executeAsOne() shouldBe 1
      database.appKeyBundleQueries.countAppKeyBundles().executeAsOne() shouldBe 1
      database.hwKeyBundleQueries.countHwKeyBundles().executeAsOne() shouldBe 1

      // Verify account-2's data still exists by checking specific IDs
      database.keyboxQueries.keyboxById("keybox-2").executeAsOneOrNull() shouldNotBe null
      database.spendingKeysetQueries.keysetById("keyset-2").executeAsOneOrNull() shouldNotBe null
      database.appKeyBundleQueries.keyBundleById("app-bundle-2").executeAsOneOrNull() shouldNotBe null

      // Additional verification: ensure deleted entities are actually gone
      database.keyboxQueries.keyboxById("keybox-1").executeAsOneOrNull() shouldBe null
      database.spendingKeysetQueries.keysetById("keyset-1").executeAsOneOrNull() shouldBe null
      database.appKeyBundleQueries.keyBundleById("app-bundle-1").executeAsOneOrNull() shouldBe null
    }
  }
})
