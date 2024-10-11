@file:Suppress("TooManyFunctions")

package build.wallet.ui.components.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.ui.components.coachmark.CoachmarkPresenter
import build.wallet.ui.components.coachmark.NewCoachmark
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.LabelTreatment.*
import build.wallet.ui.components.layout.CollapsedMoneyView
import build.wallet.ui.components.layout.CollapsibleLabelContainer
import build.wallet.ui.compose.resId
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Compact
import build.wallet.ui.model.coachmark.CoachmarkModel
import build.wallet.ui.model.coachmark.NewCoachmarkTreatment
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize.Small
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.*
import build.wallet.ui.model.list.ListItemAccessoryAlignment.CENTER
import build.wallet.ui.model.list.ListItemAccessoryAlignment.TOP
import build.wallet.ui.model.list.ListItemTreatment.*
import build.wallet.ui.model.switch.SwitchModel
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun ListItem(
  modifier: Modifier = Modifier,
  model: ListItemModel,
  collapseContent: Boolean = false,
) {
  with(model) {
    val sideTextValue: AnnotatedString? = model.sideText()
    val secondarySideTextValue: AnnotatedString? = model.secondarySideText()

    ListItem(
      modifier = modifier,
      title = AnnotatedString(title),
      titleLabel = titleLabel,
      contentAlignment =
        when (titleAlignment) {
          ListItemTitleAlignment.LEFT -> Alignment.Start
          ListItemTitleAlignment.CENTER -> Alignment.CenterHorizontally
        },
      titleTreatment =
        when (enabled) {
          true ->
            when (treatment) {
              PRIMARY -> Primary
              SECONDARY -> Secondary
              TERTIARY -> Tertiary
              PRIMARY_TITLE -> Jumbo
              SECONDARY_DISPLAY -> Jumbo
            }
          false -> Disabled
        },
      titleType =
        when (model.treatment) {
          PRIMARY -> LabelType.Body2Medium
          SECONDARY -> LabelType.Body2Regular
          TERTIARY -> LabelType.Body3Regular
          PRIMARY_TITLE -> LabelType.Title1
          SECONDARY_DISPLAY -> LabelType.Display2
        },
      listItemTitleBackgroundTreatment = listItemTitleBackgroundTreatment,
      secondaryText =
        secondaryText?.let { secondaryText ->
          val textColor = when (enabled) {
            true -> WalletTheme.colors.foreground60
            false -> WalletTheme.colors.foreground30
          }
          AnnotatedString(secondaryText, SpanStyle(color = textColor))
        },
      secondaryTextType =
        when (model.treatment) {
          SECONDARY_DISPLAY -> LabelType.Body1Regular
          else -> LabelType.Body3Regular
        },
      sideText = sideTextValue,
      secondarySideText = secondarySideTextValue,
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
      specialTrailingAccessory = specialTrailingAccessory,
      onClick = onClick,
      pickerMenu = pickerMenu,
      collapseContent = collapseContent,
      testTag = testTag,
      coachmark = model.coachmark,
      showNewCoachmark = model.showNewCoachmark
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
  listItemTitleBackgroundTreatment: ListItemTitleBackgroundTreatment? = null,
  secondaryText: String? = null,
  sideText: String? = null,
  secondarySideText: String? = null,
  leadingAccessory: ListItemAccessory? = null,
  leadingAccessoryAlignment: Alignment.Vertical = Alignment.CenterVertically,
  trailingAccessory: ListItemAccessory? = null,
  onClick: (() -> Unit)? = null,
  pickerMenu: ListItemPickerMenu<*>? = null,
  testTag: String? = null,
  titleLabel: LabelModel? = null,
  specialTrailingAccessory: ListItemAccessory? = null,
  coachmark: CoachmarkModel? = null,
  showNewCoachmark: Boolean = false,
) {
  ListItem(
    modifier = modifier,
    title = AnnotatedString(title),
    contentAlignment = contentAlignment,
    titleTreatment = titleTreatment,
    titleType = titleType,
    listItemTitleBackgroundTreatment = listItemTitleBackgroundTreatment,
    secondaryText = secondaryText?.let(::AnnotatedString),
    sideText = sideText?.let(::AnnotatedString),
    secondarySideText = secondarySideText?.let(::AnnotatedString),
    leadingAccessory = leadingAccessory,
    leadingAccessoryAlignment = leadingAccessoryAlignment,
    trailingAccessory = trailingAccessory,
    specialTrailingAccessory = specialTrailingAccessory,
    onClick = onClick,
    pickerMenu = pickerMenu,
    testTag = testTag,
    titleLabel = titleLabel,
    coachmark = coachmark,
    showNewCoachmark = showNewCoachmark
  )
}

/**
 * [title] primary text of the item.
 * [secondaryText] secondary text shown under the primary [title].
 * [leadingAccessory] an accessory to show at the start of the item, before primary content.
 * [sideText] primary side text of the item, shown after primary content.
 * [secondarySideText] secondary text shown under [sideText]
 * [trailingAccessory] an accessory to show at the end of the item, after secondary content.
 * [specialTrailingAccessory] an accessory to show just before [trailingAccessory]
 *
 * |---------------------------------------------------------------------------------|
 * | [leadingAccessory]  [title] [sideText] [specialTrailingAccessory] [trailingAccessory] |
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
  listItemTitleBackgroundTreatment: ListItemTitleBackgroundTreatment? = null,
  secondaryText: AnnotatedString? = null,
  secondaryTextType: LabelType = LabelType.Body3Regular,
  sideText: AnnotatedString? = null,
  secondarySideText: AnnotatedString? = null,
  leadingAccessory: ListItemAccessory? = null,
  leadingAccessoryAlignment: Alignment.Vertical = Alignment.CenterVertically,
  trailingAccessory: ListItemAccessory? = null,
  specialTrailingAccessory: ListItemAccessory? = null,
  onClick: (() -> Unit)? = null,
  pickerMenu: ListItemPickerMenu<*>? = null,
  testTag: String? = null,
  titleLabel: LabelModel? = null,
  collapseContent: Boolean = false,
  coachmark: CoachmarkModel?,
  showNewCoachmark: Boolean = false,
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
      Box(
        modifier = listItemTitleBackgroundTreatment?.let {
          when (it) {
            ListItemTitleBackgroundTreatment.RECOVERY ->
              Modifier
                .background(WalletTheme.colors.foreground10, RoundedCornerShape(12.dp))
                .fillMaxWidth()
                .padding(16.dp)
          }
        } ?: modifier,
        contentAlignment = listItemTitleBackgroundTreatment?.let {
          when (it) {
            ListItemTitleBackgroundTreatment.RECOVERY ->
              Alignment.Center
          }
        } ?: Alignment.TopStart
      ) {
        Row {
          if (titleLabel == null) {
            Label(
              text = title,
              treatment = titleTreatment,
              type = titleType
            )
          } else {
            Label(
              model = titleLabel,
              treatment = titleTreatment,
              type = titleType
            )
          }

          if (showNewCoachmark) {
            Spacer(Modifier.width(8.dp))
            NewCoachmark(
              if (titleTreatment == Disabled) {
                NewCoachmarkTreatment.Disabled
              } else {
                NewCoachmarkTreatment.Light
              }
            )
          }
        }
      }
    },
    contentAlignment = contentAlignment,
    secondaryContent =
      secondaryText?.let {
        {
          Label(
            text = secondaryText,
            type = secondaryTextType
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
    specialTrailingAccessoryContent =
      specialTrailingAccessory?.let {
        {
          ListItemAccessory(model = specialTrailingAccessory)
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
    collapseContent = collapseContent,
    testTag = testTag,
    coachmark = coachmark
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
  specialTrailingAccessoryContent: @Composable (BoxScope.() -> Unit)?,
  pickerMenuContent: @Composable (BoxScope.() -> Unit)?,
  coachmark: CoachmarkModel?,
  collapseContent: Boolean = false,
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
    Column {
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
          CollapsibleLabelContainer(
            modifier = Modifier.weight(1F),
            collapsed = collapseContent,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.End,
            topContent = sideContent?.let {
              { Box { sideContent() } }
            },
            bottomContent = secondarySideContent?.let {
              { Box { secondarySideContent() } }
            },
            collapsedContent = {
              Box {
                CollapsedMoneyView(
                  height = 16.dp,
                  modifier = Modifier.align(Alignment.Center)
                )
              }
            }
          )
        }
        specialTrailingAccessoryContent?.run {
          Box { specialTrailingAccessoryContent() }
        }
        trailingAccessoryContent?.run {
          Box { trailingAccessoryContent() }
        }
      }
      pickerMenuContent?.run {
        Box { pickerMenuContent() }
      }

      coachmark?.let {
        CoachmarkPresenter(yOffset = 0f, model = coachmark)
      }
    }
  }
}

@Composable
private fun ListItemModel.sideText(): AnnotatedString? =
  sideText?.let { sideText ->
    val textColor = when (sideTextTint) {
      ListItemSideTextTint.PRIMARY -> when (enabled) {
        true -> WalletTheme.colors.foreground
        false -> WalletTheme.colors.foreground30
      }

      ListItemSideTextTint.SECONDARY -> WalletTheme.colors.foreground60

      ListItemSideTextTint.GREEN -> WalletTheme.colors.positiveForeground
    }
    AnnotatedString(sideText, SpanStyle(color = textColor))
  }

@Composable
private fun ListItemModel.secondarySideText(): AnnotatedString? =
  secondarySideText?.let { secondarySideText ->
    val textColor = when (enabled) {
      true -> WalletTheme.colors.foreground60
      false -> WalletTheme.colors.foreground30
    }
    AnnotatedString(secondarySideText, SpanStyle(color = textColor))
  }

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
internal fun ListItemWithSpecialTrailingAccessoryPreview() {
  PreviewWalletTheme {
    ListItem(
      title = "Title",
      leadingAccessory =
        ListItemAccessory.IconAccessory(
          model =
            IconModel(
              icon = Icon.SmallIconCloud,
              iconSize = Small
            )
        ),
      trailingAccessory = ListItemAccessory.drillIcon(),
      specialTrailingAccessory = ListItemAccessory.IconAccessory(
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
        ListItemAccessory.IconAccessory(
          model =
            IconModel(
              icon = Icon.SmallIconCloud,
              iconSize = Small
            )
        ),
      showNewCoachmark = true,
      trailingAccessory = ListItemAccessory.drillIcon(),
      specialTrailingAccessory = ListItemAccessory.IconAccessory(
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
fun ListItemWithTrailingSwitch() {
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
fun ListItemWithTrailingIcon() {
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
fun ListItemWithTrailingButton() {
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
