package build.wallet.integration.statemachine.send

import bitkey.account.HardwareType
import build.wallet.analytics.events.screen.id.MoneyHomeEventTrackerScreenId.MONEY_HOME
import build.wallet.money.BitcoinMoney.Companion.sats
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.nfc.PromptSelectionFormBodyModel
import build.wallet.statemachine.send.BitcoinRecipientAddressScreenModel
import build.wallet.statemachine.send.TransferAmountBodyModel
import build.wallet.statemachine.send.TransferConfirmationScreenModel
import build.wallet.statemachine.send.TransferInitiatedBodyModel
import build.wallet.statemachine.send.fee.FeeOptionsBodyModel
import build.wallet.statemachine.send.signtransaction.SignTransactionNfcBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.robots.*
import build.wallet.testing.ext.*
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds

/**
 * End-to-end functional tests for sending bitcoin through the full app UI flow.
 *
 * Validates complete user journey from MoneyHome through NFC signing to success screen.
 * Tests both W1 (single-tap) and W3 (two-tap with emulated prompts) hardware flows.
 */
class SendE2EFunctionalTests : FunSpec({

  /**
   * Full UI E2E test for W1 send flow.
   *
   * Navigates through complete user journey:
   * MoneyHome → Send → Recipient Entry → Amount Entry → Confirmation → NFC (single tap) → Success
   */
  testForBdk1AndBdk2(
    "W1 send through full UI flow",
    isIsolatedTest = true
  ) { app ->
    app.onboardFullAccountWithFakeHardware()

    val fundingAmount = sats(50_000L)
    val sendAmount = sats(10_000L)

    app.addSomeFunds(amount = fundingAmount)
    app.waitForFunds { it.total == fundingAmount }

    val treasuryAddress = app.treasuryWallet.getReturnAddress()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 30.seconds,
      turbineTimeout = 10.seconds
    ) {
      // Start on Money Home
      awaitUntilBody<MoneyHomeBodyModel>(MONEY_HOME) {
        clickSend()
      }

      // Enter recipient address
      awaitUntilBody<BitcoinRecipientAddressScreenModel> {
        enterAddress(treasuryAddress)
      }

      awaitUntilBody<BitcoinRecipientAddressScreenModel>(
        matching = { it.primaryButton?.isEnabled == true }
      ) {
        clickContinue()
      }

      // Enter amount
      awaitUntilBody<TransferAmountBodyModel> {
        enterBitcoinAmount(sendAmount)
      }

      awaitUntilBody<TransferAmountBodyModel>(
        matching = { it.primaryButton.isEnabled }
      ) {
        clickContinue()
      }

      // Select fee option
      awaitUntilBody<FeeOptionsBodyModel>(
        matching = { it.primaryButton.isEnabled && !it.primaryButton.isLoading }
      ) {
        clickContinue()
      }

      // Confirm transfer - triggers NFC session
      awaitUntilBody<TransferConfirmationScreenModel> {
        clickConfirm()
      }

      // W1: Single NFC tap - shows signing screen then auto-completes
      awaitUntilBody<SignTransactionNfcBodyModel>(
        matching = { it.status is SignTransactionNfcBodyModel.Status.Success }
      )

      // Success screen
      awaitUntilBody<TransferInitiatedBodyModel>()
    }

    // Verify transaction was broadcast
    app.mineBlock()
    val spendingWallet = app.getActiveWallet()
    spendingWallet.sync().shouldBeOk()
    val finalBalance = spendingWallet.balance().first()
    (finalBalance.total < fundingAmount).shouldBe(true)

    app.returnFundsToTreasury()
  }

  /**
   * Full UI E2E test for W3 send flow.
   *
   * Navigates through complete user journey with W3 two-tap flow:
   * MoneyHome → Send → Recipient → Amount → Confirmation → NFC (first tap) → NFC (second tap) → Success
   */
  testForBdk1AndBdk2(
    "W3 send through full UI flow with two-tap signing",
    isIsolatedTest = true
  ) { app ->
    // Configure for W3 hardware
    app.accountConfigService.setHardwareType(HardwareType.W3)

    app.onboardFullAccountWithFakeHardware()

    val fundingAmount = sats(50_000L)
    val sendAmount = sats(10_000L)

    app.addSomeFunds(amount = fundingAmount)
    app.waitForFunds { it.total == fundingAmount }

    val treasuryAddress = app.treasuryWallet.getReturnAddress()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 30.seconds,
      turbineTimeout = 10.seconds
    ) {
      // Start on Money Home
      awaitUntilBody<MoneyHomeBodyModel>(MONEY_HOME) {
        clickSend()
      }

      // Enter recipient address
      awaitUntilBody<BitcoinRecipientAddressScreenModel> {
        enterAddress(treasuryAddress)
      }

      awaitUntilBody<BitcoinRecipientAddressScreenModel>(
        matching = { it.primaryButton?.isEnabled == true }
      ) {
        clickContinue()
      }

      // Enter amount
      awaitUntilBody<TransferAmountBodyModel> {
        enterBitcoinAmount(sendAmount)
      }

      awaitUntilBody<TransferAmountBodyModel>(
        matching = { it.primaryButton.isEnabled }
      ) {
        clickContinue()
      }

      // Select fee option
      awaitUntilBody<FeeOptionsBodyModel>(
        matching = { it.primaryButton.isEnabled && !it.primaryButton.isLoading }
      ) {
        clickContinue()
      }

      // Confirm transfer - triggers W3 NFC session
      awaitUntilBody<TransferConfirmationScreenModel> {
        clickConfirm()
      }

      // W3 Two-Tap Flow:
      // 1. First tap shows transferring status
      awaitUntilBody<SignTransactionNfcBodyModel>(
        matching = { it.status is SignTransactionNfcBodyModel.Status.Transferring }
      )

      // 2. Prompt selection (APPROVE/DENY) appears after transfer
      awaitUntilBody<PromptSelectionFormBodyModel> {
        clickApprove()
      }

      // 3. Second tap starts - shows searching/in-progress
      awaitUntilBody<SignTransactionNfcBodyModel>(
        matching = { it.status is SignTransactionNfcBodyModel.Status.Searching }
      )

      // 4. Success after second tap completes
      awaitUntilBody<SignTransactionNfcBodyModel>(
        matching = { it.status is SignTransactionNfcBodyModel.Status.Success }
      )

      // Success screen
      awaitUntilBody<TransferInitiatedBodyModel>()
    }

    // Verify transaction was broadcast
    app.mineBlock()
    val spendingWallet = app.getActiveWallet()
    spendingWallet.sync().shouldBeOk()
    val finalBalance = spendingWallet.balance().first()
    (finalBalance.total < fundingAmount).shouldBe(true)

    app.returnFundsToTreasury()
  }
})
