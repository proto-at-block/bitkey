package build.wallet.inheritance

import bitkey.serialization.json.decodeFromStringResult
import bitkey.serialization.json.encodeToStringResult
import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilder
import build.wallet.bitkey.inheritance.InheritanceKeyset
import build.wallet.bitkey.inheritance.InheritanceMaterial
import build.wallet.bitkey.inheritance.InheritanceMaterialHashData
import build.wallet.bitkey.inheritance.InheritanceMaterialPackage
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.relationships.DelegatedDecryptionKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.encrypt.XCiphertext
import build.wallet.relationships.RelationshipsCrypto
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8

@BitkeyInject(AppScope::class)
class InheritanceCryptoImpl(
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val relationships: InheritanceRelationshipsProvider,
  private val crypto: RelationshipsCrypto,
  private val descriptorBuilder: BitcoinMultiSigDescriptorBuilder,
) : InheritanceCrypto {
  override suspend fun getInheritanceMaterialHashData(
    keybox: Keybox,
  ): Result<InheritanceMaterialHashData, Error> {
    val contacts = relationships.getEndorsedInheritanceContacts()
      ?: return Err(Error("Inheritance Contacts unavailable."))
    val sortedContacts = contacts.sortedBy { it.id.value }
    return coroutineBinding {
      InheritanceMaterialHashData(
        networkType = keybox.config.bitcoinNetworkType,
        spendingKey = keybox.activeAppKeyBundle.spendingKey,
        contacts = sortedContacts
      )
    }
  }

  override suspend fun createInheritanceMaterial(
    keybox: Keybox,
  ): Result<InheritanceMaterial, Error> {
    val contacts = relationships.getEndorsedInheritanceContacts()
      ?: return Err(Error("Inheritance Contacts unavailable."))
    val sortedContacts = contacts.sortedBy { it.id.value }
    return coroutineBinding {
      val pkMatOutput = createInheritanceKeyset(keybox)
        .mapError { Error("Error creating inheritance keyset", it) }
        .flatMap { Json.encodeToStringResult(it) }
        .map { it.encodeUtf8() }
        .flatMap { crypto.encryptPrivateKeyMaterial(it) }
        .bind()

      val descriptor = descriptorBuilder.watchingDescriptor(
        appPublicKey = keybox.activeSpendingKeyset.appKey.key,
        hardwareKey = keybox.activeSpendingKeyset.hardwareKey.key,
        serverKey = keybox.activeSpendingKeyset.f8eSpendingKeyset.spendingPublicKey.key
      ).raw.encodeUtf8()

      val sealedDescriptor = crypto
        .encryptData(dek = pkMatOutput.privateKeyEncryptionKey, data = descriptor)
        .bind()

      val serverRootXpub = keybox.activeSpendingKeyset.f8eSpendingKeyset.privateWalletRootXpub
      val sealedServerRootXpub = serverRootXpub?.let {
        crypto.encryptData(
          dek = pkMatOutput.privateKeyEncryptionKey,
          data = serverRootXpub.encodeUtf8()
        ).bind()
      }

      val packages = sortedContacts.map {
        InheritanceMaterialPackage(
          relationshipId = it.id,
          sealedDek = crypto
            .encryptPrivateKeyEncryptionKey(it.identityKey, pkMatOutput.privateKeyEncryptionKey)
            .bind(),
          sealedMobileKey = pkMatOutput.sealedPrivateKeyMaterial,
          sealedDescriptor = sealedDescriptor,
          sealedServerRootXpub = sealedServerRootXpub
        )
      }

      InheritanceMaterial(packages)
    }
  }

  private suspend fun createInheritanceKeyset(keybox: Keybox): Result<InheritanceKeyset, Error> =
    coroutineBinding {
      val publicSpendingKey = keybox.activeAppKeyBundle.spendingKey
      val privateSpendingKey = appPrivateKeyDao.getAppSpendingPrivateKey(publicSpendingKey)
        .toErrorIfNull { IllegalStateException("Active app spending private key not found.") }
        .mapError { Error("Error getting spending private key", it) }
        .bind()
      val network = keybox.config.bitcoinNetworkType

      InheritanceKeyset(
        network = network,
        appSpendingPublicKey = publicSpendingKey,
        appSpendingPrivateKey = privateSpendingKey
      )
    }

  override suspend fun decryptInheritanceMaterialPackage(
    delegatedDecryptionKey: AppKey<DelegatedDecryptionKey>,
    sealedDek: XCiphertext,
    sealedAppKey: XCiphertext,
    sealedDescriptor: XCiphertext?,
    sealedServerRootXpub: XCiphertext?,
  ): Result<DecryptInheritanceMaterialPackageOutput, Error> {
    return coroutineBinding {
      val pkek = crypto.decryptPrivateKeyEncryptionKey(
        delegatedDecryptionKey,
        sealedDek
      )

      val privateKeyMaterial = crypto
        .decryptPrivateKeyMaterial(pkek, sealedAppKey)
        .bind()

      val inheritanceKeyset = Json
        .decodeFromStringResult<InheritanceKeyset>(privateKeyMaterial.utf8())
        .bind()

      val descriptor = sealedDescriptor?.let {
        crypto.decryptPrivateKeyMaterial(pkek, it).bind().utf8()
      }

      val serverRootXpub = sealedServerRootXpub?.let {
        crypto.decryptPrivateKeyMaterial(pkek, it).bind().utf8()
      }

      DecryptInheritanceMaterialPackageOutput(
        inheritanceKeyset = inheritanceKeyset,
        descriptor = descriptor,
        serverRootXpub = serverRootXpub
      )
    }
  }
}
