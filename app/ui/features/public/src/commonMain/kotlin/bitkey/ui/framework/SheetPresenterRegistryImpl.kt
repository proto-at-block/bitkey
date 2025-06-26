package bitkey.ui.framework

import bitkey.ui.sheets.ViewInvitationSheet
import bitkey.ui.sheets.ViewInvitationSheetPresenter
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.settings.full.device.fingerprints.ManageFingerprintsOptionsSheet
import build.wallet.statemachine.settings.full.device.fingerprints.ManageFingerprintsOptionsSheetPresenter

@BitkeyInject(ActivityScope::class)
class SheetPresenterRegistryImpl(
  private val viewingInvitationSheetPresenter: ViewInvitationSheetPresenter,
  private val manageFingerprintsOptionsSheetPresenter: ManageFingerprintsOptionsSheetPresenter,
) : SheetPresenterRegistry {
  override fun <SheetT : Sheet> get(sheet: SheetT): SheetPresenter<SheetT> {
    @Suppress("UNCHECKED_CAST")
    return when (sheet) {
      is ViewInvitationSheet -> viewingInvitationSheetPresenter
      is ManageFingerprintsOptionsSheet -> manageFingerprintsOptionsSheetPresenter
      else -> error("Unknown sheet: ${sheet::class.simpleName}")
    } as SheetPresenter<SheetT>
  }
}
