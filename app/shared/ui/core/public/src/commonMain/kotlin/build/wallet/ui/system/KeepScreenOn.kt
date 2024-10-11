package build.wallet.ui.system

import androidx.compose.runtime.Composable

/** Prevents screen from dimming/turning off from inactivity. */
@Composable
expect fun KeepScreenOn()
