package build.wallet.statemachine.dev

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.compose.collections.immutableListOf
import build.wallet.keybox.keys.OnboardingAppKeyKeystore
import build.wallet.platform.config.AppVariant
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel

class OnboardingAppKeyDeletionUiStateMachineImpl(
  private val appVariant: AppVariant,
  private val onboardingAppKeyKeystore: OnboardingAppKeyKeystore,
) : OnboardingAppKeyDeletionUiStateMachine {
  @Composable
  override fun model(props: OnboardingAppKeyDeletionProps): ListGroupModel? {
    // Don't show option in Customer build
    when (appVariant) {
      AppVariant.Customer -> return null
      else -> Unit
    }

    var isDeletingAppKey by remember { mutableStateOf(false) }

    if (isDeletingAppKey) {
      LaunchedEffect("delete-app-key") {
        onboardingAppKeyKeystore.clear()
        isDeletingAppKey = false
      }
    }

    return ListGroupModel(
      style = ListGroupStyle.DIVIDER,
      items =
        immutableListOf(
          ListItemModel(
            title = "Delete Onboarding App Key",
            secondaryText = "Delete the persisted app key, so going through onboarding will generate a new one",
            trailingAccessory =
              ListItemAccessory.ButtonAccessory(
                model =
                  ButtonModel(
                    text = "Delete",
                    isLoading = isDeletingAppKey,
                    size = ButtonModel.Size.Compact,
                    treatment = ButtonModel.Treatment.TertiaryDestructive,
                    onClick = StandardClick {
                      props.onConfirmationRequested {
                        isDeletingAppKey = true
                      }
                    }
                  )
              )
          )
        )
    )
  }
}
