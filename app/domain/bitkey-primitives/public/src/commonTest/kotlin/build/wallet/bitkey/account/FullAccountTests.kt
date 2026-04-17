package build.wallet.bitkey.account

import bitkey.account.HardwareType
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FullAccountTests : FunSpec({
  test("config always reflects keybox.config") {
    val account = FullAccountMock
    account.config.shouldBe(account.keybox.config)
  }

  test("config reflects updated keybox after copy") {
    val account = FullAccountMock
    account.config.hardwareType.shouldBe(HardwareType.W1)

    val w3Keybox = account.keybox.copy(
      config = account.keybox.config.copy(hardwareType = HardwareType.W3)
    )
    val updatedAccount = account.copy(keybox = w3Keybox)

    updatedAccount.config.hardwareType.shouldBe(HardwareType.W3)
    updatedAccount.config.shouldBe(updatedAccount.keybox.config)
  }

  test("config cannot diverge from keybox.config") {
    val w3Keybox = KeyboxMock.copy(
      config = KeyboxMock.config.copy(hardwareType = HardwareType.W3)
    )
    val account = FullAccountMock.copy(keybox = w3Keybox)

    // config is always derived from keybox.config
    account.config.hardwareType.shouldBe(HardwareType.W3)
  }
})
