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
import build.wallet.recovery.socrec.InviteCodeLoadError
import build.wallet.recovery.socrec.InviteCodeLoader
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.trustedcontact.view.ViewingInvitationBodyModel
import com.github.michaelbull.result.onFailure
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
    var codeState: CodeState by remember { mutableStateOf(CodeState.Loading) }
    val isBeneficiary = sheet.invitation.roles.contains(TrustedContactRole.Beneficiary)

    LaunchedEffect(sheet.invitation.id) {
      codeState = CodeState.Loading
      inviteCodeLoader.getInviteCode(sheet.invitation)
        .logFailure { "failed to load invite code" }
        .onSuccess { codeState = CodeState.Loaded(it.inviteCode) }
        .onFailure { error ->
          // Only the "PAKE data is gone" failure makes this invite genuinely unsharable;
          // other failures (storage hiccups, encoding bugs) shouldn't push the user toward
          // removing an otherwise-valid invitation.
          codeState = if (error is InviteCodeLoadError.MissingPakeData) {
            CodeState.Missing
          } else {
            CodeState.Loading
          }
        }
    }

    val currentCodeState = codeState
    return SheetModel(
      body = ViewingInvitationBodyModel(
        invitation = sheet.invitation,
        isExpired = sheet.invitation.isExpired(clock),
        isCodeMissing = currentCodeState is CodeState.Missing,
        isCodeLoading = currentCodeState is CodeState.Loading,
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
          if (currentCodeState is CodeState.Loaded) {
            sharingManager.shareInvitation(
              currentCodeState.code,
              isBeneficiary = isBeneficiary,
              onCompletion = {
                navigator.closeSheet()
              }
            )
          }
        },
        onBack = navigator::closeSheet
      ),
      onClosed = navigator::closeSheet
    )
  }

  private sealed interface CodeState {
    data object Loading : CodeState

    data class Loaded(val code: String) : CodeState

    data object Missing : CodeState
  }
}
