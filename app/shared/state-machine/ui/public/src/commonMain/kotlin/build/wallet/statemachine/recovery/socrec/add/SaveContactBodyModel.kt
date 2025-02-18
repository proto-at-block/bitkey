package build.wallet.statemachine.recovery.socrec.add

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.collections.immutableListOfNotNull
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.notifications.TosInfo
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Companion.BitkeyInteractionButtonModel
import build.wallet.ui.model.callout.CalloutModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint.On30
import build.wallet.ui.model.icon.IconTint.Primary
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory.IconAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemTreatment
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Prompt the user to save their trusted contact with bitkey.
 */
data class SaveContactBodyModel(
  /**
   * Name of the trusted contact to be added.
   */
  val trustedContactName: String,
  /**
   * Boolean indicating whether we are saving an inheritance beneficiary
   */
  val isBeneficiary: Boolean,
  /**
   * Invoked when the user agrees to save with bitkey.
   */
  val onSave: () -> Unit,
  /**
   * Invoked when the user navigates back.
   */
  val onBackPressed: () -> Unit,
  /**
   * Information about the terms of service.
   */
  val tosInfo: TosInfo?,
  /**
   * If we should show the terms error
   */
  val termsError: Boolean = false,
) : FormBodyModel(
    id = if (isBeneficiary) {
      SocialRecoveryEventTrackerScreenId.TC_BENEFICIARY_ENROLLMENT_ADD_TC_HARDWARE_CHECK
    } else {
      SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_ADD_TC_HARDWARE_CHECK
    },
    onBack = onBackPressed,
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.BackAccessory(onBackPressed)
    ),
    header = FormHeaderModel(
      icon = Icon.LargeIconShieldPerson,
      headline = if (isBeneficiary) "Save beneficiary" else "Save $trustedContactName as a Trusted Contact",
      subline = "Adding a " + (if (isBeneficiary) "beneficiary" else "Trusted Contact") + " requires you to tap your Bitkey device since it impacts the security of your wallet."
    ),
    mainContentList = immutableListOfNotNull(
      FormMainContentModel.Spacer(),
      FormMainContentModel.Callout(
        item = CalloutModel(
          leadingIcon = Icon.SmallIconWarningFilled,
          title = "Please accept the inheritance terms and conditions",
          treatment = CalloutModel.Treatment.Warning
        )
      ).takeIf { termsError && !(tosInfo?.termsAgree ?: false) },
      tosInfo?.let { ti ->
        ListGroup(
          listGroupModel =
            ListGroupModel(
              items =
                immutableListOf(
                  ListItemModel(
                    title = "TOS",
                    titleLabel = LabelModel.LinkSubstringModel.from(
                      substringToOnClick = mapOf(
                        Pair(
                          first = "inheritance terms and conditions",
                          second = { ti.tosLink() }
                        )
                      ),
                      string = "By clicking continue I agree to the inheritance terms and conditions",
                      underline = false,
                      bold = false
                    ),
                    treatment = ListItemTreatment.PRIMARY,
                    leadingAccessory = IconAccessory(
                      onClick = { ti.onTermsAgreeToggle(!ti.termsAgree) },
                      model = IconModel(
                        icon = if (ti.termsAgree) {
                          Icon.SmallIconCheckFilled
                        } else {
                          Icon.SmallIconCircleStroked
                        },
                        iconSize = IconSize.Regular,
                        iconTint = if (ti.termsAgree) {
                          Primary
                        } else {
                          On30
                        }
                      )
                    )
                  )
                ),
              style = ListGroupStyle.DIVIDER
            )
        )
      }
    ).takeIf { isBeneficiary } ?: emptyImmutableList(),
    primaryButton = BitkeyInteractionButtonModel(
      text = "Save " + if (isBeneficiary) "Beneficiary" else "Trusted Contact",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onSave)
    )
  )
