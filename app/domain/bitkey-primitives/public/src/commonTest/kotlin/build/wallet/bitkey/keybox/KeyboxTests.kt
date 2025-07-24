package build.wallet.bitkey.keybox

import bitkey.account.FullAccountConfig
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.bitkey.spending.SpendingKeysetMock2
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
    isUsingSocRecFakes = false
  )

  test("should not allow empty keysets") {
    val exception = shouldThrow<IllegalArgumentException> {
      Keybox(
        localId = "test-keybox-id",
        config = config,
        fullAccountId = FullAccountIdMock,
        activeSpendingKeyset = SpendingKeysetMock,
        activeAppKeyBundle = AppKeyBundleMock,
        activeHwKeyBundle = HwKeyBundleMock,
        appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
        keysets = emptyList(),
        canUseKeyboxKeysets = true
      )
    }

    exception.shouldHaveMessage("activeSpendingKeyset must be present in keysets!")
  }

  test("should allow keysets containing the active spending keyset") {
    val keybox = Keybox(
      localId = "test-keybox-id",
      config = config,
      fullAccountId = FullAccountIdMock,
      activeSpendingKeyset = SpendingKeysetMock,
      activeAppKeyBundle = AppKeyBundleMock,
      activeHwKeyBundle = HwKeyBundleMock,
      appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
      keysets = listOf(SpendingKeysetMock),
      canUseKeyboxKeysets = true
    )

    keybox.keysets.shouldBe(listOf(SpendingKeysetMock))
  }

  test("should allow keysets containing the active spending keyset with other keysets") {
    val keybox = Keybox(
      localId = "test-keybox-id",
      config = config,
      fullAccountId = FullAccountIdMock,
      activeSpendingKeyset = SpendingKeysetMock,
      activeAppKeyBundle = AppKeyBundleMock,
      activeHwKeyBundle = HwKeyBundleMock,
      appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
      keysets = listOf(SpendingKeysetMock2, SpendingKeysetMock),
      canUseKeyboxKeysets = true
    )

    keybox.keysets.shouldBe(listOf(SpendingKeysetMock2, SpendingKeysetMock))
  }

  test("should throw when keysets is not empty but doesn't contain active spending keyset") {
    val exception = shouldThrow<IllegalArgumentException> {
      Keybox(
        localId = "test-keybox-id",
        config = config,
        fullAccountId = FullAccountIdMock,
        activeSpendingKeyset = SpendingKeysetMock,
        activeAppKeyBundle = AppKeyBundleMock,
        activeHwKeyBundle = HwKeyBundleMock,
        appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
        keysets = listOf(SpendingKeysetMock2),
        canUseKeyboxKeysets = true
      )
    }

    exception.shouldHaveMessage("activeSpendingKeyset must be present in keysets!")
  }
})
