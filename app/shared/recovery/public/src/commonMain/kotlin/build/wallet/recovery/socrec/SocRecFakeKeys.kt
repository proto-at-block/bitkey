package build.wallet.recovery.socrec

import build.wallet.bitkey.socrec.DelegatedDecryptionKey
import build.wallet.bitkey.socrec.PakeCode
import build.wallet.bitkey.socrec.ProtectedCustomerIdentityKey
import com.github.michaelbull.result.getOrThrow
import io.ktor.utils.io.core.toByteArray
import okio.ByteString.Companion.toByteString

val DelegatedDecryptionKeyFake =
  SocRecCryptoFake().generateAsymmetricKey(
    ::DelegatedDecryptionKey
  ).getOrThrow()

val ProtectedCustomerIdentityKeyFake =
  SocRecCryptoFake().generateAsymmetricKey(
    ::ProtectedCustomerIdentityKey
  ).getOrThrow()

val EnrollentPakeCodeFake = PakeCode("F00DBAR".toByteArray().toByteString())

val ProtectedCustomerEnrollmentPakeKeyFake =
  SocRecCryptoFake().generateProtectedCustomerEnrollmentPakeKey(EnrollentPakeCodeFake)
    .getOrThrow()

val TrustedContactEnrollmentPakeKeyFake =
  SocRecCryptoFake().encryptDelegatedDecryptionKey(
    EnrollentPakeCodeFake,
    ProtectedCustomerEnrollmentPakeKeyFake,
    DelegatedDecryptionKeyFake
  ).getOrThrow().trustedContactEnrollmentPakeKey
