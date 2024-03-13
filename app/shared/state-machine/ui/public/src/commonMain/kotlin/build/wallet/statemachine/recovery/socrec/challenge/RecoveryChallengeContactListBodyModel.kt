package build.wallet.statemachine.recovery.socrec.challenge

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.bitkey.socrec.TrustedContact
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemSideTextTint
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

fun RecoveryChallengeContactListBodyModel(
  onExit: () -> Unit,
  trustedContacts: ImmutableList<TrustedContact>,
  onVerifyClick: (TrustedContact) -> Unit,
  verifiedBy: ImmutableList<String>,
  onContinue: () -> Unit,
) = FormBodyModel(
  id = SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_TRUSTED_CONTACTS_LIST,
  onBack = onExit,
  toolbar = ToolbarModel(),
  header =
    FormHeaderModel(
      headline = "Select a Trusted Contact",
      subline = "Choose a Trusted Contact to help with recovery."
    ),
  mainContentList =
    immutableListOf(
      FormMainContentModel.ListGroup(
        ListGroupModel(
          header = "Your Trusted Contacts",
          style = ListGroupStyle.CARD_GROUP_DIVIDER,
          items =
            trustedContacts.map { contact ->
              ListItemModel(
                leadingAccessory =
                  ListItemAccessory.CircularCharacterAccessory(
                    character = contact.trustedContactAlias.alias.first().uppercaseChar()
                  ),
                title = contact.trustedContactAlias.alias,
                sideTextTint = ListItemSideTextTint.PRIMARY,
                sideText =
                  "Verified".takeIf {
                    verifiedBy.contains(
                      contact.recoveryRelationshipId
                    )
                  },
                trailingAccessory =
                  ListItemAccessory.ButtonAccessory(
                    model =
                      ButtonModel(
                        text = "Select",
                        onClick = StandardClick { onVerifyClick(contact) },
                        treatment = ButtonModel.Treatment.Secondary,
                        size = ButtonModel.Size.Compact
                      )
                  ).takeIf { verifiedBy.isEmpty() }
              )
            }.toImmutableList()
        )
      )
    ),
  primaryButton =
    ButtonModel(
      text = "Continue",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick { onContinue() }
    ).takeIf { verifiedBy.isNotEmpty() }
)
