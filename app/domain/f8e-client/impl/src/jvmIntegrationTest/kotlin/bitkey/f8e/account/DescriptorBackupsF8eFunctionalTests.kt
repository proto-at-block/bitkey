package bitkey.f8e.account

import bitkey.backup.DescriptorBackup
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.createLostHardwareKeyset
import build.wallet.testing.ext.getHardwareFactorProofOfPossession
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8

class DescriptorBackupsF8eFunctionalTests : FunSpec({
  // TODO [W-11422]: Re-enable with SSEK upload after backend is ready.
  xtest("create new account and upload descriptor backup") {
    val app = launchNewApp()
    val account = app.onboardFullAccountWithFakeHardware()

    // No descriptor backups for the account.
    app.listKeysetsF8eClient
      .listKeysets(
        f8eEnvironment = account.config.f8eEnvironment,
        fullAccountId = account.accountId
      ).getOrThrow()
      .descriptorBackups.shouldBe(emptyList())
    app.getActiveKeysetF8eClient.get(
      f8eEnvironment = account.config.f8eEnvironment,
      accountId = account.accountId
    ).getOrThrow()
      .descriptorBackup.shouldBeNull()

    // Upload fake descriptor backup associated with the active keyset.
    val descriptorBackup = DescriptorBackup(
      keysetId = account.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId,
      sealedDescriptor = "foobar".encodeUtf8()
    )
    app.updateDescriptorBackupsF8eClient.update(
      f8eEnvironment = account.config.f8eEnvironment,
      accountId = account.accountId,
      descriptorBackups = listOf(descriptorBackup),
      hwKeyProof = app.getHardwareFactorProofOfPossession()
    ).getOrThrow()

    // Verify that the descriptor backup is returned.
    app.getActiveKeysetF8eClient.get(
      f8eEnvironment = account.config.f8eEnvironment,
      accountId = account.accountId
    ).getOrThrow()
      .descriptorBackup
      .shouldBe(descriptorBackup)
    app.listKeysetsF8eClient
      .listKeysets(
        f8eEnvironment = account.config.f8eEnvironment,
        fullAccountId = account.accountId
      ).getOrThrow()
      .descriptorBackups
      .shouldBe(listOf(descriptorBackup))

    // New descriptors need new keysets.
    val newKeyset = app.createLostHardwareKeyset(account)
    // Create a new backup
    val secondDescriptor = DescriptorBackup(
      keysetId = newKeyset.f8eSpendingKeyset.keysetId,
      sealedDescriptor = "barfoo".encodeUtf8()
    )

    app.updateDescriptorBackupsF8eClient.update(
      f8eEnvironment = account.config.f8eEnvironment,
      accountId = account.accountId,
      descriptorBackups = listOf(descriptorBackup, secondDescriptor),
      hwKeyProof = app.getHardwareFactorProofOfPossession()
    ).getOrThrow()

    app.listKeysetsF8eClient
      .listKeysets(
        f8eEnvironment = account.config.f8eEnvironment,
        fullAccountId = account.accountId
      ).getOrThrow()
      .descriptorBackups
      .shouldBe(listOf(descriptorBackup, secondDescriptor))
  }
})
