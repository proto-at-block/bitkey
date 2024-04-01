package build.wallet.statemachine.dev.featureFlags

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagSyncer
import build.wallet.feature.FeatureFlagValue.BooleanFlag
import build.wallet.statemachine.core.ScreenModel
import build.wallet.ui.model.alert.AlertModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import kotlinx.collections.immutable.toImmutableList

class FeatureFlagsStateMachineImpl(
  private val allBooleanFeatureFlags: List<FeatureFlag<BooleanFlag>>,
  private val booleanFlagItemUiStateMachine: BooleanFlagItemUiStateMachine,
  private val featureFlagSyncer: FeatureFlagSyncer,
) : FeatureFlagsStateMachine {
  @Composable
  override fun model(props: FeatureFlagsProps): ScreenModel {
    var alert: AlertModel? by remember { mutableStateOf(null) }
    var resettingFlags: Boolean by remember { mutableStateOf(false) }

    if (resettingFlags) {
      ResetFlagsEffect {
        resettingFlags = false
      }
    }

    return FeatureFlagsBodyModel(
      flagsModel =
        ListGroupModel(
          style = ListGroupStyle.DIVIDER,
          items =
            allBooleanFeatureFlags.map { booleanFeatureFlag ->
              booleanFlagItemUiStateMachine.model(
                props =
                  BooleanFlagItemUiProps(
                    featureFlag = booleanFeatureFlag,
                    onShowAlertMessage = { alertMessage ->
                      alert =
                        AlertModel(
                          title = "Unable to set feature flag",
                          subline = alertMessage,
                          onDismiss = { alert = null },
                          primaryButtonText = "OK",
                          onPrimaryButtonClick = { alert = null }
                        )
                    }
                  )
              )
            }.toImmutableList()
        ),
      onBack = props.onBack,
      onReset = { resettingFlags = true }
    ).asModalScreen(alertModel = alert)
  }

  @Composable
  fun ResetFlagsEffect(onComplete: () -> Unit) {
    LaunchedEffect("reset-feature-flags") {
      for (flag in allBooleanFeatureFlags) {
        flag.reset()
      }

      featureFlagSyncer.sync()

      onComplete()
    }
  }
}
