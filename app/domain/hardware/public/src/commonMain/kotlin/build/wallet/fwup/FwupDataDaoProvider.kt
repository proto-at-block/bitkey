package build.wallet.fwup

interface FwupDataDaoProvider {
  suspend fun get(): FwupDataDao
}
