package build.wallet.cloud.store

data class CloudStoreServiceProviderMock(
  val instanceId: String,
) : CloudStoreServiceProvider {
  override val name: String = "Mock Cloud Store: $instanceId"
}
