package bitkey.ui.framework

data class SheetFake(
  val id: String,
  override val origin: Screen,
) : Sheet
