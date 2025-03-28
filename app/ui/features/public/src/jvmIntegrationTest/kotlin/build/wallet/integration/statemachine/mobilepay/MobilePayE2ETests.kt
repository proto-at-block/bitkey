package build.wallet.integration.statemachine.mobilepay

import build.wallet.amount.KeypadButton
import build.wallet.analytics.events.screen.id.MobilePayEventTrackerScreenId
import build.wallet.analytics.events.screen.id.MoneyHomeEventTrackerScreenId.MONEY_HOME
import build.wallet.analytics.events.screen.id.SettingsEventTrackerScreenId.SETTINGS
import build.wallet.money.FiatMoney
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.limit.picker.EntryMode.Keypad
import build.wallet.statemachine.limit.picker.SpendingLimitPickerModel
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.nfc.NfcBodyModel
import build.wallet.statemachine.settings.SettingsBodyModel
import build.wallet.statemachine.settings.full.mobilepay.MobilePayStatusModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.matchers.shouldBeDisabled
import build.wallet.statemachine.ui.matchers.shouldBeEnabled
import build.wallet.statemachine.ui.robots.*
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.setupMobilePay
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class MobilePayE2ETests : FunSpec({
  context("keypad-based mobile pay amount entry") {
    test("set mobile pay") {
      val app = launchNewApp()
      app.onboardFullAccountWithFakeHardware()

      app.appUiStateMachine.test(Unit) {
        awaitUntilBody<MoneyHomeBodyModel>(MONEY_HOME) {
          clickSettings()
        }

        awaitUntilBody<SettingsBodyModel>(SETTINGS) {
          clickTransferSettings()
        }

        awaitUntilBody<MobilePayStatusModel> {
          switchCardModel.switchModel.shouldBeDisabled()
          switchCardModel.switchModel.enable()
        }

        awaitUntilBody<SpendingLimitPickerModel> {
          setLimitButtonModel.shouldBeDisabled()
          with(entryMode.shouldBeTypeOf<Keypad>()) {
            keypadModel.onButtonPress(KeypadButton.Digit.One)
            keypadModel.onButtonPress(KeypadButton.Digit.Zero)
            keypadModel.onButtonPress(KeypadButton.Digit.Zero)
            keypadModel.onButtonPress(KeypadButton.Digit.Zero)
            keypadModel.onButtonPress(KeypadButton.Digit.Zero)
            keypadModel.onButtonPress(KeypadButton.Digit.Zero)
          }
        }
        awaitUntilBody<SpendingLimitPickerModel>(
          matching = {
            (it.entryMode as Keypad).amountModel.primaryAmount == "$100,000"
          }
        ) {
          setLimitButtonModel.shouldBeEnabled()
          setLimitButtonModel.onClick()
        }

        awaitUntilBody<NfcBodyModel>()

        awaitUntilBody<LoadingSuccessBodyModel>(
          MobilePayEventTrackerScreenId.MOBILE_PAY_LIMIT_UPDATE_LOADING
        )

        awaitUntilBody<FormBodyModel>(
          MobilePayEventTrackerScreenId.MOBILE_PAY_LIMIT_UPDATE_SUCCESS
        ) {
          primaryButton.click()
        }

        awaitUntilBody<MobilePayStatusModel>(
          matching = {
            it.switchCardModel.actionRows.firstOrNull()?.sideText == "$100,000.00"
          }
        )
      }
    }

    test("entry should be pre-populated") {
      val app = launchNewApp()
      app.onboardFullAccountWithFakeHardware()
      app.setupMobilePay(limit = FiatMoney.usd(100.0))

      app.appUiStateMachine.test(Unit) {
        awaitUntilBody<MoneyHomeBodyModel>(MONEY_HOME) {
          clickSettings()
        }

        awaitUntilBody<SettingsBodyModel>(SETTINGS) {
          clickTransferSettings()
        }

        awaitUntilBody<MobilePayStatusModel> {
          val dailyLimitActionRow = switchCardModel.actionRows.first()

          dailyLimitActionRow.title.shouldBe("Daily limit")
          dailyLimitActionRow.sideText.shouldBe("$100.00")

          dailyLimitActionRow.onClick()
        }

        awaitUntilBody<SpendingLimitPickerModel>(
          matching = {
            (it.entryMode as Keypad).amountModel.primaryAmount == "$100"
          }
        ) {
          setLimitButtonModel.shouldBeEnabled()
        }

        cancelAndIgnoreRemainingEvents()
      }
    }
  }
})
