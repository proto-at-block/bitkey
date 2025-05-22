package build.wallet.statemachine.recovery.socrec.challenge

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.bitkey.relationships.EndorsedTrustedContact
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.list.*
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

data class RecoveryChallengeContactListBodyModel(
  val onExit: () -> Unit,
  val endorsedTrustedContacts: ImmutableList<EndorsedTrustedContact>,
  val onVerifyClick: (EndorsedTrustedContact) -> Unit,
  val verifiedBy: ImmutableList<String>,
  val onContinue: () -> Unit,
  val onCancelRecovery: () -> Unit,
) : FormBodyModel(
    id = SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_TRUSTED_CONTACTS_LIST,
    onBack = onExit,
    toolbar = ToolbarModel(
      trailingAccessory = ToolbarAccessoryModel.ButtonAccessory(
        model = ButtonModel(
          text = "Cancel recovery",
          treatment = ButtonModel.Treatment.TertiaryDestructive,
          size = ButtonModel.Size.Compact,
          onClick = StandardClick(onCancelRecovery)
        )
      )
    ),
    header = FormHeaderModel(
      headline = "Select a Recovery Contact",
      subline = "Choose a Recovery Contact to help with recovery."
    ),
    mainContentList = immutableListOf(
      FormMainContentModel.ListGroup(
        ListGroupModel(
          header = "Your Recovery Contacts",
          style = ListGroupStyle.CARD_GROUP_DIVIDER,
          items = endorsedTrustedContacts.map { contact ->
            ListItemModel(
              leadingAccessory = ListItemAccessory.CircularCharacterAccessory.fromLetters(
                input = contact.trustedContactAlias.alias
              ),
              title = contact.trustedContactAlias.alias,
              sideTextTint = ListItemSideTextTint.PRIMARY,
              sideText = "Verified".takeIf {
                verifiedBy.contains(contact.relationshipId)
              },
              trailingAccessory = ListItemAccessory.ButtonAccessory(
                model = ButtonModel(
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
    primaryButton = ButtonModel(
      text = "Continue",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick { onContinue() }
    ).takeIf { verifiedBy.isNotEmpty() }
      ?: ButtonModel(
        text = "Waiting for your Recovery Contact to verify you\u2026",
        treatment = ButtonModel.Treatment.TertiaryNoUnderline,
        size = ButtonModel.Size.Footer,
        onClick = StandardClick {}
      )
  )
