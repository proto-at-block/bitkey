package build.wallet.statemachine.ui.robots

import build.wallet.amount.KeypadButton
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.money.BitcoinMoney
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.moneyhome.MoneyHomeButtonsModel
import build.wallet.statemachine.send.BitcoinRecipientAddressScreenModel
import build.wallet.statemachine.send.TransferAmountBodyModel
import build.wallet.statemachine.send.TransferConfirmationScreenModel
import build.wallet.statemachine.send.fee.FeeOptionsBodyModel
import build.wallet.statemachine.ui.matchers.shouldBeEnabled
import build.wallet.statemachine.ui.matchers.shouldNotBeLoading
import io.kotest.matchers.nulls.shouldNotBeNull

// ========================================================================
// MoneyHome Send Button
// ========================================================================

/**
 * Clicks the "Send" button on Money Home.
 */
fun MoneyHomeBodyModel.clickSend() {
  val moneyHomeButtonsModel = buttonsModel
    .shouldNotBeNull()

  when (moneyHomeButtonsModel) {
    is MoneyHomeButtonsModel.MoneyMovementButtonsModel -> {
      val sendButton = moneyHomeButtonsModel.buttons.first { it.iconModel.text == "Send" }
      require(sendButton.enabled) {
        "Send button is disabled, cannot click. This may indicate limited functionality or another restriction."
      }
      sendButton.onClick()
    }
    else -> error("Expected MoneyMovementButtonsModel, got: ${moneyHomeButtonsModel::class.simpleName}")
  }
}

// ========================================================================
// Recipient Address Entry
// ========================================================================

/**
 * Enters a bitcoin address on the recipient entry screen.
 */
fun BitcoinRecipientAddressScreenModel.enterAddress(address: BitcoinAddress) {
  val input = mainContentList.filterIsInstance<FormMainContentModel.AddressInput>().first()
  input.fieldModel.onValueChange(address.address, address.address.length..address.address.length)
}

/**
 * Clicks the continue button on the recipient entry screen.
 */
fun BitcoinRecipientAddressScreenModel.clickContinue() {
  primaryButton
    .shouldNotBeNull()
    .shouldBeEnabled()
    .shouldNotBeLoading()
    .onClick()
}

// ========================================================================
// Amount Entry
// ========================================================================

/**
 * Enters a bitcoin amount via the keypad.
 */
fun TransferAmountBodyModel.enterBitcoinAmount(amount: BitcoinMoney) {
  val satsValue = amount.fractionalUnitValue.longValue().toString()
  satsValue.forEach { char ->
    val button = when (char) {
      '0' -> KeypadButton.Digit.Zero
      '1' -> KeypadButton.Digit.One
      '2' -> KeypadButton.Digit.Two
      '3' -> KeypadButton.Digit.Three
      '4' -> KeypadButton.Digit.Four
      '5' -> KeypadButton.Digit.Five
      '6' -> KeypadButton.Digit.Six
      '7' -> KeypadButton.Digit.Seven
      '8' -> KeypadButton.Digit.Eight
      '9' -> KeypadButton.Digit.Nine
      else -> error("Unexpected character: $char")
    }
    keypadModel.onButtonPress(button)
  }
}

/**
 * Clicks the continue button on the amount entry screen.
 */
fun TransferAmountBodyModel.clickContinue() {
  primaryButton
    .shouldBeEnabled()
    .shouldNotBeLoading()
    .onClick()
}

// ========================================================================
// Fee Selection
// ========================================================================

/**
 * Clicks the continue button on the fee selection screen.
 * Uses the default selected fee option.
 */
fun FeeOptionsBodyModel.clickContinue() {
  primaryButton
    .shouldBeEnabled()
    .shouldNotBeLoading()
    .onClick()
}

// ========================================================================
// Transfer Confirmation
// ========================================================================

/**
 * Clicks the confirm button on the transfer confirmation screen.
 * This will trigger the NFC session for hardware signing.
 */
fun TransferConfirmationScreenModel.clickConfirm() {
  primaryButton
    .shouldBeEnabled()
    .shouldNotBeLoading()
    .onClick()
}
