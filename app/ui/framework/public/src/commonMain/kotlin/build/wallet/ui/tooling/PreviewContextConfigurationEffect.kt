package build.wallet.ui.tooling

import androidx.compose.runtime.Composable

/**
 * Initialize Android specific code for Compose Multiplatform resource access.
 * This will no-op for all other platforms.
 */
@Composable
internal expect fun PreviewContextConfigurationEffect()
