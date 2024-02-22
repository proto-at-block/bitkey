@file:Suppress("TooManyFunctions")

package build.wallet.ui.components.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.LabelTreatment.Disabled
import build.wallet.ui.components.label.LabelTreatment.Primary
import build.wallet.ui.components.label.LabelTreatment.Secondary
import build.wallet.ui.components.label.LabelTreatment.Tertiary
import build.wallet.ui.compose.resId
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Compact
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize.Small
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemAccessoryAlignment.CENTER
import build.wallet.ui.model.list.ListItemAccessoryAlignment.TOP
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemPickerMenu
import build.wallet.ui.model.list.ListItemSideTextTint
import build.wallet.ui.model.list.ListItemTitleAlignment
import build.wallet.ui.model.list.ListItemTreatment.PRIMARY
import build.wallet.ui.model.list.ListItemTreatment.SECONDARY
import build.wallet.ui.model.list.ListItemTreatment.TERTIARY
import build.wallet.ui.model.list.disable
import build.wallet.ui.model.switch.SwitchModel
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme

@Composable
fun ListItem(
  modifier: Modifier = Modifier,
  model: ListItemModel,
) {
  with(model) {
    ListItem(
      modifier = modifier,
      title = AnnotatedString(title),
      contentAlignment =
        when (titleAlignment) {
          ListItemTitleAlignment.LEFT -> Alignment.Start
          ListItemTitleAlignment.CENTER -> Alignment.CenterHorizontally
        },
      titleTreatment =
        when (enabled) {
          true ->
            when (model.treatment) {
              PRIMARY -> Primary
              SECONDARY -> Secondary
              TERTIARY -> Tertiary
            }
          false -> Disabled
        },
      titleType =
        when (model.treatment) {
          PRIMARY -> LabelType.Body2Medium
          SECONDARY -> LabelType.Body2Regular
          TERTIARY -> LabelType.Body3Regular
        },
      secondaryText =
        secondaryText?.let { secondaryText ->
          buildAnnotatedString {
            withStyle(
              style =
                SpanStyle(
                  color =
                    when (model.enabled) {
                      true -> WalletTheme.colors.foreground60
                      false -> WalletTheme.colors.foreground30
                    }
                )
            ) {
              append(secondaryText)
            }
          }
        },
      sideText =
        sideText?.let { sideText ->
          buildAnnotatedString {
            withStyle(
              style =
                SpanStyle(
                  color =
                    when (model.sideTextTint) {
                      ListItemSideTextTint.PRIMARY ->
                        when (enabled) {
                          true -> WalletTheme.colors.foreground
                          false -> WalletTheme.colors.foreground30
                        }

                      ListItemSideTextTint.SECONDARY -> WalletTheme.colors.foreground60

                      ListItemSideTextTint.GREEN -> WalletTheme.colors.positiveForeground
                    }
                )
            ) {
              append(sideText)
            }
          }
        },
      secondarySideText =
        secondarySideText?.let { secondarySideText ->
          buildAnnotatedString {
            withStyle(
              style =
                SpanStyle(
                  color =
                    when (enabled) {
                      true -> WalletTheme.colors.foreground60
                      false -> WalletTheme.colors.foreground30
                    }
                )
            ) {
              append(secondarySideText)
            }
          }
        },
      leadingAccessory =
        when (enabled) {
          true -> leadingAccessory
          false -> leadingAccessory?.disable()
        },
      leadingAccessoryAlignment =
        when (leadingAccessoryAlignment) {
          TOP -> Alignment.Top
          CENTER -> Alignment.CenterVertically
        },
      trailingAccessory =
        when (enabled) {
          true -> trailingAccessory
          false -> trailingAccessory?.disable()
        },
      onClick = onClick,
      pickerMenu = pickerMenu,
      testTag = testTag
    )
  }
}

@Composable
fun ListItem(
  modifier: Modifier = Modifier,
  title: String,
  contentAlignment: Alignment.Horizontal = Alignment.Start,
  titleTreatment: LabelTreatment = Primary,
  titleType: LabelType = LabelType.Body2Medium,
  secondaryText: String? = null,
  sideText: String? = null,
  secondarySideText: String? = null,
  leadingAccessory: ListItemAccessory? = null,
  leadingAccessoryAlignment: Alignment.Vertical = Alignment.CenterVertically,
  trailingAccessory: ListItemAccessory? = null,
  onClick: (() -> Unit)? = null,
  pickerMenu: ListItemPickerMenu<*>? = null,
  testTag: String? = null,
) {
  ListItem(
    modifier = modifier,
    title = AnnotatedString(title),
    contentAlignment = contentAlignment,
    titleTreatment = titleTreatment,
    titleType = titleType,
    secondaryText = secondaryText?.let(::AnnotatedString),
    sideText = sideText?.let(::AnnotatedString),
    secondarySideText = secondarySideText?.let(::AnnotatedString),
    leadingAccessory = leadingAccessory,
    leadingAccessoryAlignment = leadingAccessoryAlignment,
    trailingAccessory = trailingAccessory,
    onClick = onClick,
    pickerMenu = pickerMenu,
    testTag = testTag
  )
}

/**
 * [title] primary text of the item.
 * [secondaryText] secondary text shown under the primary [title].
 * [leadingAccessory] an accessory to show at the start of the item, before primary content.
 * [sideText] primary side text of the item, shown after primary content.
 * [secondarySideText] secondary text shown under [sideText]
 * [trailingAccessory] an accessory to show at the end of the item, after secondary content.
 *
 * |---------------------------------------------------------------------------------|
 * | [leadingAccessory]  [title] [sideText]  [trailingAccessory] |
 * | [secondaryText] [secondarySideText]                      |
 * |---------------------------------------------------------------------------------|
 */
@Composable
fun ListItem(
  modifier: Modifier = Modifier,
  title: AnnotatedString,
  contentAlignment: Alignment.Horizontal = Alignment.Start,
  titleTreatment: LabelTreatment = Primary,
  titleType: LabelType = LabelType.Body2Medium,
  secondaryText: AnnotatedString? = null,
  sideText: AnnotatedString? = null,
  secondarySideText: AnnotatedString? = null,
  leadingAccessory: ListItemAccessory? = null,
  leadingAccessoryAlignment: Alignment.Vertical = Alignment.CenterVertically,
  trailingAccessory: ListItemAccessory? = null,
  onClick: (() -> Unit)? = null,
  pickerMenu: ListItemPickerMenu<*>? = null,
  testTag: String? = null,
) {
  ListItem(
    modifier = modifier,
    onClick = onClick,
    leadingAccessoryContent =
      leadingAccessory?.let {
        {
          ListItemAccessory(model = leadingAccessory)
        }
      },
    leadingAccessoryAlignment = leadingAccessoryAlignment,
    primaryContent = {
      Label(
        text = title,
        treatment = titleTreatment,
        type = titleType
      )
    },
    contentAlignment = contentAlignment,
    secondaryContent =
      secondaryText?.let {
        {
          Label(
            text = secondaryText,
            type = LabelType.Body3Regular
          )
        }
      },
    sideContent =
      sideText?.let {
        {
          Label(
            text = sideText,
            type = titleType,
            alignment = TextAlign.End
          )
        }
      },
    secondarySideContent =
      secondarySideText?.let {
        {
          Label(
            text = secondarySideText,
            type = LabelType.Body3Regular,
            treatment = LabelTreatment.Secondary,
            alignment = TextAlign.End
          )
        }
      },
    trailingAccessoryContent =
      trailingAccessory?.let {
        {
          ListItemAccessory(model = trailingAccessory)
        }
      },
    pickerMenuContent =
      pickerMenu?.takeIf { it.isShowing }?.let {
        {
          ListItemPickerMenu(model = pickerMenu)
        }
      },
    testTag = testTag
  )
}

/**
 * Slot-based implementation of the list item.
 */
@Composable
private fun ListItem(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  leadingAccessoryContent: @Composable (BoxScope.() -> Unit)?,
  leadingAccessoryAlignment: Alignment.Vertical,
  primaryContent: @Composable BoxScope.() -> Unit,
  contentAlignment: Alignment.Horizontal = Alignment.Start,
  secondaryContent: (@Composable BoxScope.() -> Unit)?,
  sideContent: (@Composable BoxScope.() -> Unit)?,
  secondarySideContent: (@Composable BoxScope.() -> Unit)?,
  trailingAccessoryContent: @Composable (BoxScope.() -> Unit)?,
  pickerMenuContent: @Composable (BoxScope.() -> Unit)?,
  testTag: String? = null,
) {
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .resId(testTag)
        .clickable(
          interactionSource = MutableInteractionSource(),
          indication = null,
          enabled = onClick != null,
          onClick = {
            onClick?.invoke()
          }
        )
        .then(modifier)
  ) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(
            vertical = 16.dp
          ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      leadingAccessoryContent?.run {
        Box(modifier = Modifier.align(leadingAccessoryAlignment)) {
          leadingAccessoryContent()
        }
      }
      Column(
        modifier = Modifier.weight(1F),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = contentAlignment
      ) {
        Box { primaryContent() }
        secondaryContent?.let {
          Box { secondaryContent() }
        }
      }
      if (sideContent != null || secondarySideContent != null) {
        Column(
          modifier = Modifier.weight(1F),
          verticalArrangement = Arrangement.spacedBy(2.dp),
          horizontalAlignment = Alignment.End
        ) {
          sideContent?.let {
            Box { sideContent() }
          }
          secondarySideContent?.let {
            Box { secondarySideContent() }
          }
        }
      }
      trailingAccessoryContent?.run {
        Box { trailingAccessoryContent() }
      }
    }
    pickerMenuContent?.run {
      Box { pickerMenuContent() }
    }
  }
}

@Preview
@Composable
internal fun ListItemWithLeadingIconPreview() {
  PreviewWalletTheme {
    ListItem(
      title = "Title",
      secondaryText = "Secondary Text",
      sideText = "Side Text",
      secondarySideText = "Secondary Side Text",
      leadingAccessory =
        ListItemAccessory.IconAccessory(
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
        ListItemAccessory.IconAccessory(
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
internal fun SingleLineListItemPreview() {
  PreviewWalletTheme {
    ListItem(
      title = "Title",
      sideText = "Side Text",
      leadingAccessory =
        ListItemAccessory.IconAccessory(
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
        ListItemAccessory.IconAccessory(
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
internal fun ListItemWithTrailingSwitch() {
  PreviewWalletTheme {
    ListItem(
      title = "Title",
      secondaryText = "Secondary Text",
      sideText = "Side Text",
      secondarySideText = "Secondary Side Text",
      trailingAccessory =
        ListItemAccessory.SwitchAccessory(
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
internal fun ListItemWithTrailingIcon() {
  PreviewWalletTheme {
    ListItem(
      title = "Title",
      secondaryText = "Secondary Text",
      sideText = "Side Text",
      secondarySideText = "Secondary Side Text",
      trailingAccessory = ListItemAccessory.IconAccessory(icon = Icon.SmallIconCheckFilled),
      onClick = {}
    )
  }
}

@Preview
@Composable
internal fun ListItemWithTrailingButton() {
  PreviewWalletTheme {
    ListItem(
      title = "Title",
      secondaryText = "Secondary Text",
      sideText = "Side Text",
      secondarySideText = "Secondary Side Text",
      trailingAccessory =
        ListItemAccessory.ButtonAccessory(
          model =
            ButtonModel(
              text = "Text",
              leadingIcon = Icon.SmallIconCheckFilled,
              size = Compact,
              onClick = Click.StandardClick { }
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
          trailingAccessory = ListItemAccessory.IconAccessory(icon = Icon.SmallIconCheckFilled),
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
          trailingAccessory = ListItemAccessory.IconAccessory(icon = Icon.SmallIconCheckFilled),
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
          trailingAccessory = ListItemAccessory.IconAccessory(icon = Icon.SmallIconCheckFilled),
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
        ListItemAccessory.ButtonAccessory(
          model =
            ButtonModel(
              text = "Text",
              leadingIcon = Icon.SmallIconCheckFilled,
              size = Compact,
              onClick = Click.StandardClick { }
            )
        ),
      onClick = {}
    )
  }
}
