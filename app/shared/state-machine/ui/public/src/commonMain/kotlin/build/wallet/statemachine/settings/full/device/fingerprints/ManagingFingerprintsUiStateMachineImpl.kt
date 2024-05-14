package build.wallet.statemachine.settings.full.device.fingerprints

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.compose.collections.immutableListOf
import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.FingerprintHandle
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsEventTrackerScreenId.LIST_FINGERPRINTS
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsUiState.AddingNewFingerprintUiState
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsUiState.CheckingFingerprintsUiState
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsUiState.DeletingFingerprintUiState
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsUiState.EditingFingerprintUiState
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsUiState.ListingFingerprintsUiState
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsUiState.SavingFingerprintLabelUiState
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemTreatment
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.toImmutableList

class ManagingFingerprintsUiStateMachineImpl(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val editingFingerprintUiStateMachine: EditingFingerprintUiStateMachine,
  private val enrollingFingerprintUiStateMachine: EnrollingFingerprintUiStateMachine,
) : ManagingFingerprintsUiStateMachine {
  @Composable
  override fun model(props: ManagingFingerprintsProps): ScreenModel {
    var uiState: ManagingFingerprintsUiState by remember {
      mutableStateOf(ListingFingerprintsUiState(props.enrolledFingerprints))
    }

    val listingFingerprintsModel = listingFingerprintsModel(
      enrolledFingerprints = uiState.enrolledFingerprints,
      onBack = props.onBack,
      onEditFingerprint = {
        uiState = EditingFingerprintUiState(
          enrolledFingerprints = uiState.enrolledFingerprints,
          isExistingFingerprint = true,
          fingerprintToEdit = it
        )
      },
      onAddFingerprint = {
        uiState = EditingFingerprintUiState(
          enrolledFingerprints = uiState.enrolledFingerprints,
          isExistingFingerprint = false,
          fingerprintToEdit = FingerprintHandle(index = it, label = "")
        )
      }
    )

    return when (val state = uiState) {
      is ListingFingerprintsUiState -> listingFingerprintsModel.asRootScreen()
      is EditingFingerprintUiState -> ScreenModel(
        body = listingFingerprintsModel,
        bottomSheetModel = editingFingerprintUiStateMachine.model(
          EditingFingerprintProps(
            enrolledFingerprints = state.enrolledFingerprints,
            onBack = {
              uiState = ListingFingerprintsUiState(
                enrolledFingerprints = state.enrolledFingerprints
              )
            },
            onSave = {
              if (state.isExistingFingerprint) {
                uiState = SavingFingerprintLabelUiState(
                  enrolledFingerprints = state.enrolledFingerprints,
                  fingerprintHandle = it
                )
              } else {
                uiState = AddingNewFingerprintUiState(
                  enrolledFingerprints = uiState.enrolledFingerprints,
                  fingerprintHandle = it
                )
              }
            },
            onDeleteFingerprint = {
              uiState = DeletingFingerprintUiState(
                enrolledFingerprints = state.enrolledFingerprints,
                fingerprintToDelete = state.fingerprintToEdit
              )
            },
            fingerprintToEdit = state.fingerprintToEdit,
            isExistingFingerprint = state.isExistingFingerprint
          )
        )
      )
      is SavingFingerprintLabelUiState -> nfcSessionUIStateMachine.model(
        NfcSessionUIStateMachineProps(
          session = { session, commands ->
            commands.setFingerprintLabel(
              session,
              FingerprintHandle(
                index = state.fingerprintHandle.index,
                label = state.fingerprintHandle.label
              )
            )
            commands.getEnrolledFingerprints(session)
          },
          onSuccess = { uiState = ListingFingerprintsUiState(enrolledFingerprints = it) },
          onCancel = {
            uiState = EditingFingerprintUiState(
              enrolledFingerprints = state.enrolledFingerprints,
              isExistingFingerprint = true,
              fingerprintToEdit = state.fingerprintHandle
            )
          },
          isHardwareFake = props.account.config.isHardwareFake,
          screenPresentationStyle = ScreenPresentationStyle.Modal,
          eventTrackerContext = NfcEventTrackerScreenIdContext.SAVE_FINGERPRINT_LABEL
        )
      )
      is CheckingFingerprintsUiState -> TODO("W-6590")
      is AddingNewFingerprintUiState -> enrollingFingerprintUiStateMachine.model(
        EnrollingFingerprintProps(
          account = props.account,
          onCancel = {
            uiState = EditingFingerprintUiState(
              enrolledFingerprints = state.enrolledFingerprints,
              isExistingFingerprint = false,
              fingerprintToEdit = state.fingerprintHandle
            )
          },
          onSuccess = { uiState = ListingFingerprintsUiState(it) },
          fingerprintHandle = state.fingerprintHandle
        )
      )
      is DeletingFingerprintUiState -> nfcSessionUIStateMachine.model(
        NfcSessionUIStateMachineProps(
          session = { session, commands ->
            commands.deleteFingerprint(session, state.fingerprintToDelete.index)
            commands.getEnrolledFingerprints(session)
          },
          onSuccess = { uiState = ListingFingerprintsUiState(it) },
          onCancel = {
            uiState = EditingFingerprintUiState(
              enrolledFingerprints = state.enrolledFingerprints,
              isExistingFingerprint = true,
              fingerprintToEdit = state.fingerprintToDelete
            )
          },
          isHardwareFake = props.account.config.isHardwareFake,
          screenPresentationStyle = ScreenPresentationStyle.Modal,
          eventTrackerContext = NfcEventTrackerScreenIdContext.DELETE_FINGERPRINT
        )
      )
    }
  }
}

private fun listingFingerprintsModel(
  enrolledFingerprints: EnrolledFingerprints,
  onBack: () -> Unit,
  onAddFingerprint: (Int) -> Unit,
  onEditFingerprint: (FingerprintHandle) -> Unit,
) = FormBodyModel(
  id = LIST_FINGERPRINTS,
  onBack = onBack,
  toolbar = ToolbarModel(
    leadingAccessory = ToolbarAccessoryModel.IconAccessory.BackAccessory(onBack)
  ),
  header =
    FormHeaderModel(
      headline = "Bitkey fingerprints",
      subline = "Fingerprints are required to unlock your Bitkey hardware device."
    ),
  primaryButton = null,
  mainContentList = immutableListOf(
    FormMainContentModel.ListGroup(
      listGroupModel = ListGroupModel(
        items = (0..2).map { index ->
          val handle = enrolledFingerprints.fingerprintHandles.find { it.index == index }

          if (handle != null) {
            enrolledFingerprintListItemModel(
              fingerprintHandle = handle,
              onEditFingerprint = onEditFingerprint
            )
          } else {
            placeholderFingerprintListItemModel(
              index = index,
              onAddFingerprint = onAddFingerprint
            )
          }
        }.toImmutableList(),
        style = ListGroupStyle.DIVIDER
      )
    )
  )
)

private fun enrolledFingerprintListItemModel(
  fingerprintHandle: FingerprintHandle,
  onEditFingerprint: (FingerprintHandle) -> Unit,
) = ListItemModel(
  leadingAccessory = ListItemAccessory.IconAccessory(
    model =
      IconModel(
        icon = Icon.SmallIconFingerprint,
        iconTint = IconTint.Foreground,
        iconSize = IconSize.Small
      )
  ),
  title = fingerprintHandle.label.ifEmpty { "Finger ${fingerprintHandle.index + 1}" },
  treatment = ListItemTreatment.PRIMARY,
  trailingAccessory = ListItemAccessory.ButtonAccessory(
    model = ButtonModel(
      text = "Edit",
      size = ButtonModel.Size.Compact,
      treatment = ButtonModel.Treatment.Secondary,
      onClick = StandardClick { onEditFingerprint(fingerprintHandle) }
    )
  )
)

private fun placeholderFingerprintListItemModel(
  index: Int,
  onAddFingerprint: (Int) -> Unit,
) = ListItemModel(
  leadingAccessory = ListItemAccessory.IconAccessory(
    model =
      IconModel(
        icon = Icon.SmallIconFingerprint,
        iconTint = IconTint.On30,
        iconSize = IconSize.Small
      )
  ),
  title = "Finger ${index + 1}",
  treatment = ListItemTreatment.SECONDARY,
  trailingAccessory = ListItemAccessory.ButtonAccessory(
    model = ButtonModel(
      text = "Add",
      size = ButtonModel.Size.Compact,
      treatment = ButtonModel.Treatment.Secondary,
      onClick = StandardClick { onAddFingerprint(index) }
    )
  )
)

private sealed interface ManagingFingerprintsUiState {
  val enrolledFingerprints: EnrolledFingerprints

  data class ListingFingerprintsUiState(
    override val enrolledFingerprints: EnrolledFingerprints,
  ) : ManagingFingerprintsUiState

  data class CheckingFingerprintsUiState(
    override val enrolledFingerprints: EnrolledFingerprints,
  ) : ManagingFingerprintsUiState

  data class EditingFingerprintUiState(
    override val enrolledFingerprints: EnrolledFingerprints,
    val isExistingFingerprint: Boolean,
    val fingerprintToEdit: FingerprintHandle,
  ) : ManagingFingerprintsUiState

  data class AddingNewFingerprintUiState(
    override val enrolledFingerprints: EnrolledFingerprints,
    val fingerprintHandle: FingerprintHandle,
  ) : ManagingFingerprintsUiState

  data class SavingFingerprintLabelUiState(
    override val enrolledFingerprints: EnrolledFingerprints,
    val fingerprintHandle: FingerprintHandle,
  ) : ManagingFingerprintsUiState

  data class DeletingFingerprintUiState(
    override val enrolledFingerprints: EnrolledFingerprints,
    val fingerprintToDelete: FingerprintHandle,
  ) : ManagingFingerprintsUiState
}
