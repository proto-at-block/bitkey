package build.wallet.ui.components.coachmark

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.coachmark.CoachmarkIdentifier
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.coachmark.CoachmarkModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
internal fun CoachmarkPreviews() {
  PreviewWalletTheme {
    Coachmark(
      model = CoachmarkModel(
        identifier = CoachmarkIdentifier.MultipleFingerprintsCoachmark,
        title = "Multiple fingerprints",
        description = "Now you can add more fingerprints to your Bitkey device.",
        arrowPosition = CoachmarkModel.ArrowPosition(
          vertical = CoachmarkModel.ArrowPosition.Vertical.Top,
          horizontal = CoachmarkModel.ArrowPosition.Horizontal.Trailing
        ),
        button = ButtonModel(
          text = "Add fingerprints",
          size = ButtonModel.Size.Footer,
          onClick = StandardClick {}
        ),
        image = null,
        dismiss = {}
      ),
      offset = Offset(0f, 0f)
    )
  }
}
