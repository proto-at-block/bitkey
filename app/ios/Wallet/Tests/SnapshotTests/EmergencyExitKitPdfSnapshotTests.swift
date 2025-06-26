import Foundation
import PDFKit
import Shared
import SnapshotTesting
import XCTest

@testable import Wallet

@MainActor
class EmergencyExitKitPdfSnapshotTests: XCTestCase {

    func test_eek_pdf() async throws {
        // For some unknown reasons our custom PDF diffing in SnapshotTestHelpers.swift always
        // records a snapshot
        // change even when the PDF is unchanged. For now we skip this test and manually run it when
        // changing the
        // EEK contents.
        throw XCTSkip("BKR-1052 iOS EEK PDF snapshot always changes when recording")

        let pdfDocument = try await EmergencyExitKitPdfGeneratorImpl.generatedPDFDocument()

        assertBitkeySnapshot(pdf: pdfDocument)
    }

    func test_eek_page_image() async throws {
        let pdfDocument = try await EmergencyExitKitPdfGeneratorImpl.generatedPDFDocument()

        for pageImage in pdfDocument.pageImages {
            assertBitkeySnapshot(image: pageImage)
        }
    }

}

extension EmergencyExitKitPdfGeneratorImpl {

    static var testGenerator: EmergencyExitKitPdfGeneratorImpl {
        EmergencyExitKitPdfGeneratorImpl(
            apkParametersProvider: EmergencyExitKitApkParametersProviderFake(),
            appKeyParametersProvider: EmergencyExitKitAppKeyParametersProviderFake(),
            pdfAnnotatorFactory: PdfAnnotatorFactoryImpl(),
            templateProvider: EmergencyExitKitTemplateProviderImpl(),
            backupDateProvider: EmergencyExitKitBackupDateProviderFake(),
            dateTimeFormatter: DateTimeFormatterImpl(),
            qrCodeGenerator: EmergencyExitKitQrCodeGeneratorImpl()
        )
    }

    static func generatedPDFDocument() async throws -> PDFDocument {
        let generator = EmergencyExitKitPdfGeneratorImpl.testGenerator
        let result = try await generator.generateOrThrow(
            keybox: KeyboxMockKt.KeyboxMock,
            sealedCsek: CsekFakeKt.SealedCsekFake
        )

        let generatedPDFDocument = try XCTUnwrap(PDFDocument(data: result.pdfData.toData()))

        XCTAssertEqual(generatedPDFDocument.pageCount, 2)

        return generatedPDFDocument
    }

}
