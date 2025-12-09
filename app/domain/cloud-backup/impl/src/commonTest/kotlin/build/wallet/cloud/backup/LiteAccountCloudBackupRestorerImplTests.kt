package build.wallet.cloud.backup

import bitkey.auth.AccessToken
import bitkey.auth.AccountAuthTokens
import bitkey.auth.AuthTokenScope.Recovery
import bitkey.auth.RefreshToken
import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus
import build.wallet.auth.AccountAuthenticatorMock
import build.wallet.auth.AuthStorageError
import build.wallet.auth.AuthTokensServiceFake
import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitkey.auth.AppRecoveryAuthPrivateKeyMock
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock
import build.wallet.bitkey.f8e.LiteAccountIdMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.cloud.backup.RestoreFromBackupError.AccountBackupRestorationError
import build.wallet.cloud.backup.local.BackupStorageError
import build.wallet.cloud.backup.local.CloudBackupDaoFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.relationships.RelationshipsKeysDaoFake
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant

class LiteAccountCloudBackupRestorerImplTests : FunSpec({

  val accountAuthenticator = AccountAuthenticatorMock(turbines::create)
  val authTokensService = AuthTokensServiceFake()
  val appPrivateKeyDaoFake = AppPrivateKeyDaoFake()
  val cloudBackupDao = CloudBackupDaoFake()
  val accountService = AccountServiceFake()

  val restorer =
    LiteAccountCloudBackupRestorerImpl(
      appPrivateKeyDao = appPrivateKeyDaoFake,
      relationshipsKeysDao = RelationshipsKeysDaoFake(),
      accountAuthenticator = accountAuthenticator,
      authTokensService = authTokensService,
      cloudBackupDao = cloudBackupDao,
      accountService = accountService
    )

  beforeTest {
    accountAuthenticator.reset()
    authTokensService.reset()
    appPrivateKeyDaoFake.reset()
    accountService.reset()
  }

  context("parameterized tests for all backup versions") {
    AllLiteAccountBackupMocks.forEach { backup ->
      val backupVersion = when (backup) {
        is CloudBackupV2 -> "v2"
        is CloudBackupV3 -> "v3"
        else -> "unknown"
      }

      context("cloud backup $backupVersion") {
        beforeTest {
          accountAuthenticator.reset()
          authTokensService.reset()
          appPrivateKeyDaoFake.reset()
          accountService.reset()
          cloudBackupDao.reset()
        }

        test("success") {
          restorer
            .restoreFromBackup(backup as CloudBackup)
            .shouldBe(Ok(LiteAccountMock))
          accountAuthenticator.authCalls.awaitItem().shouldBe(
            (backup as SocRecV1BackupFeatures).appRecoveryAuthKeypair.publicKey
          )
          authTokensService.getTokens(LiteAccountIdMock, Recovery)
            .shouldBeOk(
              AccountAuthTokens(
                accessToken = AccessToken(raw = "access-token-fake"),
                refreshToken = RefreshToken(raw = "refresh-token-fake"),
                accessTokenExpiresAt = Instant.DISTANT_FUTURE
              )
            )
          appPrivateKeyDaoFake
            .asymmetricKeys
            .shouldBe(
              mapOf(
                AppRecoveryAuthPublicKeyMock to AppRecoveryAuthPrivateKeyMock
              )
            )
          accountService.accountState.value.shouldBeEqual(
            Ok(
              AccountStatus.OnboardingAccount(
                LiteAccountMock
              )
            )
          )
        }

        test("failure - account authenticator error") {
          val authError = AuthStorageError()
          accountAuthenticator.authResults =
            mutableListOf(
              Err(AuthStorageError())
            )
          restorer
            .restoreFromBackup(backup as CloudBackup)
            .shouldBe(Err(AccountBackupRestorationError(authError)))
          accountAuthenticator.authCalls.awaitItem().shouldBe(
            (backup as SocRecV1BackupFeatures).appRecoveryAuthKeypair.publicKey
          )
        }

        test("failure - store app auth keypair failure") {
          val throwable = Throwable("foo")
          appPrivateKeyDaoFake.storeAppAuthKeyPairResult = Err(throwable)
          restorer
            .restoreFromBackup(backup as CloudBackup)
            .shouldBe(Err(AccountBackupRestorationError(throwable)))
        }

        test("failure - cloud backup dao failure") {
          cloudBackupDao.returnError = true
          restorer
            .restoreFromBackup(backup as CloudBackup)
            .shouldBe(Err(AccountBackupRestorationError(BackupStorageError())))
          accountAuthenticator.authCalls.awaitItem().shouldBe(
            (backup as SocRecV1BackupFeatures).appRecoveryAuthKeypair.publicKey
          )
          authTokensService.getTokens(LiteAccountIdMock, Recovery)
            .shouldBeOk(
              AccountAuthTokens(
                accessToken = AccessToken(raw = "access-token-fake"),
                refreshToken = RefreshToken(raw = "refresh-token-fake"),
                accessTokenExpiresAt = Instant.DISTANT_FUTURE
              )
            )
          appPrivateKeyDaoFake
            .asymmetricKeys
            .shouldBe(
              mapOf(
                AppRecoveryAuthPublicKeyMock to AppRecoveryAuthPrivateKeyMock
              )
            )
        }
      }
    }
  }
})
