package build.wallet.ui.tooling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import org.jetbrains.compose.resources.ExperimentalResourceApi

@OptIn(ExperimentalResourceApi::class)
@Composable
actual fun PreviewContextConfigurationEffect() {
  CompositionLocalProvider(
    LocalInspectionMode provides true
  ) {
    org.jetbrains.compose.resources.PreviewContextConfigurationEffect()
  }
}
