package build.wallet.statemachine.recovery.socrec.list.full

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_MANAGEMENT_SETTINGS_LIST
import build.wallet.bitkey.relationships.EndorsedTrustedContact
import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.bitkey.relationships.TrustedContact
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.recovery.socrec.list.listItemModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.*
import build.wallet.ui.model.list.ListItemAccessory.Companion.drillIcon
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.toImmutableList

private const val TRUSTED_CONTACT_COUNT_LIMIT = 3

/**
 * Data used in the TC Management screen.
 */
data class TrustedContactsListBodyModel(
  /**
   * List of the current user's trusted contacts to be displayed.
   */
  val contacts: List<EndorsedTrustedContact>,
  /**
   * List of the current user's trusted contacts to be displayed.
   */
  val invitations: List<Invitation>,
  /**
   * List of the current user's protected customers
   * (i.e. customers they are serving as Trusted Contact for) to be displayed.
   */
  val protectedCustomers: List<ProtectedCustomer>,
  /**
   * Current time, used to determine if an invitation is expired.
   */
  val now: Long,
  /**
   * Invoked when the user clicks an add action to the list of contacts.
   */
  val onAddPressed: () -> Unit,
  /**
   * Invoked when the user clicks on a trusted contact or invitation in the list of contacts.
   */
  val onContactPressed: (TrustedContact) -> Unit,
  /**
   * Invoked when the user clicks on a customer in the list of protected customers.
   */
  val onProtectedCustomerPressed: (ProtectedCustomer) -> Unit,
  /**
   * Invoked when the user clicks the accept invite action to become a Trusted Contact.
   */
  val onAcceptInvitePressed: () -> Unit,
  val onBackPressed: () -> Unit,
) : FormBodyModel(
    id = TC_MANAGEMENT_SETTINGS_LIST,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onBackPressed)),
    header = FormHeaderModel(
      headline = "Recovery Contacts",
      subline = "Add people you trust to securely recover your wallet in case of lost access."
    ),
    mainContentList = immutableListOf(
      FormMainContentModel.ListGroup(
        ListGroupModel(
          header = "Your Recovery Contacts",
          items = (contacts + invitations)
            .toListItems(now, onContactPressed)
            .toImmutableList(),
          style = ListGroupStyle.CARD_GROUP_DIVIDER,
          footerButton = ButtonModel(
            text = "Invite",
            treatment = ButtonModel.Treatment.Secondary,
            size = ButtonModel.Size.Footer,
            onClick = StandardClick(onAddPressed)
          ).takeIf {
            // Determine if the user can invite more trusted contacts.
            (invitations + contacts).size < TRUSTED_CONTACT_COUNT_LIMIT
          }
        )
      ),
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

/**
 * Convert a list of recovery contacts to row items for a ListGroup.
 */
private fun List<TrustedContact>.toListItems(
  now: Long,
  onClick: (TrustedContact) -> Unit,
) = map { contact ->
  ListItemModel(
    leadingAccessory =
      ListItemAccessory.CircularCharacterAccessory.fromLetters(
        contact.trustedContactAlias.alias
      ),
    title = contact.trustedContactAlias.alias,
    sideText = sideText(contact, now),
    sideTextTint = ListItemSideTextTint.SECONDARY,
    trailingAccessory = drillIcon(tint = IconTint.On30),
    onClick = { onClick(contact) }
  )
}

private fun sideText(
  recoveryContact: TrustedContact,
  now: Long,
): String? =
  if (recoveryContact is Invitation) {
    if (recoveryContact.isExpired(now)) {
      "Expired"
    } else {
      "Pending"
    }
  } else {
    null
  }
