package build.wallet.datadog

import build.wallet.datadog.ActionType.Back
import build.wallet.datadog.ActionType.Click
import build.wallet.datadog.ActionType.Custom
import build.wallet.datadog.ActionType.Scroll
import build.wallet.datadog.ActionType.Swipe
import build.wallet.datadog.ActionType.Tap
import build.wallet.datadog.ErrorSource.Agent
import build.wallet.datadog.ErrorSource.Console
import build.wallet.datadog.ErrorSource.Logger
import build.wallet.datadog.ErrorSource.Network
import build.wallet.datadog.ErrorSource.Source
import build.wallet.datadog.ErrorSource.WebView
import build.wallet.datadog.ResourceType.Other
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumActionType.BACK
import com.datadog.android.rum.RumActionType.CLICK
import com.datadog.android.rum.RumActionType.CUSTOM
import com.datadog.android.rum.RumActionType.SCROLL
import com.datadog.android.rum.RumActionType.SWIPE
import com.datadog.android.rum.RumActionType.TAP
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumErrorSource.AGENT
import com.datadog.android.rum.RumErrorSource.CONSOLE
import com.datadog.android.rum.RumErrorSource.LOGGER
import com.datadog.android.rum.RumErrorSource.NETWORK
import com.datadog.android.rum.RumErrorSource.SOURCE
import com.datadog.android.rum.RumErrorSource.WEBVIEW
import com.datadog.android.rum.RumResourceKind

internal val ResourceType.rumResourceKind: RumResourceKind
  get() =
    when (this) {
      Other -> RumResourceKind.OTHER
    }

internal val ActionType.rumActionType: RumActionType
  get() =
    when (this) {
      Tap -> TAP
      Scroll -> SCROLL
      Swipe -> SWIPE
      Click -> CLICK
      Back -> BACK
      Custom -> CUSTOM
    }

internal val ErrorSource.rumErrorSource: RumErrorSource
  get() =
    when (this) {
      Network -> NETWORK
      Source -> SOURCE
      Console -> CONSOLE
      Logger -> LOGGER
      Agent -> AGENT
      WebView -> WEBVIEW
    }
