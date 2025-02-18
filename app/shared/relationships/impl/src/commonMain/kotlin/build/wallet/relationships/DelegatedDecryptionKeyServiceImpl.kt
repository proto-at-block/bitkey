package build.wallet.relationships

import build.wallet.account.AccountService
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.relationships.DelegatedDecryptionKey
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
import build.wallet.crypto.SealedData
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Impl
import build.wallet.encrypt.CryptoBox
import build.wallet.ensure
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.relationships.RelationshipsF8eClient
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.flow.first
import okio.ByteString

@BitkeyInject(AppScope::class)
class DelegatedDecryptionKeyServiceImpl(
  private val accountService: AccountService,
  private val cryptoBox: CryptoBox,
  @Impl private val relationshipsF8eClient: RelationshipsF8eClient,
  private val relationshipsKeysDao: RelationshipsKeysDao,
) : DelegatedDecryptionKeyService {
  override suspend fun uploadSealedDelegatedDecryptionKeyData(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    sealedData: SealedData,
  ): Result<Unit, Error> =
    coroutineBinding {
      relationshipsF8eClient.uploadSealedDelegatedDecryptionKeyData(
        fullAccountId,
        f8eEnvironment,
        sealedData
      ).bind()
    }

  override suspend fun getSealedDelegatedDecryptionKeyData(
    accountId: AccountId?,
    f8eEnvironment: F8eEnvironment?,
  ): Result<SealedData, Error> =
    coroutineBinding {
      var accountIdParam = accountId
      var f8eEnvironmentParam = f8eEnvironment

      if (accountId == null) {
        val account = accountService.activeAccount().first()
        ensure(account is FullAccount) { Error("No active full account present.") }

        accountIdParam = account.accountId
        f8eEnvironmentParam = account.config.f8eEnvironment
      }

      ensure(accountIdParam != null) { Error("No account id present.") }
      ensure(f8eEnvironmentParam != null) { Error("No f8eEnvironmentParam present.") }

      relationshipsF8eClient.getSealedDelegatedDecryptionKeyData(
        accountIdParam,
        f8eEnvironmentParam
      ).bind()
    }

  override suspend fun restoreDelegatedDecryptionKey(
    unsealedData: ByteString,
  ): Result<Unit, RelationshipsKeyError> =
    coroutineBinding {
      val keypair = cryptoBox.keypairFromSecretBytes(unsealedData)
      val privateKey = PrivateKey<DelegatedDecryptionKey>(bytes = keypair.privateKey.bytes)
      val publicKey = PublicKey<DelegatedDecryptionKey>(keypair.publicKey.bytes.hex())

      relationshipsKeysDao.saveKey(AppKey(publicKey = publicKey, privateKey = privateKey)).bind()
    }
}
