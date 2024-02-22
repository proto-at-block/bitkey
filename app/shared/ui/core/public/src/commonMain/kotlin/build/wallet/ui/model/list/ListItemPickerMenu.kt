package build.wallet.ui.model.list

/**
 * A menu of options to be displayed on top of the list item to allow the user to select
 * a new option. The list item typically will be displaying the currently selected option.
 *
 * Implemented via [Picker] in SwiftUI and [AlertDialog] with radio options in ComposeUI.
 *
 * @property isShowing: Boolean on whether to show the menu or not. Only used on Android
 * to show or hide the [AlertDialog] because on iOS the [Picker] is built directly into
 * the list item and the OS handles showing and hiding it.
 */
data class ListItemPickerMenu<Option : Any>(
  val isShowing: Boolean,
  val selectedOption: Option?,
  val options: List<Option>,
  val onOptionSelected: (Option) -> Unit,
  val titleSelector: (Option) -> String,
  val onDismiss: () -> Unit,
) {
  // Used in SwiftUI since Picker requires a Hashable conforming type, but `Option` is only `AnyObject`
  val items: List<Item<Option>> by lazy {
    options.map(::Item)
  }

  // Used in SwiftUI since Picker requires a Hashable conforming type, but `Option` is only `AnyObject`
  val selectedItem: Item<Option>? by lazy {
    selectedOption?.let(::Item)
  }

  init {
    if (selectedOption != null) {
      require(options.contains(selectedOption))
    }
  }

  data class Item<Option : Any>(
    val option: Option,
  )

  companion object {
    operator fun invoke(
      isShowing: Boolean,
      selectedOption: String?,
      options: List<String>,
      onOptionSelected: (String) -> Unit,
      onDismiss: () -> Unit,
    ): ListItemPickerMenu<String> {
      return ListItemPickerMenu(
        isShowing = isShowing,
        selectedOption = selectedOption,
        options = options,
        onOptionSelected = onOptionSelected,
        onDismiss = onDismiss,
        titleSelector = { it }
      )
    }
  }
}
