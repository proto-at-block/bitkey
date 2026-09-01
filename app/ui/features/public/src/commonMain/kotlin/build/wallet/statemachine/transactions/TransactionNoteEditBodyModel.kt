package build.wallet.statemachine.transactions

import androidx.compose.ui.text.AnnotatedString
import build.wallet.analytics.events.screen.id.MoneyHomeEventTrackerScreenId.TRANSACTION_NOTE_EDIT
import build.wallet.bitcoin.metadata.TransactionNote
import build.wallet.compose.collections.immutableListOfNotNull
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.input.TextFieldModel.Capitalization
import dev.zacsweers.redacted.annotations.Redacted

data class TransactionNoteEditBodyModel(
  @Redacted
  val note: String,
  val existingNote: TransactionNote?,
  val isSaving: Boolean,
  val errorText: String? = null,
  val onNoteChange: (String) -> Unit,
  val onSave: () -> Unit,
  val onDelete: () -> Unit,
  val onClose: () -> Unit,
) : FormBodyModel(
    id = TRANSACTION_NOTE_EDIT,
    onBack = onClose,
    toolbar = null,
    header = FormHeaderModel(
      headline = if (existingNote != null) "Edit note" else "Add a note",
      subline = "Add a note to help you keep track of what this payment was for"
    ),
    mainContentList = immutableListOfNotNull(
      FormMainContentModel.TextArea(
        fieldModel = TextFieldModel(
          value = note,
          placeholderText = "Add a note",
          onValueChange = { newValue, _ -> onNoteChange(newValue) },
          keyboardType = TextFieldModel.KeyboardType.Default,
          enableAutoCorrect = true,
          capitalization = Capitalization.Sentences,
          maxLength = TransactionNote.MAX_NOTE_LENGTH,
          testTag = "transaction-note-input"
        )
      ),
      errorText?.let {
        FormMainContentModel.AnnotatedText(
          text = AnnotatedString(it),
          treatment = LabelTreatment.Destructive
        )
      }
    ),
    primaryButton = ButtonModel(
      text = "Save",
      size = ButtonModel.Size.Footer,
      isLoading = isSaving,
      isEnabled = note.trim().isNotBlank() && !isSaving,
      onClick = StandardClick(onSave)
    ),
    secondaryButton = existingNote?.let {
      ButtonModel(
        text = "Delete note",
        size = ButtonModel.Size.Footer,
        treatment = ButtonModel.Treatment.SecondaryDestructive,
        isEnabled = !isSaving,
        onClick = StandardClick(onDelete)
      )
    },
    renderContext = RenderContext.Sheet
  )
