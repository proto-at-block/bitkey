package build.wallet.statemachine.dev.analytics

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

class AnalyticsOptionsUiStateMachineImpl(
  private val appVariant: AppVariant,
) : AnalyticsOptionsUiStateMachine {
  @Composable
  override fun model(props: AnalyticsOptionsUiProps): ListGroupModel? {
    // Only show this option in development or team builds.
    return when (appVariant) {
      Beta, Customer, Emergency -> null
      Development, Team ->
        ListGroupModel(
          style = ListGroupStyle.DIVIDER,
          items =
            immutableListOf(
              ListItemModel(
                title = "Analytics",
                trailingAccessory = ListItemAccessory.drillIcon(),
                onClick = props.onShowAnalytics
              )
            )
        )
    }
  }
}
