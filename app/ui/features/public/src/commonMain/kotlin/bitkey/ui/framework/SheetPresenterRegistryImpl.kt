package bitkey.ui.framework

import bitkey.ui.sheets.ViewInvitationSheet
import bitkey.ui.sheets.ViewInvitationSheetPresenter
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject

@BitkeyInject(ActivityScope::class)
class SheetPresenterRegistryImpl(
  private val viewingInvitationSheetPresenter: ViewInvitationSheetPresenter,
) : SheetPresenterRegistry {
  override fun <SheetT : Sheet> get(sheet: SheetT): SheetPresenter<SheetT> {
    @Suppress("UNCHECKED_CAST")
    return when (sheet) {
      is ViewInvitationSheet -> viewingInvitationSheetPresenter
      else -> error("Unknown sheet: ${sheet::class.simpleName}")
    } as SheetPresenter<SheetT>
  }
}
