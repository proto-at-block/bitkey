package build.wallet.statemachine.data.lightning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import build.wallet.bitcoin.lightning.LightningPreference
import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagValue.BooleanFlag
import build.wallet.ldk.LdkNodeService
import build.wallet.logging.log
import build.wallet.logging.logFailure
import build.wallet.statemachine.data.lightning.LightningNodeData.LightningNodeDisabledData
import build.wallet.statemachine.data.lightning.LightningNodeData.LightningNodeRunningData

class LightningNodeDataStateMachineImpl(
  private val lightningIsAvailableFeatureFlag: FeatureFlag<BooleanFlag>,
  private val lightningPreference: LightningPreference,
  private val ldkNodeService: LdkNodeService,
) : LightningNodeDataStateMachine {
  @Composable
  override fun model(props: Unit): LightningNodeData {
    return when (rememberLightningNodeEnabled()) {
      true -> {
        LaunchedEffect("start-lightning-node") {
          startLightningNode()
        }

        LightningNodeRunningData
      }

      false -> LightningNodeDisabledData
    }
  }

  private suspend fun startLightningNode() {
    if (lightningPreference.get()) {
      ldkNodeService.start()
        .onSuccess {
          ldkNodeService.connectToLsp()
            .onSuccess { log { "DEBUG:: Connected to LSP" } }
        }
        .result
        .logFailure { "Error creating LDK node" }
    }
  }

  @Composable
  private fun rememberLightningNodeEnabled(): Boolean {
    return remember { lightningIsAvailableFeatureFlag.flagValue() }.collectAsState().value.value
  }
}
