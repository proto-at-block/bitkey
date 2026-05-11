package build.wallet.keybox

import app.cash.turbine.test
import bitkey.account.FullAccountConfig
import bitkey.account.HardwareType
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitkey.auth.AppAuthPublicKeysMock
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.keybox.AppKeyBundleMock
import build.wallet.bitkey.keybox.AppKeyBundleMock2
import build.wallet.bitkey.keybox.HwKeyBundleMock
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.keybox.withNewSpendingKeyset
import build.wallet.bitkey.spending.PrivateSpendingKeysetMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.f8e.F8eEnvironment.Development
import build.wallet.sqldelight.inMemorySqlDriver
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class KeyboxDaoImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()

  lateinit var databaseProvider: BitkeyDatabaseProviderImpl
  lateinit var dao: KeyboxDaoImpl

  val hwKeyBundle = HwKeyBundleMock

  val keyset1 = SpendingKeysetMock
  val appKeyBundle1 = AppKeyBundleMock.copy(
    spendingKey = keyset1.appKey
  )
  val config = FullAccountConfig(
    bitcoinNetworkType = SIGNET,
    isHardwareFake = false,
    f8eEnvironment = Development,
    isTestAccount = false,
    isUsingSocRecFakes = false,
    hardwareType = HardwareType.W1
  )
  val keybox1 = Keybox(
    localId = "keybox-1",
    fullAccountId = FullAccountIdMock,
    activeSpendingKeyset = keyset1,
    activeAppKeyBundle = appKeyBundle1,
    activeHwKeyBundle = hwKeyBundle,
    appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
    config = config,
    keysets = listOf(keyset1),
    canUseKeyboxKeysets = true
  )

  val keyset2 = PrivateSpendingKeysetMock
  val appKeyBundle2 = AppKeyBundleMock2.copy(
    spendingKey = keyset2.appKey
  )
  val keybox2 = Keybox(
    localId = "keybox-2",
    fullAccountId = FullAccountIdMock,
    activeSpendingKeyset = keyset2,
    activeAppKeyBundle = appKeyBundle2,
    activeHwKeyBundle = hwKeyBundle,
    appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
    config = config,
    keysets = listOf(keyset2),
    canUseKeyboxKeysets = true
  )

  beforeTest {
    databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    dao = KeyboxDaoImpl(
      databaseProvider
    )
  }

  test("save and activate new keybox") {
    dao.activeKeybox().test {
      awaitItem().shouldBe(Ok(null))

      dao.saveKeyboxAsActive(keybox1)

      awaitItem().shouldBe(Ok(keybox1))
    }
  }

  test("save and activate new keybox for onboarding, onboarding keybox") {
    dao.onboardingKeybox().test {
      awaitItem().shouldBe(Ok(null))

      dao.saveKeyboxAndBeginOnboarding(keybox1)
      awaitItem().shouldBe(Ok(keybox1))

      dao.activateNewKeyboxAndCompleteOnboarding(keybox1)
      awaitItem().shouldBe(Ok(null))
    }
  }

  test("save and activate new keybox for onboarding, active keybox") {
    dao.activeKeybox().test {
      awaitItem().shouldBe(Ok(null))

      dao.saveKeyboxAndBeginOnboarding(keybox1)
      expectNoEvents()

      dao.activateNewKeyboxAndCompleteOnboarding(keybox1)
      awaitItem().shouldBe(Ok(keybox1))
    }
  }

  test("save and activate the same new keybox") {
    dao.activeKeybox().test {
      awaitItem().shouldBe(Ok(null))

      dao.saveKeyboxAsActive(keybox1)
      dao.saveKeyboxAsActive(keybox1)

      awaitItem().shouldBe(Ok(keybox1))
    }
  }

  test("save and activate keybox") {
    dao.activeKeybox().test {
      awaitItem().shouldBe(Ok(null))

      dao.saveKeyboxAsActive(keybox1)

      awaitItem().shouldBe(Ok(keybox1))

      val keyset3 =
        PrivateSpendingKeysetMock.copy(
          localId = "3",
          f8eSpendingKeyset = PrivateSpendingKeysetMock.f8eSpendingKeyset.copy(keysetId = "server-3")
        )
      val keyBundle3 =
        AppKeyBundleMock2.copy(
          localId = "3",
          spendingKey = keyset3.appKey
        )

      val keybox3 = Keybox(
        localId = "fake-keybox-3",
        fullAccountId = FullAccountIdMock,
        activeHwKeyBundle = hwKeyBundle,
        activeSpendingKeyset = keyset3,
        activeAppKeyBundle = keyBundle3,
        appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
        config = config,
        // New keybox, but with keyset from old keybox.
        keysets = listOf(keyset1, keyset3),
        canUseKeyboxKeysets = true
      )

      dao.saveKeyboxAsActive(keybox3)

      awaitItem().shouldBe(Ok(keybox3))
    }
  }

  test("clear when no active keybox") {
    dao.activeKeybox().test {
      awaitItem().shouldBe(Ok(null))

      dao.clear()

      expectNoEvents()
    }
  }

  test("clear active keybox") {
    dao.activeKeybox().test {
      awaitItem().shouldBe(Ok(null))
      dao.saveKeyboxAsActive(keybox1)
      skipItems(1)

      dao.clear()

      awaitItem().shouldBe(Ok(null))
    }
  }

  test("save and activate different new keybox") {
    dao.activeKeybox().test {
      awaitItem().shouldBe(Ok(null))
      dao.saveKeyboxAsActive(keybox1)
      awaitItem().shouldBe(Ok(keybox1))
      dao.saveKeyboxAsActive(keybox2)
      awaitItem().shouldBe(Ok(keybox2))
    }
  }

  test("get active or onboarding keybox") {
    dao.getActiveOrOnboardingKeybox().shouldBe(Ok(null))
    dao.saveKeyboxAndBeginOnboarding(keybox1)
    dao.getActiveOrOnboardingKeybox().shouldBe(Ok(keybox1))
    dao.saveKeyboxAsActive(keybox2)
    dao.getActiveOrOnboardingKeybox().shouldBe(Ok(keybox2))
  }

  test("activate and retrieve a keybox with multiple keysets") {
    val keybox = keybox1.copy(
      keysets = listOf(keyset1, keyset2)
    )

    dao.getActiveOrOnboardingKeybox().shouldBe(Ok(null))
    dao.saveKeyboxAsActive(keybox)
    dao.getActiveOrOnboardingKeybox().shouldBe(Ok(keybox))
  }

  test("saving a replayed new spending keyset does not duplicate active keysets") {
    val replayedKeybox = keybox1
      .withNewSpendingKeyset(keyset2)
      .withNewSpendingKeyset(keyset2)

    dao.saveKeyboxAsActive(replayedKeybox)

    val persisted = dao.getActiveOrOnboardingKeybox().get().shouldNotBeNull()
    persisted.activeSpendingKeyset.shouldBe(keyset2)
    persisted.keysets.shouldBe(listOf(keyset1, keyset2))
    databaseProvider.database().spendingKeysetQueries.countSpendingKeysets()
      .executeAsOne()
      .shouldBe(2)
  }

  test("rotateKeyboxAuthKeys with newHwAuthPublicKey updates hw auth key in place") {
    dao.saveKeyboxAsActive(keybox1)

    val newHwAuthKey = HwAuthPublicKey(Secp256k1PublicKey("w3-hw-auth-dpub"))

    val result = dao.rotateKeyboxAuthKeys(
      keyboxToRotate = keybox1,
      appAuthKeys = AppAuthPublicKeysMock,
      newHwAuthPublicKey = newHwAuthKey
    )

    val rotatedKeybox = result.get().shouldNotBeNull()
    rotatedKeybox.activeHwKeyBundle.authKey.shouldBe(newHwAuthKey)
    rotatedKeybox.activeHwKeyBundle.localId.shouldBe(hwKeyBundle.localId)
    rotatedKeybox.activeHwKeyBundle.spendingKey.shouldBe(hwKeyBundle.spendingKey)

    // Verify persisted state: re-read from DB
    val persisted = dao.getActiveOrOnboardingKeybox().get().shouldNotBeNull()
    persisted.activeHwKeyBundle.authKey.shouldBe(newHwAuthKey)
    persisted.activeHwKeyBundle.localId.shouldBe(hwKeyBundle.localId)

    // In-place update: still only 1 hw bundle in table
    val count = databaseProvider.database().hwKeyBundleQueries.countHwKeyBundles().executeAsOne()
    count.shouldBe(1)
  }

  test("rotateKeyboxAuthKeys without newHwAuthPublicKey leaves hw auth key unchanged") {
    dao.saveKeyboxAsActive(keybox1)

    val result = dao.rotateKeyboxAuthKeys(
      keyboxToRotate = keybox1,
      appAuthKeys = AppAuthPublicKeysMock
    )

    val rotatedKeybox = result.get().shouldNotBeNull()
    rotatedKeybox.activeHwKeyBundle.shouldBe(hwKeyBundle)

    // Verify persisted state unchanged
    val persisted = dao.getActiveOrOnboardingKeybox().get().shouldNotBeNull()
    persisted.activeHwKeyBundle.shouldBe(hwKeyBundle)

    val count = databaseProvider.database().hwKeyBundleQueries.countHwKeyBundles().executeAsOne()
    count.shouldBe(1)
  }
})
