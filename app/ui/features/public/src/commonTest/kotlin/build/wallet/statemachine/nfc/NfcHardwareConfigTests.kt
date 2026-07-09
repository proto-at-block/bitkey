package build.wallet.statemachine.nfc

import bitkey.account.DefaultAccountConfigFake
import build.wallet.bitkey.keybox.FullAccountConfigMock
import build.wallet.bitkey.keybox.LiteAccountConfigMock
import build.wallet.bitkey.keybox.SoftwareAccountConfigMock
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.platform.config.AppVariant.Development
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class NfcHardwareConfigTests : FunSpec({

  test("full account config uses its own fake hardware setting") {
    isHardwareFakeForNfc(
      appVariant = Development,
      accountConfig = FullAccountConfigMock.copy(isHardwareFake = true),
      defaultConfig = DefaultAccountConfigFake.copy(isHardwareFake = false)
    ).shouldBe(true)

    isHardwareFakeForNfc(
      appVariant = Development,
      accountConfig = FullAccountConfigMock.copy(isHardwareFake = false),
      defaultConfig = DefaultAccountConfigFake.copy(isHardwareFake = true)
    ).shouldBe(false)
  }

  test("default account config uses its own fake hardware setting") {
    isHardwareFakeForNfc(
      appVariant = Development,
      accountConfig = DefaultAccountConfigFake.copy(isHardwareFake = true),
      defaultConfig = DefaultAccountConfigFake.copy(isHardwareFake = false)
    ).shouldBe(true)

    isHardwareFakeForNfc(
      appVariant = Development,
      accountConfig = DefaultAccountConfigFake.copy(isHardwareFake = false),
      defaultConfig = DefaultAccountConfigFake.copy(isHardwareFake = true)
    ).shouldBe(false)
  }

  test("lite account uses default fake hardware only in debug builds") {
    isHardwareFakeForNfc(
      appVariant = Development,
      accountConfig = LiteAccountConfigMock,
      defaultConfig = DefaultAccountConfigFake.copy(isHardwareFake = true)
    ).shouldBe(true)

    isHardwareFakeForNfc(
      appVariant = Development,
      accountConfig = LiteAccountConfigMock,
      defaultConfig = DefaultAccountConfigFake.copy(isHardwareFake = false)
    ).shouldBe(false)

    isHardwareFakeForNfc(
      appVariant = Customer,
      accountConfig = LiteAccountConfigMock,
      defaultConfig = DefaultAccountConfigFake.copy(isHardwareFake = true)
    ).shouldBe(false)
  }

  test("software account is never treated as fake hardware") {
    isHardwareFakeForNfc(
      appVariant = Development,
      accountConfig = SoftwareAccountConfigMock,
      defaultConfig = DefaultAccountConfigFake.copy(isHardwareFake = true)
    ).shouldBe(false)
  }

  test("account hardware config does not apply lite fallback") {
    isAccountHardwareFakeForNfc(
      accountConfig = LiteAccountConfigMock
    ).shouldBe(false)
  }
})
