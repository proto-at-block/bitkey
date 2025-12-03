package build.wallet.bitkey.keybox

import bitkey.account.FullAccountConfig
import bitkey.account.HardwareType
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingPublicKey
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.f8e.isPrivateWallet
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.PrivateSpendingKeysetMock
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.f8e.F8eEnvironment.Development
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage

class KeyboxTests : FunSpec({
  val config = FullAccountConfig(
    bitcoinNetworkType = SIGNET,
    isHardwareFake = false,
    f8eEnvironment = Development,
    isTestAccount = false,
    isUsingSocRecFakes = false,
    hardwareType = HardwareType.W1
  )

  fun createKeybox(
    activeSpendingKeyset: SpendingKeyset,
    keysets: List<SpendingKeyset> = listOf(activeSpendingKeyset),
    localId: String = "test-keybox-id",
  ) = Keybox(
    localId = localId,
    config = config,
    fullAccountId = FullAccountIdMock,
    activeSpendingKeyset = activeSpendingKeyset,
    activeAppKeyBundle = AppKeyBundleMock,
    activeHwKeyBundle = HwKeyBundleMock,
    appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
    keysets = keysets,
    canUseKeyboxKeysets = true
  )

  fun createSpendingKeyset(
    localId: String,
    f8eKeysetId: String,
    privateWalletRootXpub: String? = null,
  ) = SpendingKeyset(
    localId = localId,
    networkType = SIGNET,
    appKey = AppSpendingPublicKey(DescriptorPublicKeyMock("$localId-app-dpub")),
    hardwareKey = HwSpendingPublicKey(DescriptorPublicKeyMock("$localId-hw-dpub")),
    f8eSpendingKeyset = F8eSpendingKeyset(
      keysetId = f8eKeysetId,
      spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("$localId-server-dpub")),
      privateWalletRootXpub = privateWalletRootXpub
    )
  )

  test("should not allow empty keysets") {
    val exception = shouldThrow<IllegalArgumentException> {
      createKeybox(
        activeSpendingKeyset = SpendingKeysetMock,
        keysets = emptyList()
      )
    }

    exception.shouldHaveMessage("activeSpendingKeyset must be present in keysets!")
  }

  test("should allow keysets containing the active spending keyset") {
    val keybox = createKeybox(activeSpendingKeyset = SpendingKeysetMock)

    keybox.keysets.shouldBe(listOf(SpendingKeysetMock))
  }

  test("should allow keysets containing the active spending keyset with other keysets") {
    val keybox = createKeybox(
      activeSpendingKeyset = SpendingKeysetMock,
      keysets = listOf(PrivateSpendingKeysetMock, SpendingKeysetMock)
    )

    keybox.keysets.shouldBe(listOf(PrivateSpendingKeysetMock, SpendingKeysetMock))
  }

  test("should throw when keysets is not empty but doesn't contain active spending keyset") {
    val exception = shouldThrow<IllegalArgumentException> {
      createKeybox(
        activeSpendingKeyset = SpendingKeysetMock,
        keysets = listOf(PrivateSpendingKeysetMock)
      )
    }

    exception.shouldHaveMessage("activeSpendingKeyset must be present in keysets!")
  }

  test("isPrivate returns false for legacy keysets without privateWalletRootXpub") {
    val legacyKeyset = createSpendingKeyset(
      localId = "legacy-keyset",
      f8eKeysetId = "legacy-f8e-keyset-id",
      privateWalletRootXpub = null
    )

    val keybox = createKeybox(activeSpendingKeyset = legacyKeyset)
    keybox.activeSpendingKeyset.f8eSpendingKeyset.isPrivateWallet.shouldBe(false)
  }

  test("isPrivate returns true for private keysets with privateWalletRootXpub") {
    val privateKeyset = createSpendingKeyset(
      localId = "private-keyset",
      f8eKeysetId = "private-f8e-keyset-id",
      privateWalletRootXpub = "tpubD6NzVbkrYhZ4XPMXVToEroepyTscQmHYrdSDbvZvAFonusog8TjTB3iTQZ2Ds8atDfdxzN7DAioQ8Z4KBa4RD16FX7caE5hxiMbvkVr9Fom"
    )

    val keybox = createKeybox(activeSpendingKeyset = privateKeyset)
    keybox.activeSpendingKeyset.f8eSpendingKeyset.isPrivateWallet.shouldBe(true)
  }
})
