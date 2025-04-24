package bitkey.ui.sheets

import androidx.compose.runtime.*
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.Sheet
import bitkey.ui.framework.SheetPresenter
import bitkey.ui.screens.trustedcontact.ReinviteTrustedContactScreen
import bitkey.ui.screens.trustedcontact.RemoveTrustedContactScreen
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.platform.sharing.SharingManager
import build.wallet.platform.sharing.shareInvitation
import build.wallet.recovery.socrec.InviteCodeLoader
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.trustedcontact.view.ViewingInvitationBodyModel
import com.github.michaelbull.result.onSuccess
import kotlinx.datetime.Clock

data class ViewInvitationSheet(
  val account: FullAccount,
  val invitation: Invitation,
  override val origin: Screen,
) : Sheet

@BitkeyInject(ActivityScope::class)
class ViewInvitationSheetPresenter(
  private val sharingManager: SharingManager,
  private val clock: Clock,
  private val inviteCodeLoader: InviteCodeLoader,
) : SheetPresenter<ViewInvitationSheet> {
  @Composable
  override fun model(
    navigator: Navigator,
    sheet: ViewInvitationSheet,
  ): SheetModel {
    var code: String by remember { mutableStateOf("") }
    val isBeneficiary = sheet.invitation.roles.contains(TrustedContactRole.Beneficiary)

    LaunchedEffect(sheet.invitation.relationshipId) {
      inviteCodeLoader.getInviteCode(sheet.invitation)
        .logFailure { "failed to load invite code" }
        .onSuccess {
          code = it.inviteCode
        }
    }

    return SheetModel(
      body = ViewingInvitationBodyModel(
        invitation = sheet.invitation,
        isExpired = sheet.invitation.isExpired(clock),
        onRemove = {
          navigator.goTo(
            RemoveTrustedContactScreen(
              account = sheet.account,
              trustedContact = sheet.invitation,
              origin = sheet.origin
            )
          )
        },
        onReinvite = {
          navigator.goTo(
            ReinviteTrustedContactScreen(
              account = sheet.account,
              invitation = sheet.invitation,
              origin = sheet.origin
            )
          )
        },
        onShare = {
          sharingManager.shareInvitation(
            code,
            isBeneficiary = isBeneficiary,
            onCompletion = {
              navigator.closeSheet()
            }
          )
        },
        onBack = navigator::closeSheet
      ),
      onClosed = navigator::closeSheet
    )
  }
}
