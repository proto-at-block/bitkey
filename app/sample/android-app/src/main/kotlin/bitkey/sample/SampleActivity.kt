package bitkey.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import bitkey.sample.di.SampleAppComponent
import build.wallet.ui.app.App

class SampleActivity : ComponentActivity() {
  private lateinit var sampleAppComponent: SampleAppComponent

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    sampleAppComponent = (application as SampleApplication).sampleAppComponent

    setContent {
      val screenModel = sampleAppComponent.sampleAppUiStateMachine.model(props = Unit)
      App(
        model = screenModel,
        deviceInfo = sampleAppComponent.deviceInfoProvider.getDeviceInfo(),
        accelerometer = null,
        themePreferenceService = null
      )
    }
  }
}
