package build.wallet.keybox

import build.wallet.cloud.backup.CloudBackupRepositoryFake
import build.wallet.cloud.backup.shouldBeEmpty
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.cloud.store.CloudStoreAccountRepositoryMock
import build.wallet.cloud.store.CloudStoreServiceProviderMock
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Development
import build.wallet.platform.config.AppVariant.Team
import com.github.michaelbull.result.Ok
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec

class CloudBackupDeleterImplTests : FunSpec({
  val cloudInstanceId = "fake"
  val cloudBackupRepository = CloudBackupRepositoryFake()
  val cloudStoreAccountRepository = CloudStoreAccountRepositoryMock()
  val cloudStorageProvider = CloudStoreServiceProviderMock(cloudInstanceId)

  fun cloudBackupDeleter(appVariant: AppVariant) =
    CloudBackupDeleterImpl(
      appVariant = appVariant,
      cloudBackupRepository = cloudBackupRepository,
      cloudStoreAccountRepository = cloudStoreAccountRepository
    )

  beforeTest {
    cloudStoreAccountRepository.currentAccountResult = Ok(CloudAccountMock(cloudInstanceId))
    cloudBackupRepository.reset()
  }

  test("not allowed to delete cloud backup in Customer builds") {
    shouldThrow<IllegalStateException> {
      cloudBackupDeleter(AppVariant.Customer).delete(cloudStorageProvider)
    }
  }

  listOf(Development, Team).forEach { variant ->
    test("delete cloud backup for $variant variant") {
      cloudBackupDeleter(variant).delete(cloudStorageProvider)

      cloudBackupRepository.shouldBeEmpty()
    }
  }
})
