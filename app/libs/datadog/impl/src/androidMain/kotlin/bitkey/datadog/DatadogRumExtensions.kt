package bitkey.datadog

import bitkey.datadog.ActionType.Back
import bitkey.datadog.ActionType.Click
import bitkey.datadog.ActionType.Custom
import bitkey.datadog.ActionType.Scroll
import bitkey.datadog.ActionType.Swipe
import bitkey.datadog.ActionType.Tap
import bitkey.datadog.ErrorSource.Agent
import bitkey.datadog.ErrorSource.Console
import bitkey.datadog.ErrorSource.Logger
import bitkey.datadog.ErrorSource.Network
import bitkey.datadog.ErrorSource.Source
import bitkey.datadog.ErrorSource.WebView
import bitkey.datadog.ResourceType.Other
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
