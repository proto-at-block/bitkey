package build.wallet.cloud.store

import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.IosCloudKitBackupFeatureFlag
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import okio.ByteString.Companion.encodeUtf8

class CloudKeyValueStoreImplTests : FunSpec({
  val ubiquitousStoreFake = UbiquitousKeyValueStoreFake()
  val cloudKitStoreFake = CloudKitKeyValueStoreFake()
  val featureFlagDao = FeatureFlagDaoFake()
  val featureFlag = IosCloudKitBackupFeatureFlag(featureFlagDao)

  val account = iCloudAccount(ubiquityIdentityToken = "test-token")

  val store = CloudKeyValueStoreImpl(
    ubiquitousKeyValueStore = ubiquitousStoreFake,
    cloudKitKeyValueStore = cloudKitStoreFake,
    iosCloudKitBackupFeatureFlag = featureFlag
  )

  beforeTest {
    ubiquitousStoreFake.reset()
    cloudKitStoreFake.reset()
    featureFlagDao.reset()
  }

  context("flag OFF - delegates to KVS only") {
    beforeTest {
      featureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
    }

    test("setString writes to KVS only") {
      store.setString(account, key = "test-key", value = "test-value").shouldBeOk()

      ubiquitousStoreFake.getString(account, key = "test-key").shouldBeOk("test-value")
      cloudKitStoreFake.get(account, key = "test-key").shouldBeOk(null)
    }

    test("getString reads from KVS only") {
      ubiquitousStoreFake.setString(account, key = "test-key", value = "kvs-value")
      cloudKitStoreFake.set(account, key = "test-key", value = "cloudkit-value".encodeUtf8())

      store.getString(account, key = "test-key").shouldBeOk("kvs-value")
    }

    test("removeString removes from KVS only") {
      ubiquitousStoreFake.setString(account, key = "test-key", value = "test-value")
      cloudKitStoreFake.set(account, key = "test-key", value = "test-value".encodeUtf8())

      store.removeString(account, key = "test-key").shouldBeOk()

      ubiquitousStoreFake.getString(account, key = "test-key").shouldBeOk(null)
      cloudKitStoreFake.get(account, key = "test-key").shouldBeOk("test-value".encodeUtf8())
    }

    test("keys reads from KVS only") {
      ubiquitousStoreFake.setString(account, key = "kvs-key", value = "value")
      cloudKitStoreFake.set(account, key = "cloudkit-key", value = "value".encodeUtf8())

      store.keys(account).shouldBeOk(listOf("kvs-key"))
    }
  }

  context("flag ON - CloudKit success") {
    beforeTest {
      featureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    }

    test("setString writes to CloudKit and mirrors to KVS") {
      store.setString(account, key = "test-key", value = "test-value").shouldBeOk()

      cloudKitStoreFake.get(account, key = "test-key").shouldBeOk("test-value".encodeUtf8())
      ubiquitousStoreFake.getString(account, key = "test-key").shouldBeOk("test-value")
    }

    test("getString reads from CloudKit when available") {
      cloudKitStoreFake.set(account, key = "test-key", value = "cloudkit-value".encodeUtf8())
      ubiquitousStoreFake.setString(account, key = "test-key", value = "kvs-value")

      store.getString(account, key = "test-key").shouldBeOk("cloudkit-value")
    }

    test("removeString removes from CloudKit and mirrors to KVS") {
      cloudKitStoreFake.set(account, key = "test-key", value = "test-value".encodeUtf8())
      ubiquitousStoreFake.setString(account, key = "test-key", value = "test-value")

      store.removeString(account, key = "test-key").shouldBeOk()

      cloudKitStoreFake.get(account, key = "test-key").shouldBeOk(null)
      ubiquitousStoreFake.getString(account, key = "test-key").shouldBeOk(null)
    }

    test("keys reads from CloudKit when available") {
      cloudKitStoreFake.set(account, key = "cloudkit-key", value = "value".encodeUtf8())
      ubiquitousStoreFake.setString(account, key = "kvs-key", value = "value")

      store.keys(account).shouldBeOk(listOf("cloudkit-key"))
    }
  }

  context("flag ON - CloudKit error falls back to KVS") {
    beforeTest {
      featureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    }

    test("getString falls back to KVS when CloudKit fails") {
      cloudKitStoreFake.returnError = true
      ubiquitousStoreFake.setString(account, key = "test-key", value = "kvs-fallback-value")

      store.getString(account, key = "test-key").shouldBeOk("kvs-fallback-value")
    }

    test("keys falls back to KVS when CloudKit fails") {
      ubiquitousStoreFake.setString(account, key = "kvs-key", value = "value")
      cloudKitStoreFake.returnError = true

      store.keys(account).shouldBeOk(listOf("kvs-key"))
    }

    test("getString returns error when both CloudKit and KVS fail") {
      cloudKitStoreFake.returnError = true
      ubiquitousStoreFake.returnError = true

      store.getString(account, key = "test-key").shouldBeErrOfType<CloudError>()
    }

    test("keys returns error when both CloudKit and KVS fail") {
      cloudKitStoreFake.returnError = true
      ubiquitousStoreFake.returnError = true

      store.keys(account).shouldBeErrOfType<CloudError>()
    }
  }

  context("flag ON - CloudKit success but KVS mirror fails") {
    beforeTest {
      featureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    }

    test("setString returns success even when KVS mirror fails") {
      ubiquitousStoreFake.returnError = true

      store.setString(account, key = "test-key", value = "test-value").shouldBeOk()

      cloudKitStoreFake.get(account, key = "test-key").shouldBeOk("test-value".encodeUtf8())
    }

    test("removeString returns success even when KVS mirror fails") {
      cloudKitStoreFake.set(account, key = "test-key", value = "test-value".encodeUtf8())
      ubiquitousStoreFake.returnError = true

      store.removeString(account, key = "test-key").shouldBeOk()

      cloudKitStoreFake.get(account, key = "test-key").shouldBeOk(null)
    }
  }
})
