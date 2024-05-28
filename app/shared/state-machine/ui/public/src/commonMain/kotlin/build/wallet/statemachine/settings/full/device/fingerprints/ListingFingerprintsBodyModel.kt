package build.wallet.statemachine.settings.full.device.fingerprints

import build.wallet.compose.collections.immutableListOf
import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.FingerprintHandle
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
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

fun ListingFingerprintsBodyModel(
  enrolledFingerprints: EnrolledFingerprints,
  onBack: () -> Unit,
  onAddFingerprint: (Int) -> Unit,
  onEditFingerprint: (FingerprintHandle) -> Unit,
) = FormBodyModel(
  id = ManagingFingerprintsEventTrackerScreenId.LIST_FINGERPRINTS,
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
