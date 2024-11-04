package build.wallet.ui.app.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import build.wallet.statemachine.core.InAppBrowserModel

/**
 * Used to launch an in-app browser
 */
@Composable
fun InAppBrowserScreen(model: InAppBrowserModel) {
  LaunchedEffect("open-in-app-browser") {
    model.open()
  }
}
