package build.wallet.statemachine.settings.full.feedback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.bitkey.f8e.AccountId
import build.wallet.compose.collections.buildImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.email.Email
import build.wallet.f8e.F8eEnvironment
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SuccessBodyModel
import build.wallet.statemachine.core.SystemUIModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.support.ConditionEvaluationResult
import build.wallet.support.MutableSupportTicketData
import build.wallet.support.OptimizedSupportTicketFieldConditions
import build.wallet.support.SupportTicketAttachment
import build.wallet.support.SupportTicketData
import build.wallet.support.SupportTicketField
import build.wallet.support.SupportTicketForm
import build.wallet.support.SupportTicketFormValidator
import build.wallet.support.SupportTicketRepository
import build.wallet.time.DateTimeFormatter
import build.wallet.time.Delayer
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.picker.ItemPickerModel
import build.wallet.ui.model.switch.SwitchModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.seconds

class FeedbackFormUiStateMachineImpl(
  private val delayer: Delayer,
  private val supportTicketRepository: SupportTicketRepository,
  private val supportTicketFormValidator: SupportTicketFormValidator,
  private val dateTimeFormatter: DateTimeFormatter,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
) : FeedbackFormUiStateMachine {
  @Composable
  override fun model(props: FeedbackFormUiProps): ScreenModel {
    var uiState: FeedbackFormUiState by remember {
      mutableStateOf(FeedbackFormUiState.FillingForm)
    }

    val formData =
      remember(props.initialData) {
        StateMapBackedSupportTicketData(props.initialData)
      }

    return when (uiState) {
      is FeedbackFormUiState.FillingForm ->
        FillingForm(
          structure = props.formStructure,
          formData = formData,
          onBack = props.onBack,
          onSubmitData = {
            uiState = FeedbackFormUiState.SubmittingFormData
          },
          onPrivacyPolicyClick = {
            uiState = FeedbackFormUiState.ShowingPrivacyPolicy
          }
        )

      is FeedbackFormUiState.SubmittingFormData ->
        SubmittingFormData(
          f8eEnvironment = props.f8eEnvironment,
          accountId = props.accountId,
          structure = props.formStructure,
          data = formData,
          onSuccess = {
            uiState = FeedbackFormUiState.SubmitSuccessful
          },
          onError = {
            uiState = FeedbackFormUiState.SubmitFailed
          }
        )

      is FeedbackFormUiState.SubmitFailed ->
        SubmitFailed(
          onRetry = {
            uiState = FeedbackFormUiState.SubmittingFormData
          },
          onDismiss = {
            uiState = FeedbackFormUiState.FillingForm
          }
        )

      FeedbackFormUiState.SubmitSuccessful ->
        SubmitSuccessful(
          onClose = props.onBack
        )

      FeedbackFormUiState.ShowingPrivacyPolicy ->
        InAppBrowserModel(
          open = {
            inAppBrowserNavigator.open(
              url = "https://bitkey.world/en-US/legal/privacy-notice",
              onClose = {
                uiState = FeedbackFormUiState.FillingForm
              }
            )
          }
        ).asModalScreen()
    }
  }

  @Composable
  private fun FillingForm(
    structure: SupportTicketForm,
    formData: StateMapBackedSupportTicketData,
    onBack: () -> Unit,
    onSubmitData: (SupportTicketData) -> Unit,
    onPrivacyPolicyClick: () -> Unit,
  ): ScreenModel {
    var alertUiState: FeedbackAlertUiState? by remember {
      mutableStateOf(null)
    }

    fun confirmLeaveIfNeeded() {
      if (formData.hasPendingChanges()) {
        alertUiState = FeedbackAlertUiState.ViewingLeaveConfirmation
      } else {
        onBack()
      }
    }

    val isValid by remember(structure) {
      derivedStateOf {
        supportTicketFormValidator.validate(structure, formData)
      }
    }

    var isPickingMedia by remember {
      mutableStateOf(false)
    }

    return ScreenModel(
      body =
        FormBodyModel(
          id = FeedbackEventTrackerScreenId.FEEDBACK_FILLING_FORM,
          onBack = { confirmLeaveIfNeeded() },
          toolbar =
            ToolbarModel(
              leadingAccessory =
                ToolbarAccessoryModel.IconAccessory.BackAccessory(onClick = {
                  confirmLeaveIfNeeded()
                }),
              middleAccessory = ToolbarMiddleAccessoryModel(title = "Send feedback")
            ),
          header = null,
          mainContentList = buildImmutableList {
            add(EmailModel(formData.email, formData::email::set))
            addAll(
              structure.fields.mapNotNull { field ->
                FieldModel(
                  field = field,
                  conditions = structure.conditions,
                  data = formData
                )
              }
            )
            add(
              AttachmentsModel(
                attachments = formData.attachments.toImmutableList(),
                addAttachment = { isPickingMedia = true },
                removeAttachment = { formData.removeAttachment(it) }
              )
            )
            add(SendDebugDataModel(formData.sendDebugData, formData::sendDebugData::set))
            add(PrivacyPolicyDisclaimer(onClick = onPrivacyPolicyClick))
          },
          primaryButton =
            ButtonModel(
              text = "Submit",
              isEnabled = isValid,
              size = ButtonModel.Size.Footer,
              onClick = StandardClick {
                onSubmitData(formData.toImmutable())
              }
            )
        ),
      alertModel =
        when (alertUiState) {
          FeedbackAlertUiState.ViewingLeaveConfirmation ->
            FeedbackUiStandaloneModels.confirmLeaveAlertModel(
              onConfirm = onBack,
              onDismiss = {
                alertUiState = null
              }
            )

          null -> null
        },
      systemUIModel =
        if (isPickingMedia) {
          SystemUIModel.MediaPickerModel(
            onMediaPicked = { media ->
              isPickingMedia = false
              media.forEach {
                formData.addAttachment(
                  SupportTicketAttachment.Media(
                    name = it.name,
                    mimeType = it.mimeType,
                    data = { it.data() }
                  )
                )
              }
            }
          )
        } else {
          null
        }
    )
  }

  @Composable
  private fun <Value : Any> FieldModel(
    field: SupportTicketField<Value>,
    conditions: OptimizedSupportTicketFieldConditions,
    data: MutableSupportTicketData,
  ): FormMainContentModel? {
    val conditionResult by remember(field, conditions, data) {
      derivedStateOf {
        conditions.evaluate(field, data)
      }
    }

    val isRequired =
      when (conditionResult) {
        ConditionEvaluationResult.Visible.Optional -> false
        ConditionEvaluationResult.Visible.Required -> true
        ConditionEvaluationResult.Hidden -> return null
      }

    val title = listOfNotNull(field.title, "(required)".takeIf { isRequired }).joinToString(" ")
    return when (field) {
      is SupportTicketField.Picker ->
        PickerModel(
          title = title,
          options = field.items.toImmutableList(),
          selectedOption = data[field],
          onOptionSelected = { data[field] = it },
          titleSelector = { it?.title ?: "-" }
        )

      is SupportTicketField.MultiSelect ->
        MultiChoiceModel(
          title = title,
          items = field.items.toImmutableList(),
          selectedItems = data[field]?.toImmutableSet() ?: persistentSetOf(),
          onItemSelectionChanged = { item, selected ->
            val selectedItems = data[field] ?: emptySet()
            data[field] =
              if (selected) {
                selectedItems + item
              } else {
                selectedItems - item
              }
          }
        )

      is SupportTicketField.TextField ->
        TextFieldModel(
          title = title,
          placeholder = "",
          value = data[field] ?: "",
          onValueChange = { data[field] = it }
        )

      is SupportTicketField.TextArea ->
        TextAreaModel(
          title = title,
          placeholder = "",
          value = data[field] ?: "",
          onValueChange = { data[field] = it }
        )

      is SupportTicketField.CheckBox ->
        CheckBoxModel(
          title = title,
          checked = data[field] ?: false,
          onCheckedChange = { data[field] = it }
        )

      is SupportTicketField.Date ->
        DatePickerModel(
          title = title,
          value = data[field],
          onValueChange = { data[field] = it }
        )
    }
  }

  @Composable
  private fun EmailModel(
    email: Email,
    onEmailChange: (Email) -> Unit,
  ) = FormMainContentModel.TextInput(
    title = "Email (required)",
    fieldModel =
      build.wallet.ui.model.input.TextFieldModel(
        value = email.value,
        placeholderText = "hello@example.org",
        onValueChange = { newValue, _ -> onEmailChange(Email(newValue)) },
        keyboardType = build.wallet.ui.model.input.TextFieldModel.KeyboardType.Email,
        focusByDefault = true
      )
  )

  @Composable
  private fun TextFieldModel(
    title: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
  ) = FormMainContentModel.TextInput(
    title = title,
    fieldModel =
      build.wallet.ui.model.input.TextFieldModel(
        value = value,
        placeholderText = placeholder,
        onValueChange = { newValue, _ ->
          onValueChange(newValue)
        },
        keyboardType = build.wallet.ui.model.input.TextFieldModel.KeyboardType.Default,
        focusByDefault = false
      )
  )

  @Composable
  private fun TextAreaModel(
    title: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
  ) = FormMainContentModel.TextArea(
    title = title,
    fieldModel =
      build.wallet.ui.model.input.TextFieldModel(
        value = value,
        placeholderText = placeholder,
        onValueChange = { newValue, _ ->
          onValueChange(newValue)
        },
        keyboardType = build.wallet.ui.model.input.TextFieldModel.KeyboardType.Default,
        focusByDefault = false
      )
  )

  @Composable
  private fun CheckBoxModel(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
  ) = FormMainContentModel.ListGroup(
    listGroupModel =
      ListGroupModel(
        items =
          immutableListOf(
            ListItemModel(
              title = title,
              trailingAccessory =
                ListItemAccessory.SwitchAccessory(
                  SwitchModel(
                    checked = checked,
                    onCheckedChange = onCheckedChange
                  )
                )
            )
          ),
        style = ListGroupStyle.CARD_ITEM
      )
  )

  @Composable
  private fun DatePickerModel(
    title: String,
    value: LocalDate?,
    onValueChange: (LocalDate) -> Unit,
  ) = FormMainContentModel.DatePicker(
    title = title,
    fieldModel =
      build.wallet.ui.model.datetime.DatePickerModel(
        valueStringRepresentation = value?.let { dateTimeFormatter.longLocalDate(it) } ?: "",
        value = value,
        onValueChange = onValueChange
      )
  )

  @Composable
  private fun AttachmentsModel(
    attachments: ImmutableList<SupportTicketAttachment>,
    addAttachment: () -> Unit,
    removeAttachment: (SupportTicketAttachment) -> Unit,
  ): FormMainContentModel {
    return FormMainContentModel.ListGroup(
      listGroupModel =
        ListGroupModel(
          header = "Attachments",
          items =
            attachments.mapNotNull { attachment ->
              when (attachment) {
                is SupportTicketAttachment.Media ->
                  ListItemModel(
                    attachment.name,
                    trailingAccessory =
                      ListItemAccessory.ButtonAccessory(
                        model =
                          ButtonModel(
                            text = "Remove",
                            treatment = ButtonModel.Treatment.TertiaryDestructive,
                            size = ButtonModel.Size.Compact,
                            onClick =
                              StandardClick {
                                removeAttachment(attachment)
                              }
                          )
                      )
                  )

                is SupportTicketAttachment.Logs -> null
              }
            }.toImmutableList(),
          style = ListGroupStyle.CARD_GROUP,
          footerButton =
            ButtonModel(
              text = "Add attachment",
              size = ButtonModel.Size.Footer,
              onClick = StandardClick(addAttachment)
            )
        )
    )
  }

  @Composable
  private fun SubmittingFormData(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    structure: SupportTicketForm,
    data: SupportTicketData,
    onSuccess: () -> Unit,
    // TODO[W-5853]: Provide error
    onError: () -> Unit,
  ): ScreenModel {
    LaunchedEffect(data) {
      supportTicketRepository.createTicket(
        f8eEnvironment = f8eEnvironment,
        accountId = accountId,
        form = structure,
        data = data
      )
        .onSuccess { onSuccess() }
        .onFailure {
          // TODO[W-5853]: Pass in error details
          onError()
        }
    }

    return LoadingBodyModel(
      id = FeedbackEventTrackerScreenId.FEEDBACK_SUBMITTING
    ).asModalScreen()
  }

  @Composable
  private fun SubmitSuccessful(onClose: () -> Unit): ScreenModel {
    LaunchedEffect("feedback-submit-success") {
      delayer.delay(2.seconds)
      onClose()
    }

    return SuccessBodyModel(
      id = FeedbackEventTrackerScreenId.FEEDBACK_SUBMIT_SUCCESS,
      title = "Success!",
      message = "Feedback submitted successfully!",
      primaryButtonModel = null
    ).asModalScreen()
  }

  @Composable
  private fun SubmitFailed(
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
  ): ScreenModel {
    return ErrorFormBodyModel(
      title = "Couldn't submit your feedback",
      subline = "We couldn't submit your feedback. Please try again later.",
      primaryButton =
        ButtonDataModel(
          text = "Retry",
          onClick = onRetry
        ),
      secondaryButton =
        ButtonDataModel(
          text = "Dismiss",
          onClick = onDismiss
        ),
      eventTrackerScreenId = FeedbackEventTrackerScreenId.FEEDBACK_SUBMIT_FAILED
    ).asModalScreen()
  }

  @Composable
  private fun <Option : Any> PickerModel(
    title: String,
    options: ImmutableList<Option>,
    selectedOption: Option?,
    onOptionSelected: (Option) -> Unit,
    titleSelector: (Option?) -> String,
  ): FormMainContentModel {
    return FormMainContentModel.Picker(
      title = title,
      fieldModel =
        ItemPickerModel(
          selectedOption = selectedOption,
          options = options,
          onOptionSelected = { option ->
            onOptionSelected(option)
          },
          titleSelector = titleSelector
        )
    )
  }

  @Composable
  private fun MultiChoiceModel(
    title: String,
    items: ImmutableList<SupportTicketField.MultiSelect.Item>,
    selectedItems: ImmutableSet<SupportTicketField.MultiSelect.Item>,
    onItemSelectionChanged: (item: SupportTicketField.MultiSelect.Item, selected: Boolean) -> Unit,
  ): FormMainContentModel {
    return FormMainContentModel.ListGroup(
      listGroupModel =
        ListGroupModel(
          header = title,
          items =
            items.map { option ->
              ListItemModel(
                title = option.title,
                trailingAccessory =
                  ListItemAccessory.SwitchAccessory(
                    SwitchModel(
                      checked = option in selectedItems,
                      onCheckedChange = { checked ->
                        onItemSelectionChanged(option, checked)
                      }
                    )
                  )
              )
            }.toImmutableList(),
          style = ListGroupStyle.CARD_GROUP
        )
    )
  }

  @Composable
  private fun SendDebugDataModel(
    sendDebugData: Boolean,
    onSendDebugDataChange: (Boolean) -> Unit,
  ) = FormMainContentModel.ListGroup(
    listGroupModel =
      ListGroupModel(
        items =
          immutableListOf(
            ListItemModel(
              title = "Send debug data",
              secondaryText = "Additional identifying information, never your keys.",
              trailingAccessory =
                ListItemAccessory.SwitchAccessory(
                  SwitchModel(
                    checked = sendDebugData,
                    onCheckedChange = onSendDebugDataChange
                  )
                )
            )
          ),
        style = ListGroupStyle.CARD_ITEM
      )
  )

  @Composable
  private fun PrivacyPolicyDisclaimer(onClick: () -> Unit) =
    FormMainContentModel.Button(
      item =
        ButtonModel(
          text = "Your information will be collected and used in accordance with our Privacy Notice",
          treatment = ButtonModel.Treatment.Tertiary,
          size = ButtonModel.Size.FitContent,
          onClick = StandardClick(onClick)
        )
    )
}

private sealed interface FeedbackFormUiState {
  data object FillingForm : FeedbackFormUiState

  data object SubmittingFormData : FeedbackFormUiState

  data object SubmitSuccessful : FeedbackFormUiState

  data object SubmitFailed : FeedbackFormUiState

  data object ShowingPrivacyPolicy : FeedbackFormUiState
}

private sealed interface FeedbackAlertUiState {
  /**
   * Viewing the main Feedback link screen
   */
  data object ViewingLeaveConfirmation : FeedbackAlertUiState
}
