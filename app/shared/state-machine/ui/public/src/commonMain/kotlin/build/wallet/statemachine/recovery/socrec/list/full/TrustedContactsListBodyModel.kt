package build.wallet.statemachine.recovery.socrec.list.full

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.bitkey.socrec.Invitation
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.RecoveryContact
import build.wallet.bitkey.socrec.TrustedContact
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.recovery.socrec.list.lite.LiteTrustedContactsListBodyModel
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemAccessory.Companion.drillIcon
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemSideTextTint
import kotlinx.collections.immutable.toImmutableList

private const val TRUSTED_CONTACT_COUNT_LIMIT = 3

/**
 * Data used in the TC Management screen.
 */
fun TrustedContactsListBodyModel(
  /**
   * List of the current user's trusted contacts to be displayed.
   */
  contacts: List<TrustedContact>,
  /**
   * List of the current user's trusted contacts to be displayed.
   */
  invitations: List<Invitation>,
  /**
   * List of the current user's protected customers
   * (i.e. customers they are serving as Trusted Contact for) to be displayed.
   */
  protectedCustomers: List<ProtectedCustomer>,
  /**
   * Current time, used to determine if an invitation is expired.
   */
  now: Long,
  /**
   * Invoked when the user clicks an add action to the list of contacts.
   */
  onAddPressed: () -> Unit,
  /**
   * Invoked when the user clicks on a trusted contact or invitation in the list of contacts.
   */
  onContactPressed: (RecoveryContact) -> Unit,
  /**
   * Invoked when the user clicks on a customer in the list of protected customers.
   */
  onProtectedCustomerPressed: (ProtectedCustomer) -> Unit,
  /**
   * Invoked when the user clicks the accept invite action to become a Trusted Contact.
   */
  onAcceptInvitePressed: () -> Unit,
  onBackPressed: () -> Unit,
): FormBodyModel {
  val lite =
    LiteTrustedContactsListBodyModel(
      id = SocialRecoveryEventTrackerScreenId.TC_MANAGEMENT_SETTINGS_LIST,
      protectedCustomers = protectedCustomers.toImmutableList(),
      onProtectedCustomerPressed = onProtectedCustomerPressed,
      onAcceptInvitePressed = onAcceptInvitePressed,
      onBackPressed = onBackPressed,
      subline = "Add people you trust to securely recover your wallet in case of lost access."
    )

  // Determine if the user can invite more trusted contacts.
  val relationships = contacts.plus(invitations)

  return lite.copy(
    mainContentList =
      immutableListOf(
        FormMainContentModel.ListGroup(
          ListGroupModel(
            header = "Your Trusted Contacts",
            items =
              relationships
                .toListItems(now, onContactPressed)
                .toImmutableList(),
            style = ListGroupStyle.CARD_GROUP_DIVIDER,
            footerButton =
              ButtonModel(
                text = "Invite",
                treatment = ButtonModel.Treatment.Secondary,
                size = ButtonModel.Size.Footer,
                onClick = Click.standardClick { onAddPressed() }
              ).takeIf { relationships.size < TRUSTED_CONTACT_COUNT_LIMIT }
          )
        ),
        lite.mainContentList[0]
      )
  )
}

/**
 * Convert a list of recovery contacts to row items for a ListGroup.
 */
private fun List<RecoveryContact>.toListItems(
  now: Long,
  onClick: (RecoveryContact) -> Unit,
) = map { contact ->
  ListItemModel(
    leadingAccessory =
      ListItemAccessory.CircularCharacterAccessory(
        character = contact.trustedContactAlias.alias.first().uppercaseChar()
      ),
    title = contact.trustedContactAlias.alias,
    sideText = sideText(contact, now),
    sideTextTint = ListItemSideTextTint.SECONDARY,
    trailingAccessory = drillIcon(tint = IconTint.On30),
    onClick = { onClick(contact) }
  )
}

private fun sideText(
  recoveryContact: RecoveryContact,
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
