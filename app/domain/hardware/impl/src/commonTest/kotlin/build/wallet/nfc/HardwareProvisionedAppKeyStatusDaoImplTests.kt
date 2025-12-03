package build.wallet.nfc

import bitkey.account.HardwareType
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.auth.HwAuthSecp256k1PublicKeyMock
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingPublicKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.crypto.PublicKey
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.f8e.F8eEnvironment
import build.wallet.platform.random.UuidGeneratorFake
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class HardwareProvisionedAppKeyStatusDaoImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()
  val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
  val uuidGenerator = UuidGeneratorFake()
  val dao = HardwareProvisionedAppKeyStatusDaoImpl(
    databaseProvider = databaseProvider,
    clock = ClockFake()
  )

  beforeTest {
    dao.clear()
  }

  test("recordProvisionedKey - successfully records a key") {
    val result = dao.recordProvisionedKey(
      hwAuthPubKey = HwAuthSecp256k1PublicKeyMock,
      appAuthPubKey = AppGlobalAuthPublicKeyMock
    )
    result.shouldBe(Ok(Unit))
  }

  test("isKeyProvisionedForActiveAccount - returns true when active account has provisioned key") {
    // Set up minimal active account with keybox
    val accountId = FullAccountId("test-account-id")
    val keyboxId = uuidGenerator.random()
    val hwAuthKey = HwAuthSecp256k1PublicKeyMock
    val appAuthKey = AppGlobalAuthPublicKeyMock

    databaseProvider.database().apply {
      // Create full account
      fullAccountQueries.insertFullAccount(accountId)
      fullAccountQueries.setActiveFullAccountId(accountId)

      // Create keybox
      keyboxQueries.insertKeybox(
        id = keyboxId,
        accountId = accountId,
        networkType = BitcoinNetworkType.BITCOIN,
        fakeHardware = true,
        f8eEnvironment = F8eEnvironment.Development,
        isTestAccount = true,
        isUsingSocRecFakes = false,
        delayNotifyDuration = null,
        appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature("test-signature"),
        canUseKeyboxKeysets = true,
        hardwareType = HardwareType.W1
      )

      // Create app key bundle
      appKeyBundleQueries.insertKeyBundle(
        id = uuidGenerator.random(),
        keyboxId = keyboxId,
        globalAuthKey = appAuthKey,
        spendingKey = AppSpendingPublicKey("[11111111/84'/0'/0']xpub6BemYiVNp19a1NRKFk6DcVH2SEjNwyQMDGrPUVHPZfZa6aSZgLpgNdDPZCQjy7hUgCYQPkKrNgbWLhYFLAFPzTwvBNhNXz2shcrtfbVKqek"),
        recoveryAuthKey = PublicKey<AppRecoveryAuthKey>("recovery-key"),
        isActive = true
      )

      // Create hw key bundle
      hwKeyBundleQueries.insertKeyBundle(
        id = uuidGenerator.random(),
        keyboxId = keyboxId,
        spendingKey = HwSpendingPublicKey("[22222222/84'/0'/0']xpub6BemYiVNp19a1NRKFk6DcVH2SEjNwyQMDGrPUVHPZfZa6aSZgLpgNdDPZCQjy7hUgCYQPkKrNgbWLhYFLAFPzTwvBNhNXz2shcrtfbVKqek"),
        authKey = hwAuthKey,
        isActive = true
      )

      // Create spending keyset
      spendingKeysetQueries.insertKeyset(
        id = uuidGenerator.random(),
        keyboxId = keyboxId,
        appKey = AppSpendingPublicKey("[11111111/84'/0'/0']xpub6BemYiVNp19a1NRKFk6DcVH2SEjNwyQMDGrPUVHPZfZa6aSZgLpgNdDPZCQjy7hUgCYQPkKrNgbWLhYFLAFPzTwvBNhNXz2shcrtfbVKqek"),
        hardwareKey = HwSpendingPublicKey("[22222222/84'/0'/0']xpub6BemYiVNp19a1NRKFk6DcVH2SEjNwyQMDGrPUVHPZfZa6aSZgLpgNdDPZCQjy7hUgCYQPkKrNgbWLhYFLAFPzTwvBNhNXz2shcrtfbVKqek"),
        serverKey = F8eSpendingKeyset(
          keysetId = "server-keyset-id",
          spendingPublicKey = F8eSpendingPublicKey("[33333333/84'/0'/0']xpub6BemYiVNp19a1NRKFk6DcVH2SEjNwyQMDGrPUVHPZfZa6aSZgLpgNdDPZCQjy7hUgCYQPkKrNgbWLhYFLAFPzTwvBNhNXz2shcrtfbVKqek"),
          privateWalletRootXpub = null
        ),
        isActive = true
      )
    }

    // Record the provisioned key
    dao.recordProvisionedKey(
      hwAuthPubKey = hwAuthKey,
      appAuthPubKey = appAuthKey
    )

    val result = dao.isKeyProvisionedForActiveAccount()
    result.shouldBe(Ok(true))
  }

  test("isKeyProvisionedForActiveAccount - returns false when no active account") {
    val result = dao.isKeyProvisionedForActiveAccount()
    result.shouldBe(Ok(false))
  }

  test("isKeyProvisionedForActiveAccount - returns false when active account has no provisioned key") {
    // Set up active account with keybox but don't record provisioned key
    val accountId = FullAccountId("test-account-id")
    val keyboxId = uuidGenerator.random()
    val hwAuthKey = HwAuthSecp256k1PublicKeyMock
    val appAuthKey = AppGlobalAuthPublicKeyMock

    databaseProvider.database().apply {
      fullAccountQueries.insertFullAccount(accountId)
      fullAccountQueries.setActiveFullAccountId(accountId)

      keyboxQueries.insertKeybox(
        id = keyboxId,
        accountId = accountId,
        networkType = BitcoinNetworkType.BITCOIN,
        fakeHardware = true,
        f8eEnvironment = F8eEnvironment.Development,
        isTestAccount = true,
        isUsingSocRecFakes = false,
        delayNotifyDuration = null,
        appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature("test-signature"),
        canUseKeyboxKeysets = true,
        hardwareType = HardwareType.W1
      )

      appKeyBundleQueries.insertKeyBundle(
        id = uuidGenerator.random(),
        keyboxId = keyboxId,
        globalAuthKey = appAuthKey,
        spendingKey = AppSpendingPublicKey("[11111111/84'/0'/0']xpub6BemYiVNp19a1NRKFk6DcVH2SEjNwyQMDGrPUVHPZfZa6aSZgLpgNdDPZCQjy7hUgCYQPkKrNgbWLhYFLAFPzTwvBNhNXz2shcrtfbVKqek"),
        recoveryAuthKey = PublicKey<AppRecoveryAuthKey>("recovery-key"),
        isActive = true
      )

      hwKeyBundleQueries.insertKeyBundle(
        id = uuidGenerator.random(),
        keyboxId = keyboxId,
        spendingKey = HwSpendingPublicKey("[22222222/84'/0'/0']xpub6BemYiVNp19a1NRKFk6DcVH2SEjNwyQMDGrPUVHPZfZa6aSZgLpgNdDPZCQjy7hUgCYQPkKrNgbWLhYFLAFPzTwvBNhNXz2shcrtfbVKqek"),
        authKey = hwAuthKey,
        isActive = true
      )

      spendingKeysetQueries.insertKeyset(
        id = uuidGenerator.random(),
        keyboxId = keyboxId,
        appKey = AppSpendingPublicKey("[11111111/84'/0'/0']xpub6BemYiVNp19a1NRKFk6DcVH2SEjNwyQMDGrPUVHPZfZa6aSZgLpgNdDPZCQjy7hUgCYQPkKrNgbWLhYFLAFPzTwvBNhNXz2shcrtfbVKqek"),
        hardwareKey = HwSpendingPublicKey("[22222222/84'/0'/0']xpub6BemYiVNp19a1NRKFk6DcVH2SEjNwyQMDGrPUVHPZfZa6aSZgLpgNdDPZCQjy7hUgCYQPkKrNgbWLhYFLAFPzTwvBNhNXz2shcrtfbVKqek"),
        serverKey = F8eSpendingKeyset(
          keysetId = "server-keyset-id",
          spendingPublicKey = F8eSpendingPublicKey("[33333333/84'/0'/0']xpub6BemYiVNp19a1NRKFk6DcVH2SEjNwyQMDGrPUVHPZfZa6aSZgLpgNdDPZCQjy7hUgCYQPkKrNgbWLhYFLAFPzTwvBNhNXz2shcrtfbVKqek"),
          privateWalletRootXpub = null
        ),
        isActive = true
      )
    }

    val result = dao.isKeyProvisionedForActiveAccount()
    result.shouldBe(Ok(false))
  }

  test("clear - removes all provisioned keys") {
    dao.recordProvisionedKey(
      hwAuthPubKey = HwAuthSecp256k1PublicKeyMock,
      appAuthPubKey = AppGlobalAuthPublicKeyMock
    )

    dao.clear()

    val result = dao.isKeyProvisionedForActiveAccount()
    result.shouldBe(Ok(false))
  }
})
