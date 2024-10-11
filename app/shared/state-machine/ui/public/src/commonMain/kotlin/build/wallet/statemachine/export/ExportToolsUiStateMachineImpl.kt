package build.wallet.statemachine.export

import androidx.compose.runtime.*
import build.wallet.bitcoin.export.ExportTransactionsService
import build.wallet.bitcoin.export.ExportWatchingDescriptorService
import build.wallet.logging.logFailure
import build.wallet.platform.data.MimeType
import build.wallet.platform.sharing.SharingManager
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.export.ExportToolsUiStateMachineImpl.SheetState.*
import build.wallet.statemachine.export.view.*
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

class ExportToolsUiStateMachineImpl(
  private val sharingManager: SharingManager,
  private val exportWatchingDescriptorService: ExportWatchingDescriptorService,
  private val exportTransactionsService: ExportTransactionsService,
) : ExportToolsUiStateMachine {
  @Composable
  override fun model(props: ExportToolsUiProps): ScreenModel {
    var sheetState: SheetState? by remember { mutableStateOf(null) }

    return ScreenModel(
      body = ExportToolsSelectionModel(
        onBack = props.onBack,
        onExportDescriptorClick = { sheetState = WalletDescriptor },
        onExportTransactionHistoryClick = { sheetState = TransactionHistory }
      ),
      bottomSheetModel = when (sheetState) {
        TransactionHistoryLoading -> {
          LaunchedEffect("loading-transaction-history") {
            exportTransactionsService.export()
              .logFailure { "Error exporting transaction history" }
              .onSuccess {
                sheetState = TransactionHistory
                sharingManager.shareData(
                  data = it.data,
                  mimeType = MimeType.CSV,
                  title = "Bitkey Transaction History",
                  completion = { sheetState = null }
                )
              }
              .onFailure { sheetState = EncounteredError }
          }

          exportTransactionHistoryLoadingSheetModel(
            onClosed = { sheetState = EncounteredError }
          )
        }
        TransactionHistory -> exportTransactionHistorySheetModel(
          onClosed = { sheetState = null },
          onCtaClicked = { sheetState = TransactionHistoryLoading }
        )
        WalletDescriptorLoading -> {
          LaunchedEffect("load-descriptor") {
            exportWatchingDescriptorService.formattedActiveWalletDescriptorString()
              .logFailure { "Error exporting active wallet descriptor" }
              .onSuccess {
                sheetState = WalletDescriptor
                sharingManager.shareText(
                  text = it,
                  title = "Export wallet descriptor",
                  completion = { sheetState = null }
                )
              }
              .onFailure { sheetState = EncounteredError }
          }

          exportWalletDescriptorLoadingSheetModel(
            onClosed = { sheetState = null }
          )
        }
        WalletDescriptor -> exportWalletDescriptorSheetModel(
          onClosed = { sheetState = null },
          onClick = { sheetState = WalletDescriptorLoading }
        )
        EncounteredError -> encounteredErrorSheetModel(
          onClosed = { sheetState = null }
        )
        null -> null
      }
    )
  }

  /**
   * Different possible bottom sheets to display over the export screen
   */
  private sealed interface SheetState {
    /**
     * Bottom sheet while we load the customer's transaction history.
     */
    data object TransactionHistoryLoading : SheetState

    /**
     * Bottom sheet for exporting transaction history.
     */
    data object TransactionHistory : SheetState

    /**
     * Bottom sheet for exporting wallet descriptors.
     */
    data object WalletDescriptor : SheetState

    /**
     * Bottom sheet while we load the wallet descriptor.
     */
    data object WalletDescriptorLoading : SheetState

    /**
     * Bottom sheet for error.
     */
    data object EncounteredError : SheetState
  }
}
