package build.wallet.feature

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.map

/**
 * Collects a boolean feature flag's enabled state as Compose [State].
 */
@Composable
fun FeatureFlag<FeatureFlagValue.BooleanFlag>.collectIsEnabledAsState(): State<Boolean> =
  remember(this) { flagValue().map { it.isEnabled() } }
    .collectAsState(initial = isEnabled())
