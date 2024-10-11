package build.wallet.ui.compose

import androidx.compose.runtime.Composable
import bitkey.shared.ui_core_public.generated.resources.Res

/**
 * Create a platform specific video resource identifier for
 * the given video file name.
 *
 * @param fileName The video file name without a file extension.
 */
@Composable
expect fun Res.getVideoResource(fileName: String): String
