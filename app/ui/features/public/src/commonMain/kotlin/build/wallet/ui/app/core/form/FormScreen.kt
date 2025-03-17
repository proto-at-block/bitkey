package build.wallet.ui.app.core.form

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.statemachine.core.form.RenderContext.Screen
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
  renderContext: RenderContext = Screen,
  horizontalPadding: Int = 20,
  headerToMainContentSpacing: Int = 16,
  background: Color = WalletTheme.colors.background,
  toolbarContent: @Composable (() -> Unit)? = null,
  headerContent: @Composable (() -> Unit)? = null,
  mainContent: @Composable (ColumnScope.() -> Unit)? = null,
  footerContent: @Composable (ColumnScope.() -> Unit)? = null,
) {
  val isFullScreen = renderContext == Screen
  onBack?.let {
    BackHandler(onBack = onBack)
  }

  Column(
    modifier =
      modifier
        .background(background)
        .imePadding()
        .thenIf(isFullScreen) {
          Modifier.fillMaxSize()
        }
  ) {
    val contentShadowHeight = 12.dp
    Box(
      modifier = Modifier
        .thenIf(isFullScreen) { Modifier.weight(1F) }
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .thenIf(isFullScreen) { Modifier.matchParentSize() }
          .background(background)
          .verticalScroll(rememberScrollState())
          .padding(bottom = contentShadowHeight)
          .padding(horizontal = horizontalPadding.dp)
      ) {
        when (toolbarContent) {
          null -> EmptyToolbar()
          else -> {
            toolbarContent()
          }
        }
        headerContent?.let {
          headerContent()
        }
        Spacer(Modifier.height(headerToMainContentSpacing.dp))
        mainContent?.let {
          mainContent()
        }
      }
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(contentShadowHeight)
            .align(Alignment.BottomCenter)
            .background(
              brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, background)
              )
            )
      ) {}
    }
    footerContent?.let {
      Column(
        modifier =
          Modifier
            .background(background)
            .padding(top = 12.dp, bottom = 28.dp)
            .padding(horizontal = horizontalPadding.dp)
      ) {
        footerContent()
      }
    }
  }
}
