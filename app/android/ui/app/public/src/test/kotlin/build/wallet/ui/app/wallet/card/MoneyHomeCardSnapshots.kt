package build.wallet.ui.app.wallet.card

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.ui.app.moneyhome.card.*
import build.wallet.ui.app.moneyhome.card.PreviewCloudBackupHealthCard
import build.wallet.ui.app.moneyhome.card.PreviewMoneyHomeCardBuyOwnBitkey
import build.wallet.ui.app.moneyhome.card.PreviewMoneyHomeCardDeviceUpdate
import build.wallet.ui.app.moneyhome.card.PreviewMoneyHomeCardInvitationExpired
import build.wallet.ui.app.moneyhome.card.PreviewMoneyHomeCardInvitationPending
import build.wallet.ui.app.moneyhome.card.PreviewMoneyHomeCardReplacementPending
import build.wallet.ui.app.moneyhome.card.PreviewMoneyHomeCardReplacementReady
import build.wallet.ui.app.moneyhome.card.PreviewMoneyHomeCardWalletsProtecting
import build.wallet.ui.app.moneyhome.card.PreviewMoneyHomeGettingStarted
import build.wallet.ui.app.moneyhome.card.PreviewMoneyHomePriceCard
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

  test("Money Home Card Getting Started") {
    paparazzi.snapshot {
      PreviewMoneyHomeGettingStarted()
    }
  }

  test("Money Home Card Device Update") {
    paparazzi.snapshot {
      PreviewMoneyHomeCardDeviceUpdate()
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

  test("Cloud Backup Health Card") {
    paparazzi.snapshot {
      PreviewCloudBackupHealthCard()
    }
  }
})
