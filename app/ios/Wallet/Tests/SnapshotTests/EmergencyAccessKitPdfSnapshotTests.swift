import Foundation
import PDFKit
import Shared
import SnapshotTesting
import XCTest

@testable import Wallet

@MainActor
class EmergencyAccessKitPdfSnapshotTests: XCTestCase {

    func test_eak_pdf() async throws {
        // For some unknown reasons our custom PDF diffing in SnapshotTestHelpers.swift always
        // records a snapshot
        // change even when the PDF is unchanged. For now we skip this test and manually run it when
        // changing the
        // EAK contents.
        throw XCTSkip("BKR-1052 iOS EAK PDF snapshot always changes when recording")

        let pdfDocument = try await EmergencyAccessKitPdfGeneratorImpl.generatedPDFDocument()

        assertBitkeySnapshot(pdf: pdfDocument)
    }

    func test_eak_page_image() async throws {
        let pdfDocument = try await EmergencyAccessKitPdfGeneratorImpl.generatedPDFDocument()

        for pageImage in pdfDocument.pageImages {
            assertBitkeySnapshot(image: pageImage)
        }
    }

}

extension EmergencyAccessKitPdfGeneratorImpl {

    static var testGenerator: EmergencyAccessKitPdfGeneratorImpl {
        EmergencyAccessKitPdfGeneratorImpl(
            apkParametersProvider: EmergencyAccessKitApkParametersProviderFake(),
            mobileKeyParametersProvider: EmergencyAccessKitMobileKeyParametersProviderFake(),
            pdfAnnotatorFactory: PdfAnnotatorFactoryImpl(),
            templateProvider: EmergencyAccessKitTemplateProviderImpl(
                platformContext: PlatformContext()
            ),
            backupDateProvider: EmergencyAccessKitBackupDateProviderFake(),
            dateTimeFormatter: DateTimeFormatterImpl()
        )
    }

    static func generatedPDFDocument() async throws -> PDFDocument {
        let generator = EmergencyAccessKitPdfGeneratorImpl.testGenerator
        let result = try await generator.generateOrThrow(
            keybox: KeyboxMockKt.KeyboxMock,
            sealedCsek: CsekFakeKt.SealedCsekFake
        )

        let generatedPDFDocument = try XCTUnwrap(PDFDocument(data: result.pdfData.toData()))

        XCTAssertEqual(generatedPDFDocument.pageCount, 2)

        return generatedPDFDocument
    }

}
