import Foundation
import PDFKit
import Shared
import SnapshotTesting
import XCTest

@testable import Wallet

@MainActor
class EmergencyAccessKitPdfSnapshotTests: XCTestCase {
    
    func test_eak_pdf() async throws {
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
            templateProvider: EmergencyAccessKitTemplateProviderImpl(platformContext: PlatformContext()),
            backupDateProvider: EmergencyAccessKitBackupDateProviderFake(),
            dateTimeFormatter: DateTimeFormatterImpl()
        )
    }
    
    static func generatedPDFDocument() async throws -> PDFDocument {
        let generator = EmergencyAccessKitPdfGeneratorImpl.testGenerator
        let result = try await generator.generate(keybox: KeyboxMockKt.KeyboxMock, sealedCsek: CsekFakeKt.SealedCsekFake)
        let generatedData = try XCTUnwrap(result.component1())
        let generatedPDFDocument = try XCTUnwrap(PDFDocument(data: generatedData.pdfData.toData()))
        
        XCTAssertEqual(generatedPDFDocument.pageCount, 2)
        
        return generatedPDFDocument
    }
    
}

fileprivate extension PDFDocument {
    
    var pageImages: [UIImage] {
        var images: [UIImage] = []
        
        for pageIndex in 0..<pageCount {
            guard let page = page(at: pageIndex) else { continue }
            
            let pageRect = page.bounds(for: .mediaBox)
            let renderer = UIGraphicsImageRenderer(size: pageRect.size)
            let pageImage = renderer.image { context in
                UIColor.white.set()
                context.fill(pageRect)
                context.cgContext.translateBy(x: 0.0, y: pageRect.size.height)
                context.cgContext.scaleBy(x: 1.0, y: -1.0)
                context.cgContext.drawPDFPage(page.pageRef!)
            }
            
            images.append(pageImage)
        }
        
        return images
    }

}
