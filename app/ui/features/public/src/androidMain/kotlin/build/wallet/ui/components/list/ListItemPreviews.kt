@file:Suppress("TooManyFunctions")

package build.wallet.ui.components.list

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.core.Icon
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Compact
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize.Small
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.CoachmarkLabelModel
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemTitleBackgroundTreatment
import build.wallet.ui.model.list.ListItemTreatment.PRIMARY_TITLE
import build.wallet.ui.model.list.ListItemTreatment.SECONDARY
import build.wallet.ui.model.switch.SwitchModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun ListItemWithLeadingIconPreview() {
  PreviewWalletTheme {
    ListItem(
      title = "Title",
      secondaryText = "Secondary Text",
      sideText = "Side Text",
      secondarySideText = "Secondary Side Text",
      leadingAccessory =
        build.wallet.ui.model.list.ListItemAccessory.IconAccessory(
          model =
            IconModel(
              icon = Icon.SmallIconCheckFilled,
              iconSize = Small
            )
        ),
      onClick = {}
    )
  }
}

@Preview
@Composable
internal fun ListItemWithLeadingIconTopAlignedPreview() {
  PreviewWalletTheme {
    ListItem(
      title = "Title",
      secondaryText = "Secondary Text",
      sideText = "Side Text",
      secondarySideText = "Secondary Side Text",
      leadingAccessory =
        build.wallet.ui.model.list.ListItemAccessory.IconAccessory(
          model =
            IconModel(
              icon = Icon.SmallIconCheckFilled,
              iconSize = Small
            )
        ),
      leadingAccessoryAlignment = Alignment.Top,
      onClick = {}
    )
  }
}

@Preview
@Composable
internal fun ListItemWithSpecialTrailingAccessoryPreview() {
  PreviewWalletTheme {
    ListItem(
      title = "Title",
      leadingAccessory =
        build.wallet.ui.model.list.ListItemAccessory.IconAccessory(
          model =
            IconModel(
              icon = Icon.SmallIconCloud,
              iconSize = Small
            )
        ),
      trailingAccessory = build.wallet.ui.model.list.ListItemAccessory.drillIcon(),
      specialTrailingAccessory = build.wallet.ui.model.list.ListItemAccessory.IconAccessory(
        model = IconModel(
          icon = Icon.SmallIconInformationFilled,
          iconSize = Small,
          iconTint = IconTint.Warning
        )
      ),
      onClick = {}
    )
  }
}

@Preview
@Composable
internal fun ListItemWithNewCoachmark() {
  PreviewWalletTheme {
    ListItem(
      title = "Title",
      leadingAccessory =
        build.wallet.ui.model.list.ListItemAccessory.IconAccessory(
          model =
            IconModel(
              icon = Icon.SmallIconCloud,
              iconSize = Small
            )
        ),
      coachmarkLabel = CoachmarkLabelModel.New,
      trailingAccessory = build.wallet.ui.model.list.ListItemAccessory.drillIcon(),
      specialTrailingAccessory = build.wallet.ui.model.list.ListItemAccessory.IconAccessory(
        model = IconModel(
          icon = Icon.SmallIconInformationFilled,
          iconSize = Small,
          iconTint = IconTint.Warning
        )
      ),
      onClick = {}
    )
  }
}

@Preview
@Composable
fun SingleLineListItemPreview() {
  PreviewWalletTheme {
    ListItem(
      title = "Title",
      sideText = "Side Text",
      leadingAccessory =
        build.wallet.ui.model.list.ListItemAccessory.IconAccessory(
          model =
            IconModel(
              icon = Icon.SmallIconCheckFilled,
              iconSize = Small
            )
        ),
      onClick = {}
    )
  }
}

@Preview
@Composable
private fun BitcoinTransactionItemPreview() {
  PreviewWalletTheme {
    ListItem(
      title = "Ma3Y...D2pX",
      secondaryText = "3 hours ago",
      sideText = " + $20.00",
      secondarySideText = "0.00017 BTC",
      leadingAccessory =
        build.wallet.ui.model.list.ListItemAccessory.IconAccessory(
          model =
            IconModel(
              icon = Icon.Bitcoin,
              iconSize = Small
            )
        ),
      onClick = {}
    )
  }
}

@Preview
@Composable
fun ListItemWithTrailingSwitch() {
  PreviewWalletTheme {
    ListItem(
      title = "Title",
      secondaryText = "Secondary Text",
      sideText = "Side Text",
      secondarySideText = "Secondary Side Text",
      trailingAccessory =
        build.wallet.ui.model.list.ListItemAccessory.SwitchAccessory(
          model =
            SwitchModel(
              checked = true,
              onCheckedChange = {}
            )
        ),
      onClick = {}
    )
  }
}

@Preview
@Composable
fun ListItemWithTrailingIcon() {
  PreviewWalletTheme {
    ListItem(
      title = "Title",
      secondaryText = "Secondary Text",
      sideText = "Side Text",
      secondarySideText = "Secondary Side Text",
      trailingAccessory = build.wallet.ui.model.list.ListItemAccessory.IconAccessory(icon = Icon.SmallIconCheckFilled),
      onClick = {}
    )
  }
}

@Preview
@Composable
fun ListItemWithTrailingButton() {
  PreviewWalletTheme {
    ListItem(
      title = "Title",
      secondaryText = "Secondary Text",
      sideText = "Side Text",
      secondarySideText = "Secondary Side Text",
      trailingAccessory =
        build.wallet.ui.model.list.ListItemAccessory.ButtonAccessory(
          model =
            ButtonModel(
              text = "Text",
              leadingIcon = Icon.SmallIconCheckFilled,
              size = Compact,
              onClick = StandardClick {}
            )
        ),
      onClick = {}
    )
  }
}

@Preview
@Composable
internal fun ListItemDisabled() {
  PreviewWalletTheme {
    ListItem(
      model =
        ListItemModel(
          title = "Title",
          secondaryText = "Secondary Text",
          sideText = "Side Text",
          secondarySideText = "Secondary Side Text",
          trailingAccessory = build.wallet.ui.model.list.ListItemAccessory.IconAccessory(icon = Icon.SmallIconCheckFilled),
          enabled = false
        )
    )
  }
}

@Preview
@Composable
internal fun ListItemSecondary() {
  PreviewWalletTheme {
    ListItem(
      model =
        ListItemModel(
          title = "Title",
          secondaryText = "Secondary Text",
          sideText = "Side Text",
          secondarySideText = "Secondary Side Text",
          trailingAccessory = build.wallet.ui.model.list.ListItemAccessory.IconAccessory(icon = Icon.SmallIconCheckFilled),
          treatment = SECONDARY
        )
    )
  }
}

@Preview
@Composable
internal fun ListItemSecondaryDisabled() {
  PreviewWalletTheme {
    ListItem(
      model =
        ListItemModel(
          title = "Title",
          secondaryText = "Secondary Text",
          sideText = "Side Text",
          secondarySideText = "Secondary Side Text",
          trailingAccessory = build.wallet.ui.model.list.ListItemAccessory.IconAccessory(icon = Icon.SmallIconCheckFilled),
          treatment = SECONDARY,
          enabled = false
        )
    )
  }
}

@Preview
@Composable
internal fun ListItemWithTrailingAndLongSecondaryPreview() {
  PreviewWalletTheme {
    ListItem(
      title = "Title",
      secondaryText = "Secondary Text Secondary Text Secondary Text Secondary Text Secondary Text Secondary Text Secondary Text Secondary Text Secondary Text",
      trailingAccessory =
        build.wallet.ui.model.list.ListItemAccessory.ButtonAccessory(
          model =
            ButtonModel(
              text = "Text",
              leadingIcon = Icon.SmallIconCheckFilled,
              size = Compact,
              onClick = StandardClick {}
            )
        ),
      onClick = {}
    )
  }
}

@Preview
@Composable
internal fun ListItemRecoveryCode() {
  PreviewWalletTheme {
    ListItem(
      model =
        ListItemModel(
          title = "1234-ABCD-EF",
          treatment = PRIMARY_TITLE,
          listItemTitleBackgroundTreatment = ListItemTitleBackgroundTreatment.RECOVERY
        )
    )
  }
}
