package build.wallet.statemachine.status

import build.wallet.ui.model.status.BannerStyle
import build.wallet.ui.model.status.StatusBannerModel

val StatusBannerModelMock =
  StatusBannerModel(
    title = "Title",
    subtitle = "Subtitle",
    style = BannerStyle.Warning,
    onClick = {}
  )
