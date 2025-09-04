package build.wallet.testing.ext

import bitkey.backup.DescriptorBackup
import build.wallet.account.getAccount
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.testing.AppTester
import com.github.michaelbull.result.getOrThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe

suspend fun AppTester.verifyDescriptorBackupsUploaded(
  accountId: FullAccountId,
  count: Int,
): List<DescriptorBackup> {
  return listKeysetsF8eClient
    .listKeysets(
      f8eEnvironment = initialF8eEnvironment,
      fullAccountId = accountId
    ).getOrThrow()
    .descriptorBackups
    .shouldNotBeEmpty().also {
      it.size.shouldBe(count)
    }
}

suspend fun AppTester.verifyNoDescriptorBackups(accountId: FullAccountId) {
  listKeysetsF8eClient
    .listKeysets(
      f8eEnvironment = initialF8eEnvironment,
      fullAccountId = accountId
    ).getOrThrow()
    .descriptorBackups
    .shouldBeEmpty()
}

suspend fun AppTester.verifyCanUseKeyboxKeysets(expected: Boolean) {
  val account = accountService.getAccount<FullAccount>().getOrThrow()
  account.keybox.canUseKeyboxKeysets.shouldBe(expected)
}
