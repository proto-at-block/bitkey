package build.wallet.emergencyexitkit

import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.cloud.backup.csek.SealedCsekFake
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull

class EmergencyExitKitPdfGeneratorImplTests : FunSpec({
  context("pdf generator") {
    test("pdf data generated") {
      val pdfGenerator = EmergencyExitKitPdfGeneratorFake()
      val pdfData = pdfGenerator.generate(KeyboxMock, SealedCsekFake)
      pdfData.get().shouldNotBeNull()
    }
  }
})
