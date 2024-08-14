package build.wallet.recovery.socrec

import build.wallet.bitkey.relationships.DelegatedDecryptionKey
import build.wallet.bitkey.relationships.PakeCode
import com.github.michaelbull.result.getOrThrow
import io.ktor.utils.io.core.toByteArray
import okio.ByteString.Companion.toByteString

val DelegatedDecryptionKeyFake =
  SocRecCryptoFake().generateAsymmetricKey<DelegatedDecryptionKey>().getOrThrow()

val EnrollmentPakeCodeFake = PakeCode("F00DBAR".toByteArray().toByteString())

val ProtectedCustomerEnrollmentPakeKeyFake =
  SocRecCryptoFake().generateProtectedCustomerEnrollmentPakeKey(EnrollmentPakeCodeFake)
    .getOrThrow()
