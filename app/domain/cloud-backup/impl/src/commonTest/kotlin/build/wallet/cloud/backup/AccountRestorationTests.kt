package build.wallet.cloud.backup

import bitkey.account.FullAccountConfig
import bitkey.account.HardwareType
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.BitcoinNetworkType.TESTNET
import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.HwKeyBundleMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.cloud.backup.FullAccountCloudBackupRestorer.AccountRestoration
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment.Development
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AccountRestorationTests : FunSpec({

  val baseAccountRestoration = AccountRestoration(
    activeSpendingKeyset = SpendingKeysetMock,
    keysets = listOf(SpendingKeysetMock),
    activeAppKeyBundle = AppKeyBundle(
      localId = "app-key-bundle-id",
      spendingKey = AppSpendingPublicKey(DescriptorPublicKeyMock(identifier = "spending-dpub")),
      authKey = PublicKey("auth-dpub"),
      networkType = TESTNET,
      recoveryAuthKey = AppRecoveryAuthPublicKeyMock
    ),
    activeHwKeyBundle = HwKeyBundleMock,
    config = FullAccountConfig(
      isHardwareFake = false,
      bitcoinNetworkType = SIGNET,
      f8eEnvironment = Development,
      isTestAccount = false,
      isUsingSocRecFakes = false,
      hardwareType = HardwareType.W1
    ),
    cloudBackupForLocalStorage = CloudBackupV2WithFullAccountMock,
    appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock
  )

  test("asKeybox creates correct Keybox with all fields mapped properly") {
    val keyboxId = "test-keybox-id"
    val fullAccountId = FullAccountId("test-account-id")

    val keybox = baseAccountRestoration.asKeybox(
      keyboxId = keyboxId,
      fullAccountId = fullAccountId
    )

    keybox.localId.shouldBe(keyboxId)
    keybox.fullAccountId.shouldBe(fullAccountId)
    keybox.activeSpendingKeyset.shouldBe(baseAccountRestoration.activeSpendingKeyset)
    keybox.activeAppKeyBundle.shouldBe(baseAccountRestoration.activeAppKeyBundle)
    keybox.activeHwKeyBundle.shouldBe(baseAccountRestoration.activeHwKeyBundle)
    keybox.config.shouldBe(baseAccountRestoration.config)
    keybox.keysets.shouldBe(baseAccountRestoration.keysets)
    keybox.appGlobalAuthKeyHwSignature.shouldBe(baseAccountRestoration.appGlobalAuthKeyHwSignature)
  }

  test("asKeybox sets canUseKeyboxKeysets to true when keysets is not empty") {
    val keyboxId = "test-keybox-id"
    val fullAccountId = FullAccountId("test-account-id")

    val accountRestorationWithKeysets = baseAccountRestoration.copy(
      keysets = listOf(SpendingKeysetMock)
    )

    val keybox = accountRestorationWithKeysets.asKeybox(
      keyboxId = keyboxId,
      fullAccountId = fullAccountId
    )

    keybox.canUseKeyboxKeysets.shouldBe(true)
    keybox.keysets.shouldBe(listOf(SpendingKeysetMock))
  }

  test("asKeybox sets canUseKeyboxKeysets to false and uses active keyset when keysets is empty") {
    val keyboxId = "test-keybox-id"
    val fullAccountId = FullAccountId("test-account-id")

    val accountRestorationWithoutKeysets = baseAccountRestoration.copy(
      keysets = emptyList()
    )

    val keybox = accountRestorationWithoutKeysets.asKeybox(
      keyboxId = keyboxId,
      fullAccountId = fullAccountId
    )

    keybox.canUseKeyboxKeysets.shouldBe(false)
    keybox.keysets.shouldBe(listOf(SpendingKeysetMock))
  }
})
