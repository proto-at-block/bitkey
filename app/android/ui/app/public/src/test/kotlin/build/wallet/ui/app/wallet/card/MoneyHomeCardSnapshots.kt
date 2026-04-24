package build.wallet.ui.app.wallet.card

import app.cash.paparazzi.DeviceConfig
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.ui.app.moneyhome.card.*
import io.kotest.core.spec.style.FunSpec

class MoneyHomeCardSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("Money Home Card Price Card Loading") {
    paparazzi.snapshot {
      PreviewMoneyHomePriceCard(isLoading = true)
    }
  }

  test("Money Home Card Price Card Loaded") {
    paparazzi.snapshot {
      PreviewMoneyHomePriceCard(isLoading = false)
    }
  }

  test("Money Home Card Price Card Loaded with design system v2") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      PreviewMoneyHomePriceCard(isLoading = false)
    }
  }

  test("Money Home Card Price Card Large Font") {
    paparazzi.snapshot(deviceConfig = DeviceConfig.PIXEL_6.copy(fontScale = 1.5f)) {
      PreviewMoneyHomePriceCard(isLoading = false)
    }
  }

  test("Money Home Card Price Card Large Font Loading") {
    paparazzi.snapshot(deviceConfig = DeviceConfig.PIXEL_6.copy(fontScale = 1.5f)) {
      PreviewMoneyHomePriceCard(isLoading = true)
    }
  }

  test("Money Home Card Price Card Huge Font") {
    paparazzi.snapshot(deviceConfig = DeviceConfig.PIXEL_6.copy(fontScale = 2f)) {
      PreviewMoneyHomePriceCard(isLoading = false)
    }
  }

  test("Money Home Card Price Card Huge Font Loading") {
    paparazzi.snapshot(deviceConfig = DeviceConfig.PIXEL_6.copy(fontScale = 2f)) {
      PreviewMoneyHomePriceCard(isLoading = true)
    }
  }

  test("Money Home Card Getting Started") {
    paparazzi.snapshot {
      PreviewMoneyHomeGettingStarted()
    }
  }

  test("Money Home Card Getting Started with firmware update") {
    paparazzi.snapshot {
      PreviewMoneyHomeGettingStartedWithFirmwareUpdate()
    }
  }

  test("Money Home Card Getting Started with firmware update and design system v2") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      PreviewMoneyHomeGettingStartedWithFirmwareUpdate()
    }
  }

  test("Money Home Card Replacement Pending") {
    paparazzi.snapshot {
      PreviewMoneyHomeCardReplacementPending()
    }
  }

  test("Money Home Card Replacement Ready") {
    paparazzi.snapshot {
      PreviewMoneyHomeCardReplacementReady()
    }
  }

  test("Money Home Card Inactive Wallet with design system v2") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      PreviewMoneyHomeCardInactiveWallet()
    }
  }

  test("Money Home Card Benefactor Pending Claim with design system v2") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      PreviewMoneyHomeCardBenefactorPendingClaim()
    }
  }

  test("Money Home Card Benefactor Approved Claim with design system v2") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      PreviewMoneyHomeCardBenefactorApprovedClaim()
    }
  }

  test("Money Home Card Beneficiary Pending Claim with design system v2") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      PreviewMoneyHomeCardBeneficiaryPendingClaim()
    }
  }

  test("Money Home Card Wallets Protecting") {
    paparazzi.snapshot {
      PreviewMoneyHomeCardWalletsProtecting()
    }
  }

  test("Money Home Card Buy Own Bitkey") {
    paparazzi.snapshot {
      PreviewMoneyHomeCardBuyOwnBitkey()
    }
  }

  test("Money Home Card Inheritance with design system v2") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      PreviewInheritanceMoneyHomeCard()
    }
  }

  test("Money Home Card Pending Invitation") {
    paparazzi.snapshot {
      PreviewMoneyHomeCardInvitationPending()
    }
  }

  test("Money Home Card Expired Invitation") {
    paparazzi.snapshot {
      PreviewMoneyHomeCardInvitationExpired()
    }
  }
})