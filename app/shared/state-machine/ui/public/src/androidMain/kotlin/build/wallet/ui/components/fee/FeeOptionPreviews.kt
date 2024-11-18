package build.wallet.ui.components.fee

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
fun PreviewFeeOptionSelected() {
  FeeOption(
    modifier = Modifier.fillMaxWidth(),
    leadingText = "Priority",
    trailingPrimaryText = "~30 mins",
    trailingSecondaryText = "$0.33 (1,086 sats)",
    selected = true,
    enabled = true,
    infoText = "",
    onClick = {}
  )
}

@Preview
@Composable
fun PreviewFeeOptionNotSelected() {
  FeeOption(
    modifier = Modifier.fillMaxWidth(),
    leadingText = "Priority",
    trailingPrimaryText = "~30 mins",
    trailingSecondaryText = "$0.33 (1,086 sats)",
    selected = false,
    enabled = true,
    infoText = "",
    onClick = {}
  )
}

@Preview
@Composable
fun PreviewFeeOptionDisabled() {
  FeeOption(
    modifier = Modifier.fillMaxWidth(),
    leadingText = "Priority",
    trailingPrimaryText = "~30 mins",
    trailingSecondaryText = "$0.33 (1,086 sats)",
    selected = false,
    enabled = false,
    infoText = "Not enough balance",
    onClick = {}
  )
}

@Preview
@Composable
internal fun PreviewFeeOptionEnabledWithInfoText() {
  FeeOption(
    modifier = Modifier.fillMaxWidth(),
    leadingText = "Priority",
    trailingPrimaryText = "~30 mins",
    trailingSecondaryText = "$0.33 (1,086 sats)",
    selected = false,
    enabled = true,
    infoText = "All fees are equal – we’ve selected the fastest option for you",
    onClick = {}
  )
}
