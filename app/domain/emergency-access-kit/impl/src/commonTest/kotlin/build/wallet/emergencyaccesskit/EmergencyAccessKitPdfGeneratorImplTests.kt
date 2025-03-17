package build.wallet.emergencyaccesskit

import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.cloud.backup.csek.SealedCsekFake
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull

class EmergencyAccessKitPdfGeneratorImplTests : FunSpec({
  context("pdf generator") {
    test("pdf data generated") {
      val pdfGenerator = EmergencyAccessKitPdfGeneratorFake()
      val pdfData = pdfGenerator.generate(KeyboxMock, SealedCsekFake)
      pdfData.get().shouldNotBeNull()
    }
  }
})
