package build.wallet.emergencyaccesskit

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.cloud.backup.csek.SealedCsekFake
import build.wallet.platform.pdf.PdfAnnotatorFactoryImpl
import build.wallet.time.DateTimeFormatterImpl
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmergencyAccessKitSnapshotTest {
  private val application = ApplicationProvider.getApplicationContext<Application>()
  private val snapshotGenerator =
    EmergencyAccessKitPdfGeneratorImpl(
      apkParametersProvider = EmergencyAccessKitApkParametersProviderFake(),
      mobileKeyParametersProvider = EmergencyAccessKitMobileKeyParametersProviderFake(),
      pdfAnnotatorFactory = PdfAnnotatorFactoryImpl(application),
      templateProvider = EmergencyAccessKitTemplateProviderImpl(application),
      backupDateProvider = EmergencyAccessKitBackupDateProviderFake(),
      dateTimeFormatter = DateTimeFormatterImpl(),
      qrCodeGenerator = EmergencyAccessKitQrCodeGeneratorImpl()
    )

  @Test
  fun emergencyAccessKitPDFSnapshot() {
    runBlocking {
      @Suppress("UnsafeCallOnNullableType")
      val pdfBytes =
        snapshotGenerator
          .generate(KeyboxMock, SealedCsekFake)
          .map { it.pdfData.toByteArray() }
          .get()!!

      // Writes PDF in `data/data/build.wallet.domain.emergency.access.kit.impl.test/files`,
      // which can be viewed in IntelliJ Device Explorer.
      // The directory is removed upon the completion of the test so to view it
      // set a breakpoint on `fileOutput.close()` and run the test in Debug.
      val fileOutput = application.openFileOutput("Emergency Exit Kit.pdf", Context.MODE_PRIVATE)
      fileOutput.write(pdfBytes)
      fileOutput.close()
    }
  }
}
