@file:Suppress("TestFunctionName")

package build.wallet.ui.app.send

import androidx.compose.runtime.Composable
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.partnerships.PartnerId
import build.wallet.partnerships.PartnerInfo
import build.wallet.statemachine.send.TransactionDetailModelType
import build.wallet.statemachine.send.TransactionDetailsModel
import build.wallet.statemachine.send.TransferConfirmationScreenModel
import build.wallet.statemachine.send.TransferConfirmationScreenVariant
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class TransferConfirmationScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("transfer confirmation screen - required hardware, confirmation enabled") {
    paparazzi.snapshot {
      TransferConfirmationScreen(
        variant = TransferConfirmationScreenVariant.Regular,
        requiresHardware = true,
        confirmButtonEnabled = true,
        requiresHardwareConfirmation = false
      )
    }
  }

  test("transfer confirmation screen - hardware not required, confirmation enabled") {
    paparazzi.snapshot {
      TransferConfirmationScreen(
        variant = TransferConfirmationScreenVariant.Regular,
        requiresHardware = false,
        confirmButtonEnabled = true,
        requiresHardwareConfirmation = false
      )
    }
  }

  test("transfer confirmation screen - hardware required, confirmation disabled") {
    paparazzi.snapshot {
      TransferConfirmationScreen(
        variant = TransferConfirmationScreenVariant.Regular,
        requiresHardware = true,
        confirmButtonEnabled = false,
        requiresHardwareConfirmation = false
      )
    }
  }

  test("transfer confirmation screen - hardware not required, confirmation disabled") {
    paparazzi.snapshot {
      TransferConfirmationScreen(
        variant = TransferConfirmationScreenVariant.Regular,
        requiresHardware = false,
        confirmButtonEnabled = false,
        requiresHardwareConfirmation = false
      )
    }
  }

  test("transfer confirmation screen - pending fee bump") {
    paparazzi.snapshot {
      TransferConfirmationScreen(
        variant = TransferConfirmationScreenVariant.SpeedUp,
        requiresHardware = false,
        confirmButtonEnabled = false,
        requiresHardwareConfirmation = false
      )
    }
  }

  test("transfer confirmation screen - sell") {
    paparazzi.snapshot {
      TransferConfirmationScreen(
        variant = TransferConfirmationScreenVariant.Sell(
          PartnerInfo(
            name = "PartnerX",
            logoUrl = null,
            partnerId = PartnerId("id"),
            logoBadgedUrl = null
          )
        ),
        requiresHardware = true,
        confirmButtonEnabled = true,
        requiresHardwareConfirmation = false
      )
    }
  }

  test("transfer confirmation screen - private wallet migration") {
    paparazzi.snapshot {
      TransferConfirmationScreen(
        variant = TransferConfirmationScreenVariant.PrivateWalletMigration,
        requiresHardware = true,
        confirmButtonEnabled = true,
        requiresHardwareConfirmation = false
      )
    }
  }

  test("transfer confirmation screen - send with hardware confirmation") {
    paparazzi.snapshot {
      TransferConfirmationScreen(
        variant = TransferConfirmationScreenVariant.Regular,
        requiresHardware = true,
        confirmButtonEnabled = true,
        requiresHardwareConfirmation = true
      )
    }
  }
})

@Composable
private fun TransferConfirmationScreen(
  variant: TransferConfirmationScreenVariant,
  requiresHardware: Boolean,
  confirmButtonEnabled: Boolean,
  requiresHardwareConfirmation: Boolean,
) {
  FormScreen(
    model = TransferConfirmationScreenModel(
      variant = variant,
      onBack = {},
      recipientAddress = BitcoinAddress("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh"),
      transactionDetails = TransactionDetailsModel(
        transactionSpeedText = "~30 minutes",
        transactionDetailModelType = when (variant) {
          TransferConfirmationScreenVariant.SpeedUp ->
            TransactionDetailModelType.SpeedUp(
              transferAmountText = "$20.00",
              transferAmountSecondaryText = "0.0003 BTC",
              oldFeeAmountText = "$1.36",
              oldFeeAmountSecondaryText = "0.00002 BTC",
              feeDifferenceText = "+$1.00",
              feeDifferenceSecondaryText = "0.00001 BTC",
              totalAmountPrimaryText = "$22.36",
              totalAmountSecondaryText = "0.0010 BTC",
              totalFeeText = "$2.36",
              totalFeeSecondaryText = "0.00003 BTC"
            )
          TransferConfirmationScreenVariant.Regular,
          TransferConfirmationScreenVariant.PrivateWalletMigration,
          ->
            TransactionDetailModelType.Regular(
              transferAmountText = "$20.00",
              transferAmountSecondaryText = "0.0003 BTC",
              feeAmountText = "$1.36",
              feeAmountSecondaryText = "0.00002 BTC",
              totalAmountPrimaryText = "$21.36",
              totalAmountSecondaryText = "0.0010 BTC"
            )
          is TransferConfirmationScreenVariant.Sell ->
            TransactionDetailModelType.Regular(
              transferAmountText = "$20.00",
              feeAmountText = "$1.36",
              feeAmountSecondaryText = "234 sats",
              totalAmountPrimaryText = "$21.36",
              totalAmountSecondaryText = "(0.0010 BTC)",
              transferAmountSecondaryText = "1,234 sats"
            )
        }
      ),
      requiresHardware = requiresHardware,
      confirmButtonEnabled = confirmButtonEnabled,
      onConfirmClick = {},
      onNetworkFeesClick = {},
      onArrivalTimeClick = when (variant) {
        TransferConfirmationScreenVariant.Regular -> { -> }
        else -> null
      },
      requiresHardwareReview = requiresHardwareConfirmation
    )
  )
}
