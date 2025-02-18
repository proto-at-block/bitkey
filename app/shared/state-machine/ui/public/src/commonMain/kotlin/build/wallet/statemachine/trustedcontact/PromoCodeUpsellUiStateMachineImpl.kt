package build.wallet.statemachine.trustedcontact

import androidx.compose.runtime.*
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.clipboard.ClipItem
import build.wallet.platform.clipboard.Clipboard
import build.wallet.platform.links.DeepLinkHandler
import build.wallet.platform.random.uuid
import build.wallet.platform.sharing.SharingManager
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.toast.ToastModel

@BitkeyInject(ActivityScope::class)
class PromoCodeUpsellUiStateMachineImpl(
  private val deepLinkHandler: DeepLinkHandler,
  private val clipboard: Clipboard,
  private val sharingManager: SharingManager,
) : PromoCodeUpsellUiStateMachine {
  @Composable
  override fun model(props: PromoCodeUpsellUiProps): ScreenModel {
    var uiState: UiState by remember { mutableStateOf(UiState.ShowingPromo) }

    return when (val state = uiState) {
      /**
       * Show a form body modal with a header
       */
      UiState.ShowingPromo,
      is UiState.ShowingPromoCopiedCode,
      -> ScreenModel(
        presentationStyle = ScreenPresentationStyle.Modal,
        body = PromoCodeUpsellBodyModel(
          promoCode = props.promoCode,
          treatment = when (props.contactAlias) {
            null -> PromoCodeUpsellBodyModel.Treatment.ForBeneficiary
            else -> PromoCodeUpsellBodyModel.Treatment.ForBenefactor(props.contactAlias)
          },
          onClick = {
            deepLinkHandler.openDeeplink("https://bitkey.world/", null)
          },
          onContinue = {
            uiState = UiState.ShowingPromo
          },
          onCopyCode = {
            clipboard.setItem(ClipItem.PlainText(props.promoCode.value))
            uiState = UiState.ShowingPromoCopiedCode(
              // Generate a random toast id so it will pop back up if
              // the promo code is tapped multiple times
              toastId = uuid()
            )
          },
          onShare = {
            sharingManager.shareText(
              text = props.promoCode.value,
              title = "Bitkey Promo Code",
              completion = {}
            )
          },
          onBack = props.onExit
        ),
        toastModel = ToastModel(
          id = if (state is UiState.ShowingPromoCopiedCode) state.toastId else "",
          title = "Code copied",
          leadingIcon = IconModel(
            icon = Icon.SmallIconCheckFilled,
            iconTint = IconTint.Primary,
            iconSize = IconSize.Accessory
          ),
          iconStrokeColor = ToastModel.IconStrokeColor.Black
        ).takeIf { state is UiState.ShowingPromoCopiedCode }
      )
    }
  }

  private sealed interface UiState {
    data object ShowingPromo : UiState

    data class ShowingPromoCopiedCode(
      val toastId: String,
    ) : UiState
  }
}
