package build.wallet.ui.app.wallet.card

import app.cash.paparazzi.DeviceConfig
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.ui.app.moneyhome.card.*
import io.kotest.core.spec.style.FunSpec

class MoneyHomeCardSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("Money Home Card Price Card Loading") {
    paparazzi.snapshot {
      PreviewPriceCard(isLoading = true)
    }
  }

  test("Money Home Card Price Card Loaded") {
    paparazzi.snapshot {
      PreviewPriceCard(isLoading = false)
    }
  }

  test("Money Home Card Price Card Large Font") {
    paparazzi.snapshot(deviceConfig = DeviceConfig.PIXEL_6.copy(fontScale = 1.5f)) {
      PreviewPriceCard(isLoading = false)
    }
  }

  test("Money Home Card Price Card Large Font Loading") {
    paparazzi.snapshot(deviceConfig = DeviceConfig.PIXEL_6.copy(fontScale = 1.5f)) {
      PreviewPriceCard(isLoading = true)
    }
  }

  test("Money Home Card Price Card Huge Font") {
    paparazzi.snapshot(deviceConfig = DeviceConfig.PIXEL_6.copy(fontScale = 2f)) {
      PreviewPriceCard(isLoading = false)
    }
  }

  test("Money Home Card Price Card Huge Font Loading") {
    paparazzi.snapshot(deviceConfig = DeviceConfig.PIXEL_6.copy(fontScale = 2f)) {
      PreviewPriceCard(isLoading = true)
    }
  }

  test("Money Home Card Getting Started") {
    paparazzi.snapshot {
      PreviewGettingStarted()
    }
  }

  test("Money Home Card Getting Started with firmware update") {
    paparazzi.snapshot {
      PreviewGettingStartedWithFirmwareUpdate()
    }
  }

  test("Money Home Card Inactive Wallet") {
    paparazzi.snapshot {
      PreviewCardInactiveWallet()
    }
  }

  test("Money Home Card Benefactor Pending Claim") {
    paparazzi.snapshot {
      PreviewCardBenefactorPendingClaim()
    }
  }

  test("Money Home Card Benefactor Approved Claim") {
    paparazzi.snapshot {
      PreviewCardBenefactorApprovedClaim()
    }
  }

  test("Money Home Card Beneficiary Pending Claim") {
    paparazzi.snapshot {
      PreviewCardBeneficiaryPendingClaim()
    }
  }

  test("Money Home Card Wallets Protecting") {
    paparazzi.snapshot {
      PreviewCardWalletsProtecting()
    }
  }

  test("Money Home Card Buy Own Bitkey") {
    paparazzi.snapshot {
      PreviewCardBuyOwnBitkey()
    }
  }

  test("Money Home Card Inheritance") {
    paparazzi.snapshot {
      PreviewInheritanceCard()
    }
  }
})
