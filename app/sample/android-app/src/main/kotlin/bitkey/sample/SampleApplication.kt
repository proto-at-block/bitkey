package bitkey.sample

import android.app.Application
import bitkey.sample.di.SampleAppComponent

class SampleApplication : Application() {
  lateinit var sampleAppComponent: SampleAppComponent

  override fun onCreate() {
    super.onCreate()
    sampleAppComponent = SampleAppComponent.create()
  }
}
