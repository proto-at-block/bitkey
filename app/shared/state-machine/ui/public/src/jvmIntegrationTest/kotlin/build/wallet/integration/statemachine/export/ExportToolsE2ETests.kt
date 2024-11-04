package build.wallet.integration.statemachine.export

import build.wallet.analytics.events.screen.id.MoneyHomeEventTrackerScreenId.MONEY_HOME
import build.wallet.analytics.events.screen.id.SettingsEventTrackerScreenId.SETTINGS
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.feature.setFlagValue
import build.wallet.statemachine.core.test
import build.wallet.statemachine.export.ExportToolsSelectionModel
import build.wallet.statemachine.export.view.ExportSheetBodyModel
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.settings.SettingsBodyModel
import build.wallet.statemachine.ui.awaitScreenWithSheetModelBody
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.robots.clickExportTools
import build.wallet.statemachine.ui.robots.clickSettings
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.launch

class ExportToolsE2ETests : FunSpec({
  lateinit var appTester: AppTester

  beforeTest {
    appTester = launchNewApp()
  }

  test("e2e – export descriptor") {
    launch {
      appTester.app.appComponent.appWorkerExecutor.executeAll()
    }
    appTester.onboardFullAccountWithFakeHardware()
    appTester.app.appComponent.exportToolsFeatureFlag.setFlagValue(true)

    appTester.app.appUiStateMachine.test(Unit) {
      awaitUntilScreenWithBody<MoneyHomeBodyModel>(MONEY_HOME) {
        clickSettings()
      }

      awaitUntilScreenWithBody<SettingsBodyModel>(SETTINGS) {
        clickExportTools()
      }

      awaitUntilScreenWithBody<ExportToolsSelectionModel> {
        onExportDescriptorClick()
      }

      awaitScreenWithSheetModelBody<ExportSheetBodyModel> {
        clickPrimaryButton()
      }

      // Assert sheet is closed.
      awaitUntil { it.bottomSheetModel != null }
    }
  }
})
