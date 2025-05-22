package build.wallet.statemachine.recovery.socrec.list.lite

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.recovery.socrec.list.listItemModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

const val LITE_TRUSTED_CONTACTS_LIST_HEADER_SUBLINE =
  "These are the people who can reach out to you for help if they lose access to parts of their Bitkey wallet."

/**
 * Body model for the screen showing the list of protected customers that a Lite Account is
 * protecting.
 *
 * Different from [TrustedContactsListBodyModel] which is for a Full Account because Full
 * Accounts can both *be* a Recovery Contact and *have* Recovery Contacts.
 */
data class LiteTrustedContactsListBodyModel(
  override val id: SocialRecoveryEventTrackerScreenId = SocialRecoveryEventTrackerScreenId.TC_MANAGEMENT_SETTINGS_LIST_LITE,
  val protectedCustomers: ImmutableList<ProtectedCustomer>,
  val onProtectedCustomerPressed: (ProtectedCustomer) -> Unit,
  val onAcceptInvitePressed: () -> Unit,
  val onBackPressed: () -> Unit,
  val subline: String = LITE_TRUSTED_CONTACTS_LIST_HEADER_SUBLINE,
) : FormBodyModel(
    id = id,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onBackPressed)),
    header = FormHeaderModel(
      headline = "Recovery Contacts",
      subline = subline
    ),
    mainContentList = immutableListOf(
      FormMainContentModel.ListGroup(
        ListGroupModel(
          header = "Wallets You’re Protecting",
          items = protectedCustomers.map { protectedCustomer ->
            protectedCustomer.listItemModel {
              onProtectedCustomerPressed(it)
            }
          }.toImmutableList(),
          style = ListGroupStyle.CARD_GROUP_DIVIDER,
          footerButton = ButtonModel(
            text = if (protectedCustomers.isEmpty()) "Accept invite" else "Accept another invite",
            treatment = ButtonModel.Treatment.Secondary,
            size = ButtonModel.Size.Footer,
            onClick = StandardClick(onAcceptInvitePressed)
          )
        )
      )
    ),
    onBack = onBackPressed,
    primaryButton = null
  )
