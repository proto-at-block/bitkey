package build.wallet.statemachine.dev.featureFlags

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagService
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.FeatureFlagValue.BooleanFlag
import build.wallet.statemachine.core.ScreenModel
import build.wallet.ui.model.alert.AlertModel
import build.wallet.ui.model.alert.ButtonAlertModel
import build.wallet.ui.model.alert.InputAlertModel
import build.wallet.ui.model.input.TextFieldModel.KeyboardType
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@BitkeyInject(ActivityScope::class)
class FeatureFlagsStateMachineImpl(
  private val booleanFlagItemUiStateMachine: BooleanFlagItemUiStateMachine,
  private val doubleFlagItemUiStateMachine: DoubleFlagItemUiStateMachine,
  private val stringFlagItemUiStateMachine: StringFlagItemUiStateMachine,
  private val featureFlagService: FeatureFlagService,
) : FeatureFlagsStateMachine {
  @Composable
  override fun model(props: FeatureFlagsProps): ScreenModel {
    var alert: AlertModel? by remember { mutableStateOf(null) }
    var resettingFlags: Boolean by remember { mutableStateOf(false) }
    val scope = rememberStableCoroutineScope()

    if (resettingFlags) {
      LaunchedEffect("reset-feature-flags") {
        featureFlagService.resetFlags()
        resettingFlags = false
      }
    }

    val featureFlags = featureFlagService.getFeatureFlags()

    return FeatureFlagsBodyModel(
      flagsModel =
        ListGroupModel(
          style = ListGroupStyle.DIVIDER,
          items = featureFlags.mapNotNull { flag ->
            when (flag.defaultFlagValue) {
              is BooleanFlag -> booleanFlagItemUiStateMachine.model(
                props =
                  BooleanFlagItemUiProps(
                    featureFlag = flag as FeatureFlag<BooleanFlag>,
                    onShowAlertMessage = { alertMessage ->
                      alert =
                        ButtonAlertModel(
                          title = "Unable to set feature flag",
                          subline = alertMessage,
                          onDismiss = { alert = null },
                          primaryButtonText = "OK",
                          onPrimaryButtonClick = { alert = null }
                        )
                    }
                  )
              )
              is FeatureFlagValue.DoubleFlag -> doubleFlagItemUiStateMachine.model(
                props =
                  DoubleFlagItemUiProps(
                    featureFlag = flag as FeatureFlag<FeatureFlagValue.DoubleFlag>,
                    onClick = {
                      alert =
                        InputAlertModel(
                          title = flag.title,
                          subline = flag.description,
                          value = flag.flagValue().value.value.toString(),
                          onDismiss = { alert = null },
                          onConfirm = {
                            val updatedValue = it.trim().toDoubleOrNull()
                            if (updatedValue != null) {
                              scope.launch {
                                flag.setFlagValue(
                                  FeatureFlagValue.DoubleFlag(updatedValue),
                                  overridden = true
                                )
                              }
                            }
                            alert = null
                          },
                          onCancel = { alert = null },
                          keyboardType = KeyboardType.Decimal
                        )
                    }
                  )
              )
              is FeatureFlagValue.StringFlag -> stringFlagItemUiStateMachine.model(
                props =
                  StringFlagItemUiProps(
                    featureFlag = flag as FeatureFlag<FeatureFlagValue.StringFlag>,
                    onClick = {
                      alert =
                        InputAlertModel(
                          title = flag.title,
                          subline = flag.description,
                          value = flag.flagValue().value.value,
                          onDismiss = { alert = null },
                          onConfirm = {
                            scope.launch {
                              flag.setFlagValue(
                                FeatureFlagValue.StringFlag(it.trim()),
                                overridden = true
                              )
                            }

                            alert = null
                          },
                          onCancel = { alert = null }
                        )
                    }
                  )
              )
              else -> null
            }
          }
            .toImmutableList()
        ),
      onBack = props.onBack,
      onReset = { resettingFlags = true }
    ).asModalScreen(alertModel = alert)
  }
}
