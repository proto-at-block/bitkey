package build.wallet.statemachine.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.platform.sharing.SharingManager
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.export.ExportToolsUiStateMachineImpl.SheetState.TransactionHistory
import build.wallet.statemachine.export.ExportToolsUiStateMachineImpl.SheetState.WalletDescriptor
import build.wallet.statemachine.export.view.ExportTransactionHistorySheetModel
import build.wallet.statemachine.export.view.ExportWalletDescriptorSheetModel

class ExportToolsUiStateMachineImpl(
  private val sharingManager: SharingManager,
) : ExportToolsUiStateMachine {
  @Composable
  override fun model(props: ExportToolsUiProps): ScreenModel {
    var sheetState: SheetState? by remember { mutableStateOf(null) }

    return ScreenModel(
      body = exportToolsSelectionModel(
        onBack = props.onBack,
        onExportDescriptorClick = { sheetState = WalletDescriptor },
        onExportTransactionHistoryClick = { sheetState = TransactionHistory }
      ),
      bottomSheetModel = when (sheetState) {
        TransactionHistory -> ExportTransactionHistorySheetModel(
          onClosed = {
            sheetState = null
          },
          onCtaClicked = {
            sharingManager.shareText("abcdef", "Export transaction history", completion = {
              sheetState = null
            })
          }
        )
        WalletDescriptor -> ExportWalletDescriptorSheetModel(
          onClosed = {
            sheetState = null
          },
          onCtaClicked = {
            sharingManager.shareText("abcdef", "Export wallet descriptor", completion = {
              sheetState = null
            })
          }
        )
        null -> null
      }
    )
  }

  private sealed interface SheetState {
    data object TransactionHistory : SheetState

    data object WalletDescriptor : SheetState
  }
}
