package build.wallet.platform.links

class AppStoreUrlProviderMock : AppStoreUrlProvider {
  private var appStoreUrl: String = "https://fake.app.store/test"

  override fun getAppStoreUrl(): String = appStoreUrl

  fun setAppStoreUrl(url: String) {
    appStoreUrl = url
  }

  fun reset() {
    appStoreUrl = "https://fake.app.store/test"
  }
}
