package build.wallet.statemachine.transactions

import androidx.compose.runtime.*
import build.wallet.bitcoin.metadata.TransactionNoteService
import build.wallet.bitcoin.metadata.TransactionNoteServiceError
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logError
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.SheetSize
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.launch

@BitkeyInject(ActivityScope::class)
class TransactionNoteEditUiStateMachineImpl(
  private val transactionNoteService: TransactionNoteService,
) : TransactionNoteEditUiStateMachine {
  @Composable
  override fun model(props: TransactionNoteEditUiProps): SheetModel {
    val coroutineScope = rememberStableCoroutineScope()
    var noteText by remember(props.existingNote) {
      mutableStateOf(props.existingNote?.note.orEmpty())
    }
    var isSaving by remember { mutableStateOf(false) }
    var errorText: String? by remember { mutableStateOf(null) }

    return SheetModel(
      size = SheetSize.DEFAULT,
      onClosed = props.onClose,
      body = TransactionNoteEditBodyModel(
        note = noteText,
        existingNote = props.existingNote,
        isSaving = isSaving,
        errorText = errorText,
        onNoteChange = {
          noteText = it
          errorText = null
        },
        onClose = props.onClose,
        onSave = {
          isSaving = true
          errorText = null
          coroutineScope.launch {
            transactionNoteService
              .createOrUpdateNote(
                transactionId = props.transactionId,
                note = noteText
              )
              .onSuccess { props.onClose() }
              .onFailure { error ->
                isSaving = false
                errorText = when (error) {
                  // Validation failures are user-actionable — surface the domain
                  // message directly and don't log at error level.
                  is TransactionNoteServiceError.InvalidNote -> error.message
                  else -> {
                    logError(throwable = error) { "Failed to save transaction note" }
                    "Couldn't save note. Please try again."
                  }
                }
              }
          }
        },
        onDelete = {
          isSaving = true
          errorText = null
          coroutineScope.launch {
            transactionNoteService
              .deleteNote(transactionId = props.transactionId)
              .onSuccess { props.onClose() }
              .onFailure { error ->
                logError(throwable = error) { "Failed to delete transaction note" }
                isSaving = false
                errorText = "Couldn't delete note. Please try again."
              }
          }
        }
      )
    )
  }
}
