package build.wallet.statemachine.money.currency

import build.wallet.analytics.events.screen.id.ThemeSelectionEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.SheetSize
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.list.*
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Creates a sheet model for theme selection
 */
internal fun themeSelectionSheetModel(
  selectedTheme: ThemePreference,
  onSelectTheme: (ThemePreference) -> Unit,
  onExit: () -> Unit,
): SheetModel {
  val items = themeOptions.map { themeOption ->
    ListItemModel(
      title = themeOption.title,
      titleAlignment = ListItemTitleAlignment.CENTER,
      treatment = ListItemTreatment.QUATERNARY,
      onClick = { onSelectTheme(themeOption.theme) },
      selected = selectedTheme == themeOption.theme,
      topAccessory = ListItemAccessory.IconAccessory(
        model = themeSelectionIconModel(themeOption)
      )
    )
  }.toImmutableList()

  return SheetModel(
    body = ThemeSelectionBodyModel(
      items = items,
      selectedTheme = selectedTheme,
      onSelectTheme = onSelectTheme
    ),
    onClosed = onExit,
    size = SheetSize.DEFAULT
  )
}

private data class ThemeSelectionBodyModel(
  val items: ImmutableList<ListItemModel>,
  val selectedTheme: ThemePreference,
  val onSelectTheme: (ThemePreference) -> Unit,
) : FormBodyModel(
    id = ThemeSelectionEventTrackerScreenId.THEME_SELECTION,
    header = FormHeaderModel(
      headline = "Theme"
    ),
    onBack = {},
    toolbar = null,
    mainContentList =
      immutableListOf(
        FormMainContentModel.ListGroup(
          listGroupModel =
            ListGroupModel(
              style = ListGroupStyle.THREE_COLUMN_CARD_ITEM_LARGE,
              items = items
            )
        )
      ),
    primaryButton = null,
    renderContext = RenderContext.Sheet
  )

private fun themeSelectionIconModel(themeOption: ThemeOption): IconModel {
  return IconModel(
    iconImage = IconImage.LocalImage(
      icon = themeOption.icon
    ),
    iconSize = IconSize.Medium
  )
}

private val themeOptions = listOf(
  ThemeOption(ThemePreference.Manual(Theme.LIGHT), "Light", Icon.ThemeLight),
  ThemeOption(ThemePreference.Manual(Theme.DARK), "Dark", Icon.ThemeDark),
  ThemeOption(ThemePreference.System, "System", Icon.ThemeSystem)
)

private data class ThemeOption(
  val theme: ThemePreference,
  val title: String,
  val icon: Icon,
)
