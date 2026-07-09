package build.wallet.statemachine.core.form

data class FormScreenTitleModel(
  val eyebrow: String? = null,
  val title: String? = null,
)

sealed interface FormScreenLayoutModel {
  data object Legacy : FormScreenLayoutModel

  data class LargeTitle(
    val contentSpacing: Int = 24,
    val scrollable: Boolean = true,
    val mainContentVerticalAlignment: FormMainContentVerticalAlignment =
      FormMainContentVerticalAlignment.TOP,
  ) : FormScreenLayoutModel
}

enum class FormMainContentVerticalAlignment {
  TOP,
  CENTER,
  BOTTOM,
}
