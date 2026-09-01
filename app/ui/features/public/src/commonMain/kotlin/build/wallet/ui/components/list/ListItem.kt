package build.wallet.ui.components.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.LabelModel
import build.wallet.ui.components.coachmark.CoachmarkLabel
import build.wallet.ui.components.coachmark.CoachmarkPresenter
import build.wallet.ui.components.icon.IconButton
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.LabelTreatment.*
import build.wallet.ui.components.label.LabelTreatment.Destructive
import build.wallet.ui.components.label.loadingScrim
import build.wallet.ui.components.layout.CollapsedMoneyView
import build.wallet.ui.components.layout.CollapsibleLabelContainer
import build.wallet.ui.compose.listItemTestTag
import build.wallet.ui.compose.resId
import build.wallet.ui.model.coachmark.CoachmarkLabelTreatment
import build.wallet.ui.model.coachmark.CoachmarkModel
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
    ListItem(
      modifier = modifier,
      listItemTreatment = treatment,
      title = AnnotatedString(title),
      titleLabel = titleLabel,
      allowFontScaling = allowFontScaling,
      contentAlignment = when (titleAlignment) {
        ListItemTitleAlignment.LEFT -> Alignment.Start
        ListItemTitleAlignment.CENTER -> Alignment.CenterHorizontally
      },
      titleTreatment = when {
        !enabled -> Disabled
        else -> when (treatment) {
          PRIMARY, DESTRUCTIVE -> if (treatment == DESTRUCTIVE) Destructive else Primary
          SECONDARY, INFO -> Secondary
          TERTIARY -> Tertiary
          QUATERNARY -> Quaternary
          PRIMARY_TITLE, SECONDARY_DISPLAY -> Jumbo
        }
      },
      titleType = (
        titleType ?: when (treatment) {
          PRIMARY, DESTRUCTIVE -> LabelType.Body2Medium
          SECONDARY -> LabelType.Body2Regular
          TERTIARY -> LabelType.Body3Regular
          QUATERNARY -> LabelType.Label3
          PRIMARY_TITLE -> LabelType.Title1
          SECONDARY_DISPLAY -> LabelType.Display2
          INFO -> LabelType.Body4Regular
        }
      ).regularizedForListItems(),
      listItemTitleBackgroundTreatment = listItemTitleBackgroundTreatment,
      secondaryText = secondaryText?.let {
        AnnotatedString(it, SpanStyle(color = secondaryTextTint.textColor(enabled)))
      },
      secondaryTextType = when (treatment) {
        SECONDARY_DISPLAY -> LabelType.Body1Regular
        else -> LabelType.Body3Regular
      }.regularizedForListItems(),
      sideText = sideText?.let {
        AnnotatedString(it, SpanStyle(color = sideTextTint.textColor(enabled)))
      },
      secondarySideText = secondarySideText?.let {
        AnnotatedString(it, SpanStyle(color = ListItemSideTextTint.SECONDARY.textColor(enabled)))
      },
      secondarySideTextType = secondarySideTextType.regularizedForListItems(),
      leadingAccessory = if (enabled) leadingAccessory else leadingAccessory?.disable(),
      leadingAccessoryAlignment = when (leadingAccessoryAlignment) {
        TOP -> Alignment.Top
        CENTER -> Alignment.CenterVertically
      },
      trailingAccessory = if (enabled) trailingAccessory else trailingAccessory?.disable(),
      specialTrailingAccessory = specialTrailingAccessory,
      topAccessory = topAccessory,
      onClick = onClick,
      pickerMenu = pickerMenu,
      collapseContent = collapseContent,
      coachmark = coachmark,
      coachmarkLabel = coachmarkLabel,
      isLoading = isLoading,
      explainer = explainer,
      titleSingleLine = titleSingleLine
    )
  }
}

@Composable
fun ListItem(
  modifier: Modifier = Modifier,
  listItemTreatment: ListItemTreatment? = null,
  title: String,
  allowFontScaling: Boolean = true,
  contentSpacing: Dp = 8.dp,
  contentAlignment: Alignment.Horizontal = Alignment.Start,
  titleTreatment: LabelTreatment = Primary,
  titleType: LabelType = LabelType.Body2Medium,
  listItemTitleBackgroundTreatment: ListItemTitleBackgroundTreatment? = null,
  secondaryText: String? = null,
  sideText: String? = null,
  secondarySideText: String? = null,
  secondarySideTextType: LabelType = LabelType.Body3Regular,
  leadingAccessory: ListItemAccessory? = null,
  leadingAccessoryAlignment: Alignment.Vertical = Alignment.CenterVertically,
  trailingAccessory: ListItemAccessory? = null,
  onClick: (() -> Unit)? = null,
  pickerMenu: ListItemPickerMenu<*>? = null,
  titleLabel: LabelModel? = null,
  specialTrailingAccessory: ListItemAccessory? = null,
  coachmark: CoachmarkModel? = null,
  coachmarkLabel: CoachmarkLabelModel? = null,
  isLoading: Boolean = false,
  explainer: ListItemExplainer? = null,
) {
  ListItem(
    modifier = modifier,
    listItemTreatment = listItemTreatment,
    title = AnnotatedString(title),
    allowFontScaling = allowFontScaling,
    contentSpacing = contentSpacing,
    contentAlignment = contentAlignment,
    titleTreatment = titleTreatment,
    titleType = titleType,
    listItemTitleBackgroundTreatment = listItemTitleBackgroundTreatment,
    secondaryText = secondaryText?.let(::AnnotatedString),
    sideText = sideText?.let(::AnnotatedString),
    secondarySideText = secondarySideText?.let(::AnnotatedString),
    secondarySideTextType = secondarySideTextType,
    leadingAccessory = leadingAccessory,
    leadingAccessoryAlignment = leadingAccessoryAlignment,
    trailingAccessory = trailingAccessory,
    specialTrailingAccessory = specialTrailingAccessory,
    onClick = onClick,
    pickerMenu = pickerMenu,
    titleLabel = titleLabel,
    coachmark = coachmark,
    coachmarkLabel = coachmarkLabel,
    isLoading = isLoading,
    explainer = explainer
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
private fun ListItem(
  modifier: Modifier = Modifier,
  listItemTreatment: ListItemTreatment?,
  title: AnnotatedString,
  allowFontScaling: Boolean,
  contentSpacing: Dp = 8.dp,
  contentAlignment: Alignment.Horizontal,
  titleTreatment: LabelTreatment,
  titleType: LabelType,
  listItemTitleBackgroundTreatment: ListItemTitleBackgroundTreatment?,
  secondaryText: AnnotatedString?,
  secondaryTextType: LabelType = LabelType.Body3Regular,
  sideText: AnnotatedString?,
  secondarySideText: AnnotatedString?,
  secondarySideTextType: LabelType,
  leadingAccessory: ListItemAccessory?,
  leadingAccessoryAlignment: Alignment.Vertical,
  trailingAccessory: ListItemAccessory?,
  specialTrailingAccessory: ListItemAccessory?,
  topAccessory: ListItemAccessory? = null,
  onClick: (() -> Unit)?,
  pickerMenu: ListItemPickerMenu<*>?,
  titleLabel: LabelModel?,
  collapseContent: Boolean = false,
  coachmark: CoachmarkModel?,
  coachmarkLabel: CoachmarkLabelModel?,
  isLoading: Boolean,
  explainer: ListItemExplainer?,
  titleSingleLine: Boolean = false,
) {
  val testTag = listItemTestTag(title.text)
  val isInfo = listItemTreatment == INFO

  Box(
    modifier = modifier
      .fillMaxWidth()
      .resId(testTag)
      .clickable(
        interactionSource = MutableInteractionSource(),
        indication = null,
        enabled = onClick != null,
        onClick = { onClick?.invoke() }
      )
  ) {
    Column {
      val contentVerticalPadding = when {
        topAccessory != null -> 8.dp
        isInfo -> 0.dp
        else -> 16.dp
      }

      topAccessory?.let {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = contentVerticalPadding),
          contentAlignment = Alignment.Center
        ) {
          ListItemAccessory(
            model = it,
            isLoading = isLoading,
            parentTestTag = "$testTag-top-accessory"
          )
        }
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(
            vertical = contentVerticalPadding,
            horizontal = if (isInfo) 16.dp else 0.dp
          )
          .offset(y = if (isInfo) (-12).dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(contentSpacing)
      ) {
        leadingAccessory?.let {
          Box(modifier = Modifier.align(leadingAccessoryAlignment)) {
            ListItemAccessory(
              model = it,
              isLoading = isLoading,
              parentTestTag = "$testTag-leading-accessory"
            )
          }
        }
        Column(
          modifier = Modifier.weight(1F),
          horizontalAlignment = contentAlignment
        ) {
          TitleContent(
            title = title,
            titleLabel = titleLabel,
            titleTreatment = titleTreatment,
            titleType = titleType,
            allowFontScaling = allowFontScaling,
            backgroundTreatment = listItemTitleBackgroundTreatment,
            coachmarkLabel = coachmarkLabel,
            isLoading = isLoading,
            singleLine = titleSingleLine
          )
          secondaryText?.let {
            Label(
              modifier = Modifier.loadingScrim(isLoading).hideWhenLoading(isLoading),
              text = it,
              type = secondaryTextType,
              allowFontScaling = allowFontScaling
            )
          }
        }
        if (sideText != null || secondarySideText != null) {
          // No weight: side texts wrap to their content, allowing the title and
          // secondary text to grow into any unused space.
          CollapsibleLabelContainer(
            collapsed = collapseContent,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.End,
            slideCollapsedContent = false,
            topContent = sideText?.let {
              {
                Label(
                  modifier = Modifier.loadingScrim(isLoading).hideWhenLoading(isLoading),
                  text = it,
                  type = titleType,
                  alignment = TextAlign.End,
                  allowFontScaling = allowFontScaling
                )
              }
            },
            bottomContent = secondarySideText?.let {
              {
                Label(
                  modifier = Modifier.loadingScrim(isLoading).hideWhenLoading(isLoading),
                  text = it,
                  type = secondarySideTextType,
                  treatment = LabelTreatment.Secondary,
                  alignment = TextAlign.End,
                  allowFontScaling = allowFontScaling
                )
              }
            },
            collapsedContent = {
              Box {
                CollapsedMoneyView(
                  height = 16.dp,
                  modifier = Modifier.align(Alignment.Center),
                  shimmer = false
                )
              }
            }
          )
        }
        specialTrailingAccessory?.let {
          Box {
            ListItemAccessory(
              model = it,
              isLoading = isLoading,
              parentTestTag = "$testTag-special-trailing-accessory"
            )
          }
        }
        trailingAccessory?.let {
          Box {
            ListItemAccessory(
              model = it,
              isLoading = isLoading,
              parentTestTag = "$testTag-trailing-accessory"
            )
          }
        }
      }
      if (pickerMenu?.isShowing == true) {
        Box { ListItemPickerMenu(model = pickerMenu) }
      }
      explainer?.let {
        Box(modifier = Modifier.fillMaxWidth()) {
          ExplainerContent(it)
        }
      }
      coachmark?.let {
        CoachmarkPresenter(yOffset = 0f, model = it)
      }
    }
  }
}

@Composable
private fun TitleContent(
  title: AnnotatedString,
  titleLabel: LabelModel?,
  titleTreatment: LabelTreatment,
  titleType: LabelType,
  allowFontScaling: Boolean,
  backgroundTreatment: ListItemTitleBackgroundTreatment?,
  coachmarkLabel: CoachmarkLabelModel?,
  isLoading: Boolean,
  singleLine: Boolean = false,
) {
  Box(
    modifier = when (backgroundTreatment) {
      ListItemTitleBackgroundTreatment.RECOVERY ->
        Modifier
          .background(WalletTheme.colors.foreground10, RoundedCornerShape(12.dp))
          .fillMaxWidth()
          .padding(16.dp)
      null -> Modifier
    },
    contentAlignment = when (backgroundTreatment) {
      ListItemTitleBackgroundTreatment.RECOVERY -> Alignment.Center
      null -> Alignment.TopStart
    }
  ) {
    Row(modifier = Modifier.loadingScrim(isLoading)) {
      val maxLines = if (singleLine) 1 else Int.MAX_VALUE
      val overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Clip
      if (titleLabel == null) {
        Label(
          modifier = Modifier.hideWhenLoading(isLoading),
          text = title,
          treatment = titleTreatment,
          type = titleType,
          maxLines = maxLines,
          overflow = overflow,
          allowFontScaling = allowFontScaling
        )
      } else {
        Label(
          modifier = Modifier.hideWhenLoading(isLoading),
          model = titleLabel,
          treatment = titleTreatment,
          type = titleType,
          maxLines = maxLines,
          overflow = overflow,
          allowFontScaling = allowFontScaling
        )
      }

      coachmarkLabel?.let { model ->
        Spacer(Modifier.width(8.dp))
        CoachmarkLabel(
          model = if (titleTreatment == Disabled) {
            model.copy(treatment = CoachmarkLabelTreatment.Disabled)
          } else {
            model
          }
        )
      }
    }
  }
}

@Composable
private fun ExplainerContent(explainer: ListItemExplainer) {
  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val extraWidth = 32.dp
    Box(
      modifier = Modifier
        .requiredWidth(maxWidth + extraWidth)
        .background(
          color = WalletTheme.colors.secondary,
          shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
        ),
      contentAlignment = Alignment.CenterStart
    ) {
      if (explainer.showTopDivider) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(WalletTheme.colors.foreground10)
            .align(Alignment.TopCenter)
        )
      }

      Row(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          explainer.title?.let {
            Label(
              text = it,
              type = LabelType.Body3Bold,
              alignment = TextAlign.Start,
              treatment = LabelTreatment.Primary
            )
          }
          if (explainer.title != null && explainer.subtitle != null) {
            Spacer(Modifier.height(6.dp))
          }
          explainer.subtitle?.let {
            Label(
              text = it,
              type = LabelType.Body3Regular,
              alignment = TextAlign.Start,
              treatment = LabelTreatment.Secondary
            )
          }
        }
        explainer.iconButton?.let {
          IconButton(model = it)
        }
      }
    }
  }
}

/**
 * Hides content while loading (the loading scrim renders in its place).
 */
private fun Modifier.hideWhenLoading(isLoading: Boolean): Modifier =
  if (isLoading) alpha(0f) else this

/**
 * List items always use regular font weights for consistency.
 */
private fun LabelType.regularizedForListItems(): LabelType =
  when (this) {
    LabelType.Body1Medium -> LabelType.Body1Regular
    LabelType.Body2Medium -> LabelType.Body2Regular
    LabelType.Body3Medium -> LabelType.Body3Regular
    LabelType.Body4Medium -> LabelType.Body4Regular
    else -> this
  }

@Composable
private fun ListItemSideTextTint.textColor(enabled: Boolean) =
  when {
    !enabled -> WalletTheme.colors.foreground30
    else -> when (this) {
      ListItemSideTextTint.PRIMARY -> WalletTheme.colors.foreground
      ListItemSideTextTint.SECONDARY -> WalletTheme.colors.foreground60
      ListItemSideTextTint.GREEN -> WalletTheme.colors.positiveForeground
    }
  }
