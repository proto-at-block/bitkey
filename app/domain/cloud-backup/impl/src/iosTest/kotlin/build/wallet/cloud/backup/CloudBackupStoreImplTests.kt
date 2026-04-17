package build.wallet.cloud.backup

import build.wallet.cloud.store.CloudError
import build.wallet.cloud.store.CloudKeyValueStoreFake
import build.wallet.cloud.store.CloudKitKeyValueStoreFake
import build.wallet.cloud.store.iCloudAccount
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.IosCloudKitBackupFeatureFlag
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import okio.ByteString.Companion.encodeUtf8

class CloudBackupStoreImplTests : FunSpec({
  val featureFlagDao = FeatureFlagDaoFake()
  val iosCloudKitBackupFeatureFlag = IosCloudKitBackupFeatureFlag(featureFlagDao)
  val cloudKeyValueStore = CloudKeyValueStoreFake()
  val cloudKitKeyValueStore = CloudKitKeyValueStoreFake()
  val cloudBackupStore = CloudBackupStoreImpl(
    cloudKeyValueStore = cloudKeyValueStore,
    cloudKitKeyValueStore = cloudKitKeyValueStore,
    iosCloudKitBackupFeatureFlag = iosCloudKitBackupFeatureFlag
  )

  val account = iCloudAccount(ubiquityIdentityToken = "test-token")
  val key = "backup-key"
  val value = "backup-value".encodeUtf8()

  beforeTest {
    featureFlagDao.reset()
    cloudKeyValueStore.reset()
    cloudKitKeyValueStore.reset()
  }

  test("flag OFF delegates set/get/remove/keys to KVS only") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))

    cloudBackupStore.set(account, key, value).shouldBeOk()
    cloudBackupStore.get(account, key).shouldBeOk(value)
    cloudBackupStore.keys(account).shouldBeOk(listOf(key))

    cloudBackupStore.remove(account, key).shouldBeOk()
    cloudBackupStore.get(account, key).shouldBeOk(null)

    cloudKitKeyValueStore.get(account, key).shouldBeOk(null)
  }

  test("flag ON writes to CloudKit and mirrors to KVS") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    cloudBackupStore.set(account, key, value).shouldBeOk()

    cloudKitKeyValueStore.get(account, key).shouldBeOk(value)
    cloudKeyValueStore.getString(account, key).shouldBeOk(value.utf8())
  }

  test("flag ON removes from CloudKit and mirrors removal to KVS") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    cloudBackupStore.set(account, key, value).shouldBeOk()

    cloudBackupStore.remove(account, key).shouldBeOk()

    cloudKitKeyValueStore.get(account, key).shouldBeOk(null)
    cloudKeyValueStore.getString(account, key).shouldBeOk(null)
  }

  test("flag ON get uses CloudKit value when CloudKit succeeds") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    cloudKitKeyValueStore.set(account, key, "cloud-kit".encodeUtf8()).shouldBeOk()
    cloudKeyValueStore.setString(account, key, "kvs").shouldBeOk()

    cloudBackupStore.get(account, key).shouldBeOk("cloud-kit".encodeUtf8())
  }

  test("flag ON get falls back to KVS when CloudKit errors") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    cloudKeyValueStore.setString(account, key, "kvs").shouldBeOk()
    cloudKitKeyValueStore.returnError = true

    cloudBackupStore.get(account, key).shouldBeOk("kvs".encodeUtf8())
  }

  test("flag ON get falls back to KVS when CloudKit returns null") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    cloudKeyValueStore.setString(account, key, "kvs").shouldBeOk()

    cloudBackupStore.get(account, key).shouldBeOk("kvs".encodeUtf8())
  }

  test("flag ON get returns null when CloudKit returns null and KVS read fails") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    cloudKeyValueStore.returnError = true

    cloudBackupStore.get(account, key).shouldBeOk(null)
  }

  test("flag ON get returns error when both CloudKit and KVS fail") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    cloudKitKeyValueStore.returnError = true
    cloudKeyValueStore.returnError = true

    cloudBackupStore.get(account, key).shouldBeErrOfType<CloudError>()
  }

  test("flag ON keys falls back to KVS when CloudKit errors") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    cloudKeyValueStore.setString(account, "kvs-key", "value").shouldBeOk()
    cloudKitKeyValueStore.returnError = true

    cloudBackupStore.keys(account).shouldBeOk(listOf("kvs-key"))
  }

  test("flag ON keys does not fall back to KVS when CloudKit returns empty keys") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    cloudKeyValueStore.setString(account, "kvs-key", "value").shouldBeOk()

    cloudBackupStore.keys(account).shouldBeOk(emptyList())
  }

  test("flag ON keys uses CloudKit result when CloudKit succeeds") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    cloudKitKeyValueStore.set(account, "cloud-kit-key", "value".encodeUtf8()).shouldBeOk()
    cloudKeyValueStore.setString(account, "kvs-key", "value").shouldBeOk()

    cloudBackupStore.keys(account).shouldBeOk(listOf("cloud-kit-key"))
  }

  test("flag ON keys returns error when both CloudKit and KVS fail") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    cloudKitKeyValueStore.returnError = true
    cloudKeyValueStore.returnKeysError = true

    cloudBackupStore.keys(account).shouldBeErrOfType<CloudError>()
  }

  test("flag ON set succeeds when KVS mirror write fails") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    cloudKeyValueStore.returnError = true

    cloudBackupStore.set(account, key, value).shouldBeOk()

    cloudKitKeyValueStore.get(account, key).shouldBeOk(value)
  }

  test("flag ON set returns CloudKit error and still mirrors to KVS when CloudKit write fails") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    cloudKitKeyValueStore.returnError = true

    cloudBackupStore.set(account, key, value).shouldBeErrOfType<CloudError>()

    cloudKeyValueStore.getString(account, key).shouldBeOk(value.utf8())
  }

  test("flag ON remove succeeds when KVS mirror removal fails") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    cloudBackupStore.set(account, key, value).shouldBeOk()
    cloudKeyValueStore.returnError = true

    cloudBackupStore.remove(account, key).shouldBeOk()

    cloudKitKeyValueStore.get(account, key).shouldBeOk(null)
  }

  test("flag ON remove returns CloudKit error and still mirrors removal to KVS when CloudKit remove fails") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    cloudBackupStore.set(account, key, value).shouldBeOk()
    cloudKitKeyValueStore.returnError = true

    cloudBackupStore.remove(account, key).shouldBeErrOfType<CloudError>()

    cloudKeyValueStore.getString(account, key).shouldBeOk(null)
  }
})
