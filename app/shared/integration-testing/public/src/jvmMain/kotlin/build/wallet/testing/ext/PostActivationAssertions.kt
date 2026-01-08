package build.wallet.testing.ext

import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.f8e.recovery.PrivateMultisigRemoteKeyset
import build.wallet.testing.AppTester
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.getOrThrow
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.flow.first

/**
 * Encapsulates expectations for verifying account/keybox state after onboarding or recovery.
 * Set only the fields relevant to the scenario being asserted.
 */
data class PostActivationExpectations(
  val cloudAccount: CloudStoreAccount? = null,
  val expectCloudBackup: Boolean? = null,
  val expectEmergencyExitKit: Boolean? = null,
  val expectedKeysetCount: Int? = null,
  val checkActiveKeysetDescriptorBackupExists: Boolean = false,
  val expectedCanUseKeyboxKeysets: Boolean? = null,
  val checkOnboardingArtifactsCleared: Boolean = true,
)

/**
 * Verifies key post-activation invariants so individual tests don’t duplicate assertions.
 *
 * Common usages:
 * - After onboarding: call with defaults and provide `cloudAccount = CloudStoreAccount1Fake`.
 * - After recovery with cloud backup step: set `expectCloudBackup = true`, `expectEmergencyExitKit = true`.
 */
suspend fun AppTester.verifyPostActivationState(expectations: PostActivationExpectations) {
  // Resolve active account and keybox
  val account = accountService.activeAccount().first().shouldBeTypeOf<FullAccount>()
  val activeKeybox = keyboxDao.activeKeybox().first().shouldBeOk().shouldNotBeNull()

  // Keybox integrity
  activeKeybox.fullAccountId shouldBe account.accountId
  expectations.expectedCanUseKeyboxKeysets?.let { expected ->
    activeKeybox.canUseKeyboxKeysets.shouldBe(expected)
  }

  // Onboarding scratch artifacts should be cleared in post-activation state
  if (expectations.checkOnboardingArtifactsCleared) {
    keyboxDao.onboardingKeybox().first().shouldBeOk { onboardingKeybox ->
      onboardingKeybox.shouldBeNull()
    }
    onboardingAppKeyKeystore
      .getAppKeyBundle(
        localId = activeKeybox.activeAppKeyBundle.localId,
        network = account.config.bitcoinNetworkType
      )
      .shouldBeNull()
    onboardingKeyboxHwAuthPublicKeyDao.get().shouldBeOk { hwKeys ->
      hwKeys.shouldBeNull()
    }
    onboardingKeyboxSealedSsekDao.get().shouldBeOk { sealedSsek ->
      sealedSsek.shouldBeNull()
    }
  }

  // Cloud backup and EEK assertions, if requested
  val cloudAccount: CloudStoreAccount? =
    expectations.cloudAccount
      ?: cloudStoreAccountRepository.currentAccount(cloudServiceProvider()).getOrThrow()

  expectations.expectCloudBackup?.let { shouldExist ->
    cloudAccount.shouldNotBeNull()
    val backup = cloudBackupRepository.readActiveBackup(cloudAccount)
      .getOrThrow()
    if (shouldExist) {
      backup.shouldNotBeNull()
    } else {
      backup.shouldBeNull()
    }
  }

  expectations.expectEmergencyExitKit?.let { shouldExist ->
    cloudFileStore
      .exists(account = cloudAccount ?: CloudStoreAccountFake.CloudStoreAccount1Fake, fileName = "Emergency Exit Kit.pdf")
      .result
      .shouldBeOk { exists ->
        exists.shouldBe(shouldExist)
      }
  }

  // Server-side key material assertions
  val serverKeysets = listKeysetsF8eClient
    .listKeysets(
      f8eEnvironment = account.config.f8eEnvironment,
      fullAccountId = account.accountId
    )
    .getOrThrow()
    .keysets

  if (expectations.expectedKeysetCount != null) {
    serverKeysets.size.shouldBe(expectations.expectedKeysetCount)
  } else {
    serverKeysets.shouldNotBeEmpty()
  }

  if (expectations.checkActiveKeysetDescriptorBackupExists && serverKeysets.isNotEmpty()) {
    val activeKeyset = serverKeysets.first()

    if (activeKeyset is PrivateMultisigRemoteKeyset) {
      descriptorBackupService
        .checkBackupForPrivateKeyset(keysetId = activeKeyset.keysetId)
        .shouldBeOk()
    }
  }
}

/** Convenience for onboarding tests with default fake cloud account. */
suspend fun AppTester.verifyPostOnboardingState(
  cloudAccount: CloudStoreAccount = CloudStoreAccountFake.CloudStoreAccount1Fake,
) = verifyPostActivationState(
  PostActivationExpectations(
    cloudAccount = cloudAccount,
    expectCloudBackup = true,
    expectEmergencyExitKit = true,
    expectedKeysetCount = 1,
    checkActiveKeysetDescriptorBackupExists = true,
    expectedCanUseKeyboxKeysets = true,
    checkOnboardingArtifactsCleared = true
  )
)

/** Convenience for recovery tests that include the cloud backup step. */
suspend fun AppTester.verifyPostRecoveryState(
  cloudAccount: CloudStoreAccount = CloudStoreAccountFake.CloudStoreAccount1Fake,
  expectCloudBackup: Boolean = true,
  expectEmergencyExitKit: Boolean = true,
  expectedKeysetCount: Int? = null,
  expectedCanUseKeyboxKeysets: Boolean? = null,
  checkOnboardingArtifactsCleared: Boolean = false,
) = verifyPostActivationState(
  PostActivationExpectations(
    cloudAccount = cloudAccount,
    expectCloudBackup = expectCloudBackup,
    expectEmergencyExitKit = expectEmergencyExitKit,
    expectedKeysetCount = expectedKeysetCount,
    checkActiveKeysetDescriptorBackupExists = true,
    expectedCanUseKeyboxKeysets = expectedCanUseKeyboxKeysets,
    checkOnboardingArtifactsCleared = checkOnboardingArtifactsCleared
  )
)
