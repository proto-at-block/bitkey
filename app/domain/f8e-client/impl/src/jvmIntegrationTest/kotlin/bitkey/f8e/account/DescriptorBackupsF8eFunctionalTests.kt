package bitkey.f8e.account

import bitkey.backup.DescriptorBackup
import build.wallet.encrypt.XCiphertext
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.createLostHardwareKeyset
import build.wallet.testing.ext.getActiveAppGlobalAuthKey
import build.wallet.testing.ext.getHardwareFactorProofOfPossession
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.toByteString

class DescriptorBackupsF8eFunctionalTests : FunSpec({
  test("create new account and upload descriptor backup") {
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
      sealedDescriptor = XCiphertext("foobar")
    )
    // The server checks that we have a valid 60-byte sealed SSEK.
    val sealedSsekByteArray = ByteArray(60) { 1 }
    app.updateDescriptorBackupsF8eClient.update(
      f8eEnvironment = account.config.f8eEnvironment,
      accountId = account.accountId,
      descriptorBackups = listOf(descriptorBackup),
      sealedSsek = sealedSsekByteArray.toByteString(),
      appAuthKey = app.getActiveAppGlobalAuthKey().publicKey,
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
      sealedDescriptor = XCiphertext("barfoo")
    )

    app.updateDescriptorBackupsF8eClient.update(
      f8eEnvironment = account.config.f8eEnvironment,
      accountId = account.accountId,
      descriptorBackups = listOf(descriptorBackup, secondDescriptor),
      sealedSsek = sealedSsekByteArray.toByteString(),
      appAuthKey = app.getActiveAppGlobalAuthKey().publicKey,
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
