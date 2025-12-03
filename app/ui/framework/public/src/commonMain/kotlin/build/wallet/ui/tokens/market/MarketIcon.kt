package build.wallet.ui.tokens.market

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Immutable
public data class MarketIcon internal constructor(
  val resource: DrawableResource,
  val multiColor: Boolean = false,
) {
  override fun toString(): String = "MarketIcon"
}

@Composable
public fun MarketIcon.painter() = painterResource(resource)
