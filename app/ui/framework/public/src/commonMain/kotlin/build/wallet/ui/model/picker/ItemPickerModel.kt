package build.wallet.ui.model.picker

import kotlinx.collections.immutable.ImmutableList

/**
 * A form field to choose one of the options.
 *
 * Implemented via [Picker] in SwiftUI and [AlertDialog] with radio options in ComposeUI.
 */
data class ItemPickerModel<Option : Any>(
  val selectedOption: Option?,
  val options: ImmutableList<Option>,
  val onOptionSelected: (Option) -> Unit,
  val titleSelector: (Option?) -> String,
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
      selectedOption: String?,
      options: ImmutableList<String>,
      onOptionSelected: (String) -> Unit,
      emptyValueTitle: String = "",
    ): ItemPickerModel<String> {
      return ItemPickerModel(
        selectedOption = selectedOption,
        options = options,
        onOptionSelected = onOptionSelected,
        titleSelector = { it ?: emptyValueTitle }
      )
    }
  }
}
