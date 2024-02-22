package build.wallet.statemachine.dev.featureFlags

import androidx.compose.runtime.Composable
import build.wallet.compose.collections.immutableListOf
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Beta
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.platform.config.AppVariant.Development
import build.wallet.platform.config.AppVariant.Emergency
import build.wallet.platform.config.AppVariant.Team
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel

class FeatureFlagsOptionsUiStateMachineImpl(
  private val appVariant: AppVariant,
) : FeatureFlagsOptionsUiStateMachine {
  @Composable
  override fun model(props: FeatureFlagsOptionsUiProps): ListGroupModel? {
    // Only show this option in development or team builds.
    return when (appVariant) {
      Beta, Emergency, Customer -> null
      Development, Team ->
        ListGroupModel(
          style = ListGroupStyle.DIVIDER,
          items =
            immutableListOf(
              ListItemModel(
                title = "Feature Flags",
                trailingAccessory = ListItemAccessory.drillIcon(),
                onClick = props.onShowFeatureFlags
              )
            )
        )
    }
  }
}
