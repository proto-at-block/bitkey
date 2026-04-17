package build.wallet.ui.app.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel.State.Success
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun LoadingSuccessPreviewLoading() {
  PreviewWalletTheme {
    LoadingSuccessScreen(
      model =
        LoadingSuccessBodyModel(
          state = LoadingSuccessBodyModel.State.Loading,
          id = null
        )
    )
  }
}

@Preview
@Composable
fun LoadingSuccessPreviewLoadingDesignSystemV2() {
  PreviewWalletTheme {
    CompositionLocalProvider(LocalDesignSystemUpdatesEnabled provides true) {
      LoadingSuccessScreen(
        model =
          LoadingSuccessBodyModel(
            message = "Syncing backup data",
            state = LoadingSuccessBodyModel.State.Loading,
            id = null
          )
      )
    }
  }
}

@Preview
@Composable
fun LoadingSuccessPreviewSuccess() {
  PreviewWalletTheme {
    LoadingSuccessScreen(
      model =
        LoadingSuccessBodyModel(
          message = "You succeeded",
          state = Success,
          id = null
        )
    )
  }
}

@Preview
@Composable
fun LoadingSuccessPreviewSuccessDesignSystemV2() {
  PreviewWalletTheme {
    CompositionLocalProvider(LocalDesignSystemUpdatesEnabled provides true) {
      LoadingSuccessScreen(
        model =
          LoadingSuccessBodyModel(
            message = "Backup synced",
            state = Success,
            id = null
          )
      )
    }
  }
}
