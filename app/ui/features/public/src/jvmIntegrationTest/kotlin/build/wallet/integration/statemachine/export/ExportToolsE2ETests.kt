package build.wallet.integration.statemachine.export

import build.wallet.analytics.events.screen.id.MoneyHomeEventTrackerScreenId.MONEY_HOME
import build.wallet.analytics.events.screen.id.SettingsEventTrackerScreenId.SETTINGS
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.export.ExportToolsSelectionModel
import build.wallet.statemachine.export.view.ExportSheetBodyModel
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.settings.SettingsBodyModel
import build.wallet.statemachine.ui.awaitSheet
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.robots.clickExportTools
import build.wallet.statemachine.ui.robots.clickSettings
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import io.kotest.core.spec.style.FunSpec

class ExportToolsE2ETests : FunSpec({

  test("e2e – export descriptor") {
    val app = launchNewApp()
    app.onboardFullAccountWithFakeHardware()

    app.appUiStateMachine.testWithVirtualTime(Unit) {
      awaitUntilBody<MoneyHomeBodyModel>(MONEY_HOME) {
        clickSettings()
      }

      awaitUntilBody<SettingsBodyModel>(SETTINGS) {
        clickExportTools()
      }

      awaitUntilBody<ExportToolsSelectionModel> {
        onExportDescriptorClick()
      }

      awaitSheet<ExportSheetBodyModel> {
        clickPrimaryButton()
      }

      // Assert sheet is closed.
      awaitUntil { it.bottomSheetModel != null }
    }
  }
})
