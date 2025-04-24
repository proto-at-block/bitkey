package bitkey.ui.framework

/**
 * Provides a [SheetPresenter] for a given [Sheet].
 * Each [Sheet] should have a corresponding [SheetPresenter].
 *
 * A likely implementation should look like this:
 *
 * ```kotlin
 * class MyAppSheetPresenterRegistry(
 *   private val settingsSheetPresenter: SettingsSheetPresenter,
 *   private val moneyHomeSheetPresenter: MoneyHomeSheetPresenter
 * ): SheetPresenterRegistry {
 *    override fun <SheetT : Sheet> get(sheet: SheetT): SheetPresenter<SheetT> {
 *      return when (sheet) {
 *        is SettingsSheet -> settingsSheetPresenter
 *        is MoneyHomeSheet -> moneyHomeSheetPresenter
 *        else -> error("Unknown sheet: ${sheet::class.simpleName}")
 *   } as SheetPresenter<SheetT>
 * ```
 */
interface SheetPresenterRegistry {
  fun <SheetT : Sheet> get(sheet: SheetT): SheetPresenter<SheetT>
}
