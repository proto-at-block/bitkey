package build.wallet.ui.components.sheet

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.SheetSize
import build.wallet.statemachine.core.SheetSize.MIN40
import build.wallet.statemachine.core.SheetTreatment
import build.wallet.ui.compose.getScreenSize
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.render
import build.wallet.ui.system.BackHandler
import build.wallet.ui.theme.WalletTheme
import kotlinx.coroutines.launch
import androidx.compose.material3.ModalBottomSheet as MaterialBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(
  modifier: Modifier = Modifier,
  model: SheetModel,
  sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
  Sheet(
    modifier = modifier,
    treatment = model.treatment,
    size = model.size,
    sheetState = sheetState,
    onClosed = model.onClosed,
    content = {
      model.body.render()
    }
  )
}

@Composable
fun Sheet(
  modifier: Modifier = Modifier,
  treatment: SheetTreatment = SheetTreatment.STANDARD,
  size: SheetSize = SheetSize.DEFAULT,
  sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  onClosed: () -> Unit,
  content: @Composable () -> Unit,
) {
  CompositionLocalProvider(
    LocalSheetCloser provides { sheetState.hide() }
  ) {
    SheetLayout(
      modifier = modifier,
      containerColor = when (treatment) {
        SheetTreatment.STANDARD -> WalletTheme.colors.background
        SheetTreatment.INHERITANCE -> WalletTheme.colors.inheritanceSurface
      },
      size = size,
      onClosed = onClosed,
      sheetState = sheetState,
      sheetContent = content
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun SheetLayout(
  modifier: Modifier = Modifier,
  containerColor: Color = WalletTheme.colors.background,
  size: SheetSize,
  sheetState: SheetState,
  onClosed: () -> Unit,
  sheetContent: @Composable () -> Unit,
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

  val sheetShape = RoundedCornerShape(
    topStart = sheetCornerRadius,
    topEnd = sheetCornerRadius
  )

  MaterialBottomSheet(
    containerColor = containerColor,
    onDismissRequest = {
      onClosed()
    },
    modifier = modifier
      .fillMaxWidth()
      .thenIf(size == MIN40) {
        modifier.heightIn(min = getScreenSize().height * 0.4f)
      },
    sheetState = sheetState,
    shape = sheetShape,
    dragHandle = {
      DragHandle(Modifier.padding(top = 12.dp, bottom = 16.dp))
    },
    tonalElevation = sheetElevation,
    content = {
      Column(
        modifier = Modifier
          .animateContentSize(
            alignment = Alignment.Center,
            animationSpec = spring(
              stiffness = Spring.StiffnessMedium,
              visibilityThreshold = IntSize.VisibilityThreshold
            )
          )
      ) {
        sheetContent()
      }
    },
    scrimColor = WalletTheme.colors.mask,
    contentWindowInsets = { WindowInsets.navigationBars }
  )
}

private val sheetCornerRadius = 32.dp
private val sheetElevation = 24.dp

/**
 * Material's drag handle uses different colors, handle shape and padding, so we implement our own.
 */
@Composable
private fun DragHandle(modifier: Modifier = Modifier) {
  Box(
    modifier
      .background(
        color = WalletTheme.colors.foreground30,
        shape = RoundedCornerShape(32.dp)
      )
      .height(4.dp)
      .width(32.dp)
  )
}
