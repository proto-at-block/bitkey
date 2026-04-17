package build.wallet.statemachine.recovery.socrec.list.full

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_MANAGEMENT_SETTINGS_LIST
import build.wallet.bitkey.relationships.EndorsedTrustedContact
import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.bitkey.relationships.TrustedContact
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormDesignSystemV2Model
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.recovery.socrec.list.listItemModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.*
import build.wallet.ui.model.list.ListItemAccessory.Companion.drillIcon
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.tokens.LabelType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

private const val TRUSTED_CONTACT_COUNT_LIMIT = 3
private const val RECOVERY_CONTACTS_TITLE = "Recovery Contacts"
private const val RECOVERY_CONTACTS_SUBLINE =
  "Add people you trust to securely recover your wallet in case of lost access."
private const val ADD_NEW_CONTACT_TITLE = "Add new contact"
private const val PROTECT_NEW_WALLET_TITLE = "Protect new wallet"

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
      headline = RECOVERY_CONTACTS_TITLE,
      subline = RECOVERY_CONTACTS_SUBLINE
    ),
    mainContentList =
      trustedContactsMainContentList(
        contacts = contacts,
        invitations = invitations,
        protectedCustomers = protectedCustomers,
        now = now,
        listStyle = ListGroupStyle.CARD_GROUP_DIVIDER,
        useInlineActionRows = false,
        onAddPressed = onAddPressed,
        onContactPressed = onContactPressed,
        onProtectedCustomerPressed = onProtectedCustomerPressed,
        onAcceptInvitePressed = onAcceptInvitePressed
      ),
    onBack = onBackPressed,
    primaryButton = null,
    designSystemV2Model =
      FormDesignSystemV2Model(
        title = RECOVERY_CONTACTS_TITLE,
        header =
          FormHeaderModel(
            headline = null,
            sublineModel = StringModel(RECOVERY_CONTACTS_SUBLINE)
          ),
        mainContentList =
          trustedContactsMainContentList(
            contacts = contacts,
            invitations = invitations,
            protectedCustomers = protectedCustomers,
            now = now,
            listStyle = ListGroupStyle.DIVIDER,
            useInlineActionRows = true,
            onAddPressed = onAddPressed,
            onContactPressed = onContactPressed,
            onProtectedCustomerPressed = onProtectedCustomerPressed,
            onAcceptInvitePressed = onAcceptInvitePressed
          )
      )
  )

private fun trustedContactsMainContentList(
  contacts: List<EndorsedTrustedContact>,
  invitations: List<Invitation>,
  protectedCustomers: List<ProtectedCustomer>,
  now: Long,
  listStyle: ListGroupStyle,
  useInlineActionRows: Boolean,
  headerTreatment: ListGroupModel.HeaderTreatment = ListGroupModel.HeaderTreatment.SECONDARY,
  onAddPressed: () -> Unit,
  onContactPressed: (TrustedContact) -> Unit,
  onProtectedCustomerPressed: (ProtectedCustomer) -> Unit,
  onAcceptInvitePressed: () -> Unit,
): ImmutableList<FormMainContentModel> =
  immutableListOf(
    FormMainContentModel.ListGroup(
      ListGroupModel(
        header = "Your Recovery Contacts",
        items =
          buildList {
            addAll(
              (contacts + invitations)
                .toListItems(
                  now = now,
                  onClick = onContactPressed,
                  useLargeLeadingAccessory = useInlineActionRows
                )
            )
            if (useInlineActionRows && (invitations + contacts).size < TRUSTED_CONTACT_COUNT_LIMIT) {
              add(
                recoveryContactActionListItem(
                  title = ADD_NEW_CONTACT_TITLE,
                  onClick = onAddPressed
                )
              )
            }
          }.toImmutableList(),
        style = listStyle,
        headerTreatment = headerTreatment,
        footerButton = ButtonModel(
          text = "Invite",
          treatment = ButtonModel.Treatment.Secondary,
          size = ButtonModel.Size.Footer,
          onClick = StandardClick(onAddPressed)
        ).takeIf {
          // Determine if the user can invite more trusted contacts.
          !useInlineActionRows && (invitations + contacts).size < TRUSTED_CONTACT_COUNT_LIMIT
        }
      )
    ),
    FormMainContentModel.ListGroup(
      ListGroupModel(
        header = "Wallets You’re Protecting",
        items =
          buildList {
            addAll(
              protectedCustomers.map { protectedCustomer ->
                protectedCustomer.listItemModel(useLargeLeadingAccessory = useInlineActionRows) {
                  onProtectedCustomerPressed(it)
                }
              }
            )
            if (useInlineActionRows) {
              add(
                recoveryContactActionListItem(
                  title = PROTECT_NEW_WALLET_TITLE,
                  onClick = onAcceptInvitePressed
                )
              )
            }
          }
            .toImmutableList(),
        style = listStyle,
        headerTreatment = headerTreatment,
        footerButton = ButtonModel(
          text = if (protectedCustomers.isEmpty()) "Accept invite" else "Accept another invite",
          treatment = ButtonModel.Treatment.Secondary,
          size = ButtonModel.Size.Footer,
          onClick = StandardClick(onAcceptInvitePressed)
        ).takeIf { !useInlineActionRows }
      )
    )
  )

/**
 * Convert a list of recovery contacts to row items for a ListGroup.
 */
private fun List<TrustedContact>.toListItems(
  now: Long,
  onClick: (TrustedContact) -> Unit,
  useLargeLeadingAccessory: Boolean,
) = map { contact ->
  ListItemModel(
    titleType = if (useLargeLeadingAccessory) LabelType.Body2Regular else null,
    leadingAccessory =
      ListItemAccessory.CircularCharacterAccessory.fromLetters(
        input = contact.trustedContactAlias.alias,
        circleSize = if (useLargeLeadingAccessory) IconSize.Large else IconSize.Small,
        characterType = if (useLargeLeadingAccessory) LabelType.Body2Medium else LabelType.Label3
      ),
    title = contact.trustedContactAlias.alias,
    sideText = sideText(contact, now),
    sideTextTint = ListItemSideTextTint.SECONDARY,
    trailingAccessory = drillIcon(tint = IconTint.On30),
    onClick = { onClick(contact) }
  )
}

private fun recoveryContactActionListItem(
  title: String,
  onClick: () -> Unit,
) = ListItemModel(
  title = title,
  titleType = LabelType.Body2Regular,
  leadingAccessory =
    ListItemAccessory.CircularIconAccessory(
      icon = Icon.SmallIconPlus,
      circleSize = IconSize.Large,
      iconSize = IconSize.Accessory
    ),
  trailingAccessory = drillIcon(tint = IconTint.On30),
  onClick = onClick
)

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
