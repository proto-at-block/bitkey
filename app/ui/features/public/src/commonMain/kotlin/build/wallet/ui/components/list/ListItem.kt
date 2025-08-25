package build.wallet.ui.components.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.LabelModel
import build.wallet.ui.components.coachmark.CoachmarkPresenter
import build.wallet.ui.components.coachmark.NewCoachmark
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.LabelTreatment.*
import build.wallet.ui.components.layout.CollapsedMoneyView
import build.wallet.ui.components.layout.CollapsibleLabelContainer
import build.wallet.ui.compose.resId
import build.wallet.ui.model.coachmark.CoachmarkModel
import build.wallet.ui.model.coachmark.NewCoachmarkTreatment
import build.wallet.ui.model.list.*
import build.wallet.ui.model.list.ListItemAccessoryAlignment.CENTER
import build.wallet.ui.model.list.ListItemAccessoryAlignment.TOP
import build.wallet.ui.model.list.ListItemTreatment.*
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

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
      listItemTreatment = model.treatment,
      title = AnnotatedString(title),
      titleLabel = titleLabel,
      allowFontScaling = allowFontScaling,
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
              INFO -> Secondary
              TERTIARY -> Tertiary
              QUATERNARY -> Quaternary
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
          QUATERNARY -> LabelType.Label3
          PRIMARY_TITLE -> LabelType.Title1
          SECONDARY_DISPLAY -> LabelType.Display2
          INFO -> LabelType.Body4Regular
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
      topAccessory = topAccessory,
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
  listItemTreatment: ListItemTreatment? = null,
  title: String,
  allowFontScaling: Boolean = true,
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
    listItemTreatment = listItemTreatment,
    title = AnnotatedString(title),
    allowFontScaling = allowFontScaling,
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
  listItemTreatment: ListItemTreatment? = null,
  title: AnnotatedString,
  allowFontScaling: Boolean = true,
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
  topAccessory: ListItemAccessory? = null,
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
              type = titleType,
              allowFontScaling = allowFontScaling
            )
          } else {
            Label(
              model = titleLabel,
              treatment = titleTreatment,
              type = titleType,
              allowFontScaling = allowFontScaling
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
            type = secondaryTextType,
            allowFontScaling = allowFontScaling
          )
        }
      },
    sideContent =
      sideText?.let {
        {
          Label(
            text = sideText,
            type = titleType,
            alignment = TextAlign.End,
            allowFontScaling = allowFontScaling
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
            alignment = TextAlign.End,
            allowFontScaling = allowFontScaling
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
    topAccessoryContent =
      topAccessory?.let {
        {
          ListItemAccessory(model = topAccessory)
        }
      },
    pickerMenuContent =
      pickerMenu?.takeIf { it.isShowing }?.let {
        {
          ListItemPickerMenu(model = pickerMenu)
        }
      },
    verticalPadding = if (listItemTreatment == INFO) 0.dp else 16.dp,
    horizontalPadding = if (listItemTreatment == INFO) 16.dp else 0.dp,
    offset = if (listItemTreatment == INFO) Offset(0f, -12f) else Offset.Zero,
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
  topAccessoryContent: @Composable (BoxScope.() -> Unit)?,
  pickerMenuContent: @Composable (BoxScope.() -> Unit)?,
  verticalPadding: Dp = 16.dp,
  horizontalPadding: Dp = 0.dp,
  offset: Offset = Offset.Zero,
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
      var verticalPadding = verticalPadding

      topAccessoryContent?.let {
        verticalPadding = 8.dp
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(
              top = verticalPadding
            ),
          contentAlignment = Alignment.Center
        ) {
          it()
        }
      }

      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(
              vertical = verticalPadding,
              horizontal = horizontalPadding
            )
            .offset(offset.x.dp, offset.y.dp),
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
            collapsedContent = { placeholder ->
              Box {
                CollapsedMoneyView(
                  height = 16.dp,
                  modifier = Modifier.align(Alignment.Center),
                  shimmer = !placeholder
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
