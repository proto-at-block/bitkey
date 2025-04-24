package bitkey.sample.ui

import bitkey.ui.framework.Sheet
import bitkey.ui.framework.SheetPresenter
import bitkey.ui.framework.SheetPresenterRegistry

class SampleSheetPresenterRegistry : SheetPresenterRegistry {
  override fun <SheetT : Sheet> get(sheet: SheetT): SheetPresenter<SheetT> {
    @Suppress("UNCHECKED_CAST")
    return when (sheet) {
      else -> error("Unknown sheet: ${sheet::class.simpleName}")
    } as SheetPresenter<SheetT>
  }
}
