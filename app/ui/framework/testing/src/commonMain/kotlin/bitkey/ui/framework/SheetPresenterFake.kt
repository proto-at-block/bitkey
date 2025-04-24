package bitkey.ui.framework

import androidx.compose.runtime.Composable
import build.wallet.statemachine.core.SheetModel

class SheetPresenterFake : SheetPresenter<SheetFake> {
  @Composable
  override fun model(
    navigator: Navigator,
    sheet: SheetFake,
  ): SheetModel {
    return SheetModel(
      body = NavigatingBodyModelFake(
        sheet.id,
        goTo = navigator::goTo,
        showSheet = navigator::showSheet,
        closeSheet = navigator::closeSheet
      ),
      onClosed = {}
    )
  }
}
