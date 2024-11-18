package build.wallet.ui.app.core.form

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import build.wallet.ui.components.toolbar.EmptyToolbar
import build.wallet.ui.compose.thenIf
import build.wallet.ui.system.BackHandler
import build.wallet.ui.theme.WalletTheme

/**
 * A slot-based screen for rendering form views.
 *
 * https://www.figma.com/file/aaOrQTgHXp2NpOYCBDoe5E/Wallet-System?node-id=3477%3A4033&t=JWWhnI4XJy2RNvVd-1.
 */
@Composable
fun FormScreen(
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)?,
  fullHeight: Boolean = true,
  horizontalPadding: Int = 20,
  headerToMainContentSpacing: Int = 16,
  background: Color = WalletTheme.colors.background,
  toolbarContent: @Composable (() -> Unit)? = null,
  headerContent: @Composable (() -> Unit)? = null,
  mainContent: @Composable (ColumnScope.() -> Unit)? = null,
  footerContent: @Composable (ColumnScope.() -> Unit)? = null,
) {
  onBack?.let {
    BackHandler(onBack = onBack)
  }

  Column(
    modifier =
      modifier
        .background(background)
        .padding(horizontal = horizontalPadding.dp)
        .imePadding()
        .thenIf(fullHeight) {
          Modifier.fillMaxSize()
        },
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    when (toolbarContent) {
      null -> EmptyToolbar()
      else -> {
        if (!fullHeight) {
          Spacer(Modifier.height(16.dp))
        }
        toolbarContent()
      }
    }
    headerContent?.let {
      headerContent()
    }
    Spacer(Modifier.height(headerToMainContentSpacing.dp))
    Column(
      modifier =
        Modifier
          .verticalScroll(rememberScrollState())
          .thenIf(fullHeight) {
            Modifier.weight(1F)
          }
    ) {
      mainContent?.let {
        mainContent()
      }
    }
    footerContent?.let {
      Spacer(Modifier.height(24.dp))
      Column(
        modifier =
          Modifier.background(
            Brush.verticalGradient(
              0f to Color.hsv(360f, 0f, 1f),
              0.15f to WalletTheme.colors.background
            )
          )
      ) {
        footerContent()
      }
    }
    Spacer(Modifier.height(28.dp))
  }
}
