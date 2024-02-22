package build.wallet.statemachine.recovery.socrec.help.model

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

fun VerifyingContactMethodFormBodyModel(
  onBack: () -> Unit,
  onTextMessageClick: () -> Unit,
  onEmailClick: () -> Unit,
  onPhoneCallClick: () -> Unit,
  onVideoChatClick: () -> Unit,
  onInPersonClick: () -> Unit,
): FormBodyModel {
  return FormBodyModel(
    header =
      FormHeaderModel(
        headline = "How did they get in touch with you about their recovery?"
      ),
    onBack = onBack,
    toolbar =
      ToolbarModel(
        leadingAccessory =
          ToolbarAccessoryModel.IconAccessory.CloseAccessory {
            onBack()
          }
      ),
    mainContentList =
      immutableListOf(
        FormMainContentModel.ListGroup(
          listGroupModel =
            ListGroupModel(
              style = ListGroupStyle.CARD_ITEM,
              items =
                immutableListOf(
                  ListItemModel(
                    leadingAccessory =
                      ListItemAccessory.IconAccessory(
                        icon = Icon.SmallIconMessage
                      ),
                    title = "Text Message",
                    trailingAccessory = ListItemAccessory.drillIcon(IconTint.On30),
                    onClick = onTextMessageClick
                  ),
                  ListItemModel(
                    leadingAccessory =
                      ListItemAccessory.IconAccessory(
                        icon = Icon.SmallIconEmail
                      ),
                    title = "Email",
                    trailingAccessory = ListItemAccessory.drillIcon(IconTint.On30),
                    onClick = onEmailClick
                  ),
                  ListItemModel(
                    leadingAccessory =
                      ListItemAccessory.IconAccessory(
                        icon = Icon.SmallIconPhone
                      ),
                    title = "Phone Call",
                    trailingAccessory = ListItemAccessory.drillIcon(IconTint.On30),
                    onClick = onPhoneCallClick
                  ),
                  ListItemModel(
                    leadingAccessory =
                      ListItemAccessory.IconAccessory(
                        icon = Icon.SmallIconVideo
                      ),
                    title = "Video Chat",
                    trailingAccessory = ListItemAccessory.drillIcon(IconTint.On30),
                    onClick = onVideoChatClick
                  ),
                  ListItemModel(
                    leadingAccessory =
                      ListItemAccessory.IconAccessory(
                        icon = Icon.SmallIconAccount
                      ),
                    title = "In Person",
                    trailingAccessory = ListItemAccessory.drillIcon(IconTint.On30),
                    onClick = onInPersonClick
                  )
                )
            )
        )
      ),
    primaryButton = null,
    id = SocialRecoveryEventTrackerScreenId.TC_RECOVERY_GET_IN_TOUCH
  )
}

fun SecurityNoticeFormBodyModel(onBack: () -> Unit): FormBodyModel {
  return FormBodyModel(
    header =
      FormHeaderModel(
        headline = "Insecure verification method",
        subline =
          """
          For the safety of the person you’re protecting, we strongly recommend using a more reliable method of identifying the source of the request for help, such as a video chat or meeting in person.
          
          Texts, emails, and even phone calls can be susceptible to scam attempts and impersonation risks.
          """.trimIndent(),
        icon = Icon.LargeIconWarningFilled
      ),
    onBack = onBack,
    toolbar =
      ToolbarModel(
        leadingAccessory =
          ToolbarAccessoryModel.IconAccessory.BackAccessory {
            onBack()
          }
      ),
    primaryButton = null,
    id = SocialRecoveryEventTrackerScreenId.TC_RECOVERY_SECURITY_NOTICE
  )
}

fun ConfirmingIdentityFormBodyModel(
  protectedCustomer: ProtectedCustomer,
  onBack: () -> Unit,
  onVerifiedClick: () -> Unit,
) = FormBodyModel(
  header =
    FormHeaderModel(
      headline = "Are you sure it’s really ${protectedCustomer.alias.alias} asking for help?",
      subline = "To safeguard their wallet from potential scams, it's crucial we confirm the authenticity of your Trusted Contact's communication."
    ),
  onBack = onBack,
  toolbar =
    ToolbarModel(
      leadingAccessory =
        ToolbarAccessoryModel.IconAccessory.BackAccessory {
          onBack()
        }
    ),
  mainContentList =
    immutableListOf(
      FormMainContentModel.ListGroup(
        listGroupModel =
          ListGroupModel(
            style = ListGroupStyle.CARD_ITEM,
            items =
              immutableListOf(
                ListItemModel(
                  title = "Yes, I verified their identity",
                  trailingAccessory = ListItemAccessory.drillIcon(IconTint.On30),
                  onClick = onVerifiedClick
                ),
                ListItemModel(
                  title = "I'm not sure",
                  trailingAccessory = ListItemAccessory.drillIcon(IconTint.On30),
                  onClick = onBack
                )
              )
          )
      )
    ),
  primaryButton = null,
  id = SocialRecoveryEventTrackerScreenId.TC_RECOVERY_CONTACT_CONFIRMATION
)

fun EnterRecoveryCodeFormBodyModel(
  value: String,
  onBack: () -> Unit,
  onContinueClick: () -> Unit,
  onInputChange: (String) -> Unit,
): FormBodyModel =
  FormBodyModel(
    header =
      FormHeaderModel(
        headline = "Enter recovery code",
        subline = "Use the code your recovery contact provided you with."
      ),
    onBack = onBack,
    toolbar =
      ToolbarModel(
        leadingAccessory =
          ToolbarAccessoryModel.IconAccessory.BackAccessory {
            onBack()
          }
      ),
    mainContentList =
      immutableListOf(
        FormMainContentModel.TextInput(
          fieldModel =
            TextFieldModel(
              value = value,
              selectionOverride = null,
              placeholderText = "••••••",
              onValueChange = { s: String, _: IntRange ->
                onInputChange(s)
              },
              keyboardType = TextFieldModel.KeyboardType.Number
            )
        )
      ),
    primaryButton =
      ButtonModel(
        text = "Continue",
        onClick = Click.standardClick { onContinueClick() },
        size = ButtonModel.Size.Footer
      ),
    id = SocialRecoveryEventTrackerScreenId.TC_RECOVERY_CODE_VERIFICATION
  )
