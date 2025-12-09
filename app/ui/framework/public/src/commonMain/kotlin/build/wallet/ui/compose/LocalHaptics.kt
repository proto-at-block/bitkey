package build.wallet.ui.compose

import androidx.compose.runtime.compositionLocalOf
import build.wallet.platform.haptics.Haptics

val LocalHaptics = compositionLocalOf<Haptics?> { null }
