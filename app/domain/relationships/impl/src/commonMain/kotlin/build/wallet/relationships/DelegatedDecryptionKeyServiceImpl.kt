package build.wallet.relationships

import bitkey.account.AccountConfigService
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
import build.wallet.f8e.relationships.RelationshipsF8eClient
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import okio.ByteString

@BitkeyInject(AppScope::class)
class DelegatedDecryptionKeyServiceImpl(
  private val cryptoBox: CryptoBox,
  @Impl private val relationshipsF8eClient: RelationshipsF8eClient,
  private val relationshipsKeysDao: RelationshipsKeysDao,
  private val accountConfigService: AccountConfigService,
) : DelegatedDecryptionKeyService {
  override suspend fun uploadSealedDelegatedDecryptionKeyData(
    fullAccountId: FullAccountId,
    sealedData: SealedData,
  ): Result<Unit, Error> =
    coroutineBinding {
      val f8eEnvironment = accountConfigService.activeOrDefaultConfig().value.f8eEnvironment
      relationshipsF8eClient.uploadSealedDelegatedDecryptionKeyData(
        fullAccountId,
        f8eEnvironment,
        sealedData
      ).bind()
    }

  override suspend fun getSealedDelegatedDecryptionKeyData(
    accountId: AccountId,
  ): Result<SealedData, Error> =
    coroutineBinding {
      val f8eEnvironment = accountConfigService.activeOrDefaultConfig().value.f8eEnvironment
      relationshipsF8eClient.getSealedDelegatedDecryptionKeyData(accountId, f8eEnvironment).bind()
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
