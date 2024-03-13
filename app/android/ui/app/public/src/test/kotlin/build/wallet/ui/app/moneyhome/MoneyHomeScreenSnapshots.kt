package build.wallet.ui.app.moneyhome

import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.ProtectedCustomerAlias
import build.wallet.compose.collections.immutableListOf
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.moneyhome.MoneyHomeButtonsModel
import build.wallet.statemachine.moneyhome.lite.LiteMoneyHomeBodyModel
import io.kotest.core.spec.style.FunSpec

class MoneyHomeScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("moneyhome_screen_full") {
    paparazzi.snapshot {
      MoneyHomeScreenFull()
    }
  }

  test("MoneyHome Screen Lite with protecting wallets") {
    paparazzi.snapshot {
      LiteMoneyHomeScreen(
        model =
          LiteMoneyHomeBodyModel(
            onSettings = {},
            buttonModel = MoneyHomeButtonsModel.SingleButtonModel(onSetUpBitkeyDevice = { }),
            protectedCustomers = immutableListOf(
              ProtectedCustomer("", ProtectedCustomerAlias("Alice"))
            ),
            onProtectedCustomerClick = {},
            onAcceptInviteClick = {},
            onBuyOwnBitkeyClick = {}
          )
      )
    }
  }

  test("MoneyHome Screen Lite without protecting wallets") {
    paparazzi.snapshot {
      LiteMoneyHomeScreen(
        model =
          LiteMoneyHomeBodyModel(
            onSettings = {},
            buttonModel = MoneyHomeButtonsModel.SingleButtonModel(onSetUpBitkeyDevice = { }),
            protectedCustomers = immutableListOf(),
            onProtectedCustomerClick = {},
            onAcceptInviteClick = {},
            onBuyOwnBitkeyClick = {}
          )
      )
    }
  }
})
