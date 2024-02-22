@file:OptIn(ExperimentalMaterial3Api::class)

package build.wallet.ui.components.sheet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.SheetSize
import build.wallet.statemachine.core.SheetSize.MIN40
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.UiModelContent
import build.wallet.ui.theme.WalletTheme
import kotlinx.coroutines.launch
import androidx.compose.material3.ModalBottomSheet as MaterialBottomSheet

@Composable
fun Sheet(
  modifier: Modifier = Modifier,
  model: SheetModel,
) {
  val sheetState =
    rememberModalBottomSheetState(
      skipPartiallyExpanded = true
    )

  CompositionLocalProvider(
    LocalSheetCloser provides { sheetState.hide() }
  ) {
    Box {
      SheetLayout(
        modifier = modifier.fillMaxWidth(),
        size = model.size,
        dragIndicatorVisible = model.dragIndicatorVisible,
        onClosed = model.onClosed,
        sheetState = sheetState,
        sheetContent = {
          Column(
            modifier = Modifier.fillMaxWidth()
          ) {
            UiModelContent(model.body)
          }
        }
      )
    }
  }
}

/**
 * A [CompositionLocalProvider] that provides a [SheetCloser] to be used by the sheet content. Defaults
 * to a no-op implementation when not defined.
 */
val LocalSheetCloser = staticCompositionLocalOf { SheetCloser { } }

/**
 * An interface which defines an action to close the sheet.
 */
fun interface SheetCloser {
  suspend operator fun invoke()
}

@Composable
private fun SheetLayout(
  modifier: Modifier = Modifier,
  size: SheetSize,
  dragIndicatorVisible: Boolean,
  sheetState: SheetState,
  onClosed: () -> Unit,
  sheetContent: @Composable ColumnScope.() -> Unit,
) {
  // Scope used to request showing and hiding the sheet.
  val coroutineScope = rememberStableCoroutineScope()

  // Close the sheet and update the state on back click, if the sheet is open.
  if (sheetState.isVisible) {
    BackHandler {
      coroutineScope.launch {
        sheetState.hide()
      }
    }
  }

  val sheetShape =
    RoundedCornerShape(
      topStart = sheetCornerRadius,
      topEnd = sheetCornerRadius
    )

  MaterialBottomSheet(
    containerColor = WalletTheme.colors.background,
    onDismissRequest = {
      onClosed()
    },
    modifier =
      modifier
        .thenIf(size == MIN40) {
          modifier.heightIn(min = (LocalConfiguration.current.screenHeightDp * 0.4f).dp)
        },
    sheetState = sheetState,
    shape = sheetShape,
    dragHandle =
      if (dragIndicatorVisible) {
        {
          BottomSheetDefaults.DragHandle()
        }
      } else {
        null
      },
    tonalElevation = sheetElevation,
    content = sheetContent,
    scrimColor = WalletTheme.colors.mask,
    windowInsets = WindowInsets.navigationBars
  )
}

private val sheetCornerRadius = 24.dp
private val sheetElevation = 24.dp
