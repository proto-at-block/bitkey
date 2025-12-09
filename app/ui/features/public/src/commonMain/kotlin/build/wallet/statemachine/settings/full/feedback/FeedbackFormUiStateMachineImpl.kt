package build.wallet.statemachine.settings.full.feedback

import androidx.compose.runtime.*
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.compose.collections.buildImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.email.Email
import build.wallet.feature.flags.EncryptedDescriptorSupportUploadFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.root.ActionSuccessDuration
import build.wallet.support.*
import build.wallet.time.DateTimeFormatter
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.list.*
import build.wallet.ui.model.picker.ItemPickerModel
import build.wallet.ui.model.switch.SwitchModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.*
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate

@Suppress("LargeClass")
@BitkeyInject(ActivityScope::class)
class FeedbackFormUiStateMachineImpl(
  private val supportTicketRepository: SupportTicketRepository,
  private val supportTicketFormValidator: SupportTicketFormValidator,
  private val dateTimeFormatter: DateTimeFormatter,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
  private val actionSuccessDuration: ActionSuccessDuration,
  private val encryptedDescriptorSupportUploadFeatureFlag:
    EncryptedDescriptorSupportUploadFeatureFlag,
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

    return when (val state = uiState) {
      is FeedbackFormUiState.FillingForm ->
        FillingForm(
          account = props.account,
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
          structure = props.formStructure,
          data = formData,
          onSuccess = {
            uiState = FeedbackFormUiState.SubmitSuccessful
          },
          onError = { error ->
            uiState = FeedbackFormUiState.SubmitFailed(error)
          }
        )

      is FeedbackFormUiState.SubmitFailed ->
        SubmitFailed(
          error = state.error,
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
    account: Account?,
    structure: SupportTicketForm,
    formData: StateMapBackedSupportTicketData,
    onBack: () -> Unit,
    onSubmitData: (SupportTicketData) -> Unit,
    onPrivacyPolicyClick: () -> Unit,
  ): ScreenModel {
    var alertUiState: FeedbackAlertUiState? by remember {
      mutableStateOf(null)
    }

    val confirmLeaveIfNeeded = {
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

    val contentList = buildImmutableList {
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
      if (encryptedDescriptorSupportUploadFeatureFlag.isEnabled() && account is FullAccount) {
        add(
          SupportRequestedDescriptorPickerModel(
            title = "Has Support requested a wallet descriptor?",
            options = immutableListOf(true, false),
            selectedOption = formData.sendEncryptedDescriptor is SendEncryptedDescriptor.Selected,
            onOptionSelected = {
              formData.sendEncryptedDescriptor = if (it) {
                SendEncryptedDescriptor.Selected(account.accountId)
              } else {
                SendEncryptedDescriptor.NotSelected
              }
            },
            titleSelector = { if (it == true) "Yes" else "No" }
          )
        )

        if (formData.sendEncryptedDescriptor is SendEncryptedDescriptor.Selected) {
          add(DeviceInfoLearnMoreModel(LearnMore.EncryptedDescriptor))
        }
      }

      add(
        AttachmentsModel(
          attachments = formData.attachments.toImmutableList(),
          addAttachment = { isPickingMedia = true },
          removeAttachment = { formData.removeAttachment(it) }
        )
      )

      if (formData.sendEncryptedDescriptor is SendEncryptedDescriptor.NotSelected) {
        add(SendDebugDataModel(formData.sendDebugData, formData::sendDebugData::set))
        add(DeviceInfoLearnMoreModel(LearnMore.DeviceInfo))
      }

      add(PrivacyPolicyDisclaimer(onClick = onPrivacyPolicyClick))
    }

    return ScreenModel(
      body = FillingFormBodyModel(
        formData = formData,
        onSubmitData = onSubmitData,
        confirmLeaveIfNeeded = confirmLeaveIfNeeded,
        isValid = isValid,
        mainContentList = contentList
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
    structure: SupportTicketForm,
    data: SupportTicketData,
    onSuccess: () -> Unit,
    onError: (SupportTicketError) -> Unit,
  ): ScreenModel {
    LaunchedEffect(data) {
      supportTicketRepository.createTicket(
        form = structure,
        data = data
      )
        .onSuccess { onSuccess() }
        .onFailure { error ->
          onError(error)
        }
    }

    return LoadingBodyModel(
      id = FeedbackEventTrackerScreenId.FEEDBACK_SUBMITTING
    ).asModalScreen()
  }

  @Composable
  private fun SubmitSuccessful(onClose: () -> Unit): ScreenModel {
    LaunchedEffect("feedback-submit-success") {
      delay(actionSuccessDuration.value)
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
    error: SupportTicketError,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
  ): ScreenModel {
    val (title, subline, retryable) = when (error) {
      SupportTicketError.InvalidEmailAddress ->
        Triple(
          "The entered email is not valid.",
          "Please provide a different email.",
          false
        )

      is SupportTicketError.NetworkFailure ->
        Triple(
          "Couldn't submit your feedback",
          "We couldn't submit your feedback. Please try again later.",
          true
        )
    }

    val dismissButton = ButtonDataModel(
      text = "Dismiss",
      onClick = onDismiss
    )

    return ErrorFormBodyModel(
      title = title,
      subline = subline,
      primaryButton = if (retryable) {
        ButtonDataModel(
          text = "Retry",
          onClick = onRetry
        )
      } else {
        dismissButton
      },
      secondaryButton = if (retryable) dismissButton else null,
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
              title = "Send device info",
              secondaryText = "Basic device and app diagnostic info. No personal data is included.",
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
  private fun SupportRequestedDescriptorPickerModel(
    title: String,
    options: ImmutableList<Boolean>,
    selectedOption: Boolean,
    onOptionSelected: (Boolean) -> Unit,
    titleSelector: (Boolean?) -> String,
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
  private fun DeviceInfoLearnMoreModel(type: LearnMore): FormMainContentModel {
    return FormMainContentModel.ListGroup(
      listGroupModel =
        ListGroupModel(
          items =
            immutableListOf(
              ListItemModel(
                leadingAccessory = null,
                title = type.title(),
                treatment = ListItemTreatment.INFO,
                titleLabel = LabelModel.LinkSubstringModel.from(
                  substringToOnClick = mapOf(
                    Pair(
                      first = "Learn more",
                      second = {
                        inAppBrowserNavigator.open(
                          url = "https://bitkey.world/hc/data-sent-to-cs",
                          onClose = {}
                        )
                      }
                    )
                  ),
                  string = type.text(),
                  underline = true,
                  bold = false
                )
              )
            ),
          style = ListGroupStyle.NONE
        )
    )
  }

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

  enum class LearnMore {
    DeviceInfo,
    EncryptedDescriptor,
    ;

    fun title(): String {
      return when (this) {
        DeviceInfo -> "Device Info"
        EncryptedDescriptor -> "Wallet Descriptor"
      }
    }

    fun text(): String {
      return when (this) {
        DeviceInfo -> "Basic device and app diagnostic info. No personal data is included. Learn more."
        EncryptedDescriptor -> "By selecting “Yes” you agree to send your wallet descriptor and basic device info. Learn more."
      }
    }
  }
}

private sealed interface FeedbackFormUiState {
  data object FillingForm : FeedbackFormUiState

  data object SubmittingFormData : FeedbackFormUiState

  data object SubmitSuccessful : FeedbackFormUiState

  data class SubmitFailed(val error: SupportTicketError) : FeedbackFormUiState

  data object ShowingPrivacyPolicy : FeedbackFormUiState
}

private sealed interface FeedbackAlertUiState {
  /**
   * Viewing the main Feedback link screen
   */
  data object ViewingLeaveConfirmation : FeedbackAlertUiState
}
