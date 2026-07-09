package build.wallet.cloud.backup

import build.wallet.cloud.store.CloudError
import build.wallet.cloud.store.CloudKeyValueStoreFake
import build.wallet.cloud.store.CloudKitKeyValueStore
import build.wallet.cloud.store.CloudKitKeyValueStoreFake
import build.wallet.cloud.store.iCloudAccount
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.IosCloudKitBackupFeatureFlag
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

class CloudBackupStoreImplTests : FunSpec({
  val featureFlagDao = FeatureFlagDaoFake()
  val iosCloudKitBackupFeatureFlag = IosCloudKitBackupFeatureFlag(featureFlagDao)
  val cloudKeyValueStore = CloudKeyValueStoreFake()
  val cloudKitKeyValueStore = CloudKitKeyValueStoreFake()
  val jsonSerializer = JsonSerializer()
  lateinit var cloudBackupStore: CloudBackupStoreImpl

  val account = iCloudAccount(ubiquityIdentityToken = "test-token")
  val otherAccount = iCloudAccount(ubiquityIdentityToken = "other-token")
  val key = "backup-key"
  val value = "backup-value".encodeUtf8()

  fun newCloudBackupStore() =
    CloudBackupStoreImpl(
      cloudKeyValueStore = cloudKeyValueStore,
      cloudKitKeyValueStore = cloudKitKeyValueStore,
      iosCloudKitBackupFeatureFlag = iosCloudKitBackupFeatureFlag,
      jsonSerializer = jsonSerializer
    )

  fun CloudBackup.encodeForStore(): ByteString =
    when (this) {
      is CloudBackupV2 -> jsonSerializer.encodeToStringResult<CloudBackupV2>(this)
      is CloudBackupV3 -> jsonSerializer.encodeToStringResult<CloudBackupV3>(this)
    }.shouldBeOk().encodeUtf8()

  beforeTest {
    featureFlagDao.reset()
    cloudKeyValueStore.reset()
    cloudKitKeyValueStore.reset()
    cloudBackupStore = newCloudBackupStore()
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
    cloudBackupStore.keys(account).shouldBeOk(listOf(key))
  }

  test("flag ON writes CloudKit populated marker with record-name-safe prefix") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    cloudBackupStore.set(account, key, value).shouldBeOk()

    val markerKey = "$CLOUD_KIT_TEST_POPULATED_MARKER_KEY_PREFIX$key"
    markerKey.startsWith("_") shouldBe false
    cloudKitKeyValueStore.get(account, markerKey)
      .shouldBeOk(CLOUD_KIT_TEST_POPULATED_MARKER_VALUE.encodeUtf8())
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

  test("flag ON get uses KVS V3 backup when it is fresher than CloudKit V3 backup") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    val cloudKitBackup = CloudBackupV3WithFullAccountMock.copy(
      createdAt = Instant.parse("2024-01-01T00:00:00Z"),
      deviceNickname = "cloudkit-old"
    )
    val kvsBackup = CloudBackupV3WithFullAccountMock.copy(
      createdAt = Instant.parse("2024-02-01T00:00:00Z"),
      deviceNickname = "kvs-new"
    )
    cloudKitKeyValueStore.set(account, key, cloudKitBackup.encodeForStore()).shouldBeOk()
    cloudKeyValueStore.setString(account, key, kvsBackup.encodeForStore().utf8()).shouldBeOk()

    cloudBackupStore.get(account, key).shouldBeOk(kvsBackup.encodeForStore())
  }

  test("flag ON get keeps CloudKit backup when fresher KVS backup is for different account") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    val cloudKitBackup = CloudBackupV3WithFullAccountMock.copy(
      accountId = "cloudkit-account",
      createdAt = Instant.parse("2024-01-01T00:00:00Z"),
      deviceNickname = "cloudkit-account"
    )
    val kvsBackup = CloudBackupV3WithFullAccountMock.copy(
      accountId = "kvs-account",
      createdAt = Instant.parse("2024-02-01T00:00:00Z"),
      deviceNickname = "kvs-account"
    )
    cloudKitKeyValueStore.set(account, key, cloudKitBackup.encodeForStore()).shouldBeOk()
    cloudKeyValueStore.setString(account, key, kvsBackup.encodeForStore().utf8()).shouldBeOk()

    cloudBackupStore.get(account, key).shouldBeOk(cloudKitBackup.encodeForStore())
  }

  test("flag ON get keeps CloudKit V3 backup when KVS has equal freshness") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    val createdAt = Instant.parse("2024-02-01T00:00:00Z")
    val cloudKitBackup = CloudBackupV3WithFullAccountMock.copy(
      createdAt = createdAt,
      deviceNickname = "cloudkit"
    )
    val kvsBackup = CloudBackupV3WithFullAccountMock.copy(
      createdAt = createdAt,
      deviceNickname = "kvs"
    )
    cloudKitKeyValueStore.set(account, key, cloudKitBackup.encodeForStore()).shouldBeOk()
    cloudKeyValueStore.setString(account, key, kvsBackup.encodeForStore().utf8()).shouldBeOk()

    cloudBackupStore.get(account, key).shouldBeOk(cloudKitBackup.encodeForStore())
  }

  test("flag ON get keeps CloudKit V3 backup when KVS has older V3 backup") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    val cloudKitBackup = CloudBackupV3WithFullAccountMock.copy(
      createdAt = Instant.parse("2024-02-01T00:00:00Z"),
      deviceNickname = "cloudkit-new"
    )
    val kvsBackup = CloudBackupV3WithFullAccountMock.copy(
      createdAt = Instant.parse("2024-01-01T00:00:00Z"),
      deviceNickname = "kvs-old"
    )
    cloudKitKeyValueStore.set(account, key, cloudKitBackup.encodeForStore()).shouldBeOk()
    cloudKeyValueStore.setString(account, key, kvsBackup.encodeForStore().utf8()).shouldBeOk()

    cloudBackupStore.get(account, key).shouldBeOk(cloudKitBackup.encodeForStore())
  }

  test("flag ON get keeps CloudKit V3 backup when KVS has V2 backup") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    val cloudKitBackup = CloudBackupV3WithFullAccountMock.copy(deviceNickname = "cloudkit-v3")
    val kvsBackup = CloudBackupV2WithFullAccountMock
    cloudKitKeyValueStore.set(account, key, cloudKitBackup.encodeForStore()).shouldBeOk()
    cloudKeyValueStore.setString(account, key, kvsBackup.encodeForStore().utf8()).shouldBeOk()

    cloudBackupStore.get(account, key).shouldBeOk(cloudKitBackup.encodeForStore())
  }

  test("flag ON get uses KVS V3 backup when CloudKit has V2 backup") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    val cloudKitBackup = CloudBackupV2WithFullAccountMock
    val kvsBackup = CloudBackupV3WithFullAccountMock.copy(deviceNickname = "kvs-v3")
    cloudKitKeyValueStore.set(account, key, cloudKitBackup.encodeForStore()).shouldBeOk()
    cloudKeyValueStore.setString(account, key, kvsBackup.encodeForStore().utf8()).shouldBeOk()

    cloudBackupStore.get(account, key).shouldBeOk(kvsBackup.encodeForStore())
  }

  test("flag ON get returns CloudKit value when marker write fails") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    val delegate = CloudKitKeyValueStoreFake()
    val markerFailingCloudKitKeyValueStore = MarkerFailingCloudKitKeyValueStore(delegate)
    val cloudBackupStore = CloudBackupStoreImpl(
      cloudKeyValueStore = cloudKeyValueStore,
      cloudKitKeyValueStore = markerFailingCloudKitKeyValueStore,
      iosCloudKitBackupFeatureFlag = iosCloudKitBackupFeatureFlag,
      jsonSerializer = jsonSerializer
    )
    delegate.set(account, key, value).shouldBeOk()
    markerFailingCloudKitKeyValueStore.failMarkerWrites = true

    cloudBackupStore.get(account, key).shouldBeOk(value)

    delegate.remove(account, key).shouldBeOk()
    cloudKeyValueStore.setString(account, key, "stale-kvs").shouldBeOk()

    cloudBackupStore.get(account, key).shouldBeOk(null)
  }

  test("flag ON get falls back to KVS when CloudKit errors") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    cloudKeyValueStore.setString(account, key, "kvs").shouldBeOk()
    cloudKitKeyValueStore.returnError = true

    cloudBackupStore.get(account, key).shouldBeOk("kvs".encodeUtf8())
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

  test("flag ON keys returns CloudKit keys and KVS-only keys when CloudKit succeeds") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    cloudKitKeyValueStore.set(account, "cloud-kit-key", "value".encodeUtf8()).shouldBeOk()
    cloudKeyValueStore.setString(account, "kvs-key", "value").shouldBeOk()

    cloudBackupStore.keys(account).shouldBeOk(listOf("cloud-kit-key", "kvs-key"))
  }

  test("flag ON keys returns CloudKit keys when marker write fails") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    val delegate = CloudKitKeyValueStoreFake()
    val markerFailingCloudKitKeyValueStore = MarkerFailingCloudKitKeyValueStore(delegate)
    val cloudBackupStore = CloudBackupStoreImpl(
      cloudKeyValueStore = cloudKeyValueStore,
      cloudKitKeyValueStore = markerFailingCloudKitKeyValueStore,
      iosCloudKitBackupFeatureFlag = iosCloudKitBackupFeatureFlag,
      jsonSerializer = jsonSerializer
    )
    delegate.set(account, "cloud-kit-key", "value".encodeUtf8()).shouldBeOk()
    cloudKeyValueStore.setString(account, "kvs-key", "value").shouldBeOk()
    markerFailingCloudKitKeyValueStore.failMarkerWrites = true

    cloudBackupStore.keys(account).shouldBeOk(listOf("cloud-kit-key", "kvs-key"))

    delegate.remove(account, "cloud-kit-key").shouldBeOk()
    cloudKeyValueStore.setString(account, "cloud-kit-key", "stale-kvs").shouldBeOk()

    cloudBackupStore.keys(account).shouldBeOk(listOf("kvs-key"))
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

  test("flag ON set returns error when CloudKit marker write fails") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    val delegate = CloudKitKeyValueStoreFake()
    val markerFailingCloudKitKeyValueStore = MarkerFailingCloudKitKeyValueStore(delegate)
    val cloudBackupStore = CloudBackupStoreImpl(
      cloudKeyValueStore = cloudKeyValueStore,
      cloudKitKeyValueStore = markerFailingCloudKitKeyValueStore,
      iosCloudKitBackupFeatureFlag = iosCloudKitBackupFeatureFlag,
      jsonSerializer = jsonSerializer
    )

    markerFailingCloudKitKeyValueStore.failMarkerWrites = true

    cloudBackupStore.set(account, key, value).shouldBeErrOfType<CloudError>()

    delegate.get(account, key).shouldBeOk(value)
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

  test("flag ON remove returns error when CloudKit marker write fails") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    val delegate = CloudKitKeyValueStoreFake()
    val markerFailingCloudKitKeyValueStore = MarkerFailingCloudKitKeyValueStore(delegate)
    val cloudBackupStore = CloudBackupStoreImpl(
      cloudKeyValueStore = cloudKeyValueStore,
      cloudKitKeyValueStore = markerFailingCloudKitKeyValueStore,
      iosCloudKitBackupFeatureFlag = iosCloudKitBackupFeatureFlag,
      jsonSerializer = jsonSerializer
    )
    delegate.set(account, key, value).shouldBeOk()
    cloudKeyValueStore.setString(account, key, "stale-kvs").shouldBeOk()

    cloudKeyValueStore.returnError = true
    markerFailingCloudKitKeyValueStore.failMarkerWrites = true

    cloudBackupStore.remove(account, key).shouldBeErrOfType<CloudError>()

    delegate.get(account, key).shouldBeOk(null)
    cloudKeyValueStore.returnError = false
    cloudKeyValueStore.getString(account, key).shouldBeOk("stale-kvs")
  }

  test("flag ON remove retries marker write after prior repair failed") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    val delegate = CloudKitKeyValueStoreFake()
    val markerFailingCloudKitKeyValueStore = MarkerFailingCloudKitKeyValueStore(delegate)
    val cloudBackupStore = CloudBackupStoreImpl(
      cloudKeyValueStore = cloudKeyValueStore,
      cloudKitKeyValueStore = markerFailingCloudKitKeyValueStore,
      iosCloudKitBackupFeatureFlag = iosCloudKitBackupFeatureFlag,
      jsonSerializer = jsonSerializer
    )
    delegate.set(account, key, value).shouldBeOk()
    markerFailingCloudKitKeyValueStore.failMarkerWrites = true
    cloudBackupStore.get(account, key).shouldBeOk(value)
    cloudKeyValueStore.setString(account, key, "stale-kvs").shouldBeOk()

    cloudKeyValueStore.returnError = true

    cloudBackupStore.remove(account, key).shouldBeErrOfType<CloudError>()

    delegate.get(account, key).shouldBeOk(null)
    cloudKeyValueStore.returnError = false
    cloudKeyValueStore.getString(account, key).shouldBeOk("stale-kvs")
  }

  // --- Pre-migration KVS fallback (CloudKit null/empty, no prior write) ---

  test("flag ON get falls back to KVS when CloudKit returns null pre-migration") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    cloudKeyValueStore.setString(account, key, "kvs-legacy").shouldBeOk()

    // No prior write to CloudKit: pre-migration state.
    cloudBackupStore.get(account, key).shouldBeOk("kvs-legacy".encodeUtf8())
  }

  test("flag ON get falls back to KVS when marker read fails pre-migration") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    val delegate = CloudKitKeyValueStoreFake()
    val markerFailingCloudKitKeyValueStore = MarkerFailingCloudKitKeyValueStore(delegate)
    val cloudBackupStore = CloudBackupStoreImpl(
      cloudKeyValueStore = cloudKeyValueStore,
      cloudKitKeyValueStore = markerFailingCloudKitKeyValueStore,
      iosCloudKitBackupFeatureFlag = iosCloudKitBackupFeatureFlag,
      jsonSerializer = jsonSerializer
    )
    cloudKeyValueStore.setString(account, key, "kvs-legacy").shouldBeOk()
    markerFailingCloudKitKeyValueStore.failMarkerReads = true

    cloudBackupStore.get(account, key).shouldBeOk("kvs-legacy".encodeUtf8())
  }

  test("flag ON get returns null when both CloudKit and KVS are empty pre-migration") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    cloudBackupStore.get(account, key).shouldBeOk(null)
  }

  test("flag ON get returns null when CloudKit is null and KVS fallback fails pre-migration") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    cloudKeyValueStore.returnError = true

    cloudBackupStore.get(account, key).shouldBeOk(null)
  }

  test("flag ON keys falls back to KVS when CloudKit returns empty pre-migration") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    cloudKeyValueStore.setString(account, "kvs-key", "value").shouldBeOk()

    // No prior write to CloudKit: pre-migration state.
    cloudBackupStore.keys(account).shouldBeOk(listOf("kvs-key"))
  }

  test("flag ON keys returns empty when both CloudKit and KVS are empty pre-migration") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    cloudBackupStore.keys(account).shouldBeOk(emptyList())
  }

  test("flag ON keys returns empty when CloudKit is empty and KVS fallback fails pre-migration") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    cloudKeyValueStore.returnKeysError = true

    cloudBackupStore.keys(account).shouldBeOk(emptyList())
  }

  // --- Post-migration: CloudKit is authoritative after a successful write ---

  test("flag ON get does not fall back to KVS when CloudKit returns null post-migration") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    // Write then delete to simulate post-migration with a deletion.
    cloudBackupStore.set(account, key, value).shouldBeOk()
    cloudBackupStore.remove(account, key).shouldBeOk()

    // KVS still has stale data from a failed mirror delete
    cloudKeyValueStore.setString(account, key, "stale-kvs").shouldBeOk()

    // Should NOT resurrect the stale KVS data
    cloudBackupStore.get(account, key).shouldBeOk(null)
  }

  test("flag ON keys does not fall back to KVS when CloudKit returns empty post-migration") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    // Write then delete to simulate post-migration with a deletion.
    cloudBackupStore.set(account, key, value).shouldBeOk()
    cloudBackupStore.remove(account, key).shouldBeOk()

    // KVS still has stale data for the CloudKit-authoritative key.
    cloudKeyValueStore.setString(account, key, "stale-value").shouldBeOk()

    // Should NOT return stale KVS keys
    cloudBackupStore.keys(account).shouldBeOk(emptyList())
  }

  test("flag ON get falls back when only another key is CloudKit-authoritative") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    // CloudKit has data for one key, but another key can still be KVS-only.
    cloudKitKeyValueStore.set(account, "existing-key", value).shouldBeOk()
    cloudBackupStore.get(account, "existing-key").shouldBeOk(value)

    cloudKeyValueStore.setString(account, "other-key", "stale").shouldBeOk()

    cloudBackupStore.get(account, "other-key").shouldBeOk("stale".encodeUtf8())
  }

  test("flag ON keys does not fall back after CloudKit returned non-empty") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    // CloudKit has data, proving it has been populated.
    cloudKitKeyValueStore.set(account, "existing-key", value).shouldBeOk()
    cloudBackupStore.keys(account).shouldBeOk(listOf("existing-key"))

    cloudKitKeyValueStore.remove(account, "existing-key")
    cloudKeyValueStore.setString(account, "existing-key", "stale").shouldBeOk()

    // CloudKit is known to be populated for this key, so it should not fall back.
    cloudBackupStore.keys(account).shouldBeOk(emptyList())
  }

  test("flag ON get does not fall back to stale KVS after restart when CloudKit marker exists") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    cloudBackupStore.set(account, key, value).shouldBeOk()
    cloudBackupStore.remove(account, key).shouldBeOk()
    cloudKeyValueStore.setString(account, key, "stale-kvs").shouldBeOk()

    cloudBackupStore = newCloudBackupStore()

    cloudBackupStore.get(account, key).shouldBeOk(null)
  }

  test("flag ON keys does not fall back to stale KVS after restart when CloudKit marker exists") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    cloudBackupStore.set(account, key, value).shouldBeOk()
    cloudBackupStore.remove(account, key).shouldBeOk()
    cloudKeyValueStore.setString(account, key, "stale").shouldBeOk()

    cloudBackupStore = newCloudBackupStore()

    cloudBackupStore.keys(account).shouldBeOk(emptyList())
  }

  test("flag ON get falls back to KVS-only backup key in the same iCloud account") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    val cloudKitKey = "cb-account-a"
    val kvsOnlyKey = "cb-account-b"
    cloudKitKeyValueStore.set(account, cloudKitKey, "cloud-kit".encodeUtf8()).shouldBeOk()
    cloudBackupStore.get(account, cloudKitKey).shouldBeOk("cloud-kit".encodeUtf8())
    cloudKeyValueStore.setString(account, kvsOnlyKey, "legacy").shouldBeOk()

    cloudBackupStore.get(account, kvsOnlyKey).shouldBeOk("legacy".encodeUtf8())
  }

  test("flag ON keys returns KVS-only backup keys in the same iCloud account") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    val cloudKitKey = "cb-account-a"
    val kvsOnlyKey = "cb-account-b"
    cloudKitKeyValueStore.set(account, cloudKitKey, "cloud-kit".encodeUtf8()).shouldBeOk()
    cloudKeyValueStore.setString(account, kvsOnlyKey, "legacy").shouldBeOk()

    cloudBackupStore.keys(account).shouldBeOk(listOf(cloudKitKey, kvsOnlyKey))
  }

  test("flag ON get fallback population state is scoped by account") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    cloudKitKeyValueStore.set(account, key, value).shouldBeOk()
    cloudBackupStore.get(account, key).shouldBeOk(value)
    cloudKeyValueStore.setString(otherAccount, key, "legacy").shouldBeOk()

    cloudBackupStore.get(otherAccount, key).shouldBeOk("legacy".encodeUtf8())
  }

  test("flag ON keys fallback population state is scoped by account") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    cloudKitKeyValueStore.set(account, "cloud-kit-key", value).shouldBeOk()
    cloudBackupStore.keys(account).shouldBeOk(listOf("cloud-kit-key"))
    cloudKeyValueStore.setString(otherAccount, "legacy-key", "legacy").shouldBeOk()

    cloudBackupStore.keys(otherAccount).shouldBeOk(listOf("legacy-key"))
  }

  test("flag ON get still falls back to KVS on CloudKit error post-migration") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    // Establish post-migration state
    cloudBackupStore.set(account, key, value).shouldBeOk()

    // CloudKit now errors
    cloudKitKeyValueStore.returnError = true
    cloudKeyValueStore.setString(account, "other-key", "fallback").shouldBeOk()

    // Error fallback still works post-migration
    cloudBackupStore.get(account, "other-key").shouldBeOk("fallback".encodeUtf8())
  }

  test("flag ON keys still falls back to KVS on CloudKit error post-migration") {
    iosCloudKitBackupFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    // Establish post-migration state
    cloudBackupStore.set(account, key, value).shouldBeOk()

    // CloudKit now errors
    cloudKitKeyValueStore.returnError = true
    cloudKeyValueStore.setString(account, "kvs-key", "value").shouldBeOk()

    // Error fallback still works post-migration
    cloudBackupStore.keys(account).shouldBeOk(listOf(key, "kvs-key"))
  }
})

private const val CLOUD_KIT_TEST_POPULATED_MARKER_KEY_PREFIX = "bitkey_cloudkit_populated:"
private const val CLOUD_KIT_TEST_POPULATED_MARKER_VALUE = "true"

private class MarkerFailingCloudKitKeyValueStore(
  private val delegate: CloudKitKeyValueStoreFake,
) : CloudKitKeyValueStore {
  var failMarkerWrites = false
  var failMarkerReads = false

  override suspend fun set(
    account: iCloudAccount,
    key: String,
    value: ByteString,
  ): Result<Unit, CloudError> {
    return if (failMarkerWrites && key.startsWith(CLOUD_KIT_TEST_POPULATED_MARKER_KEY_PREFIX)) {
      Err(CloudError())
    } else {
      delegate.set(account, key, value)
    }
  }

  override suspend fun get(
    account: iCloudAccount,
    key: String,
  ): Result<ByteString?, CloudError> {
    return if (failMarkerReads && key.startsWith(CLOUD_KIT_TEST_POPULATED_MARKER_KEY_PREFIX)) {
      Err(CloudError())
    } else {
      delegate.get(account, key)
    }
  }

  override suspend fun remove(
    account: iCloudAccount,
    key: String,
  ): Result<Unit, CloudError> {
    return delegate.remove(account, key)
  }

  override suspend fun keys(account: iCloudAccount): Result<Set<String>, CloudError> {
    return delegate.keys(account)
  }
}
