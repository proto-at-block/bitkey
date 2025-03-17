import PDFKit
import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

extension XCTestCase {

    private var isRecording: Bool {
        UserDefaults.standard.bool(forKey: "is-recording")
    }

    func assertBitkeySnapshots(
        viewController: () -> UIViewController,
        fileName: String,
        precision: Float = 0.9992,
        perceptualPrecision: Float = 0.98
    ) {
        // From app root, sourceFileUrl = "ios/Wallet/Tests/SnapshotTests/SnapshotTestHelpers.swift"
        let sourceFileUrl = URL(fileURLWithPath: "\(#file)", isDirectory: false)
        let snapshotDirectory = sourceFileUrl
            .deletingLastPathComponent() // SnapshotTestHelpers.swift
            .appendingPathComponent(
                "../../../../ui/features/public/snapshots/images",
                isDirectory: true
            )
            .absoluteString
            .dropFirst("file://".count)
        let iPhoneSEResult = verifySnapshot(
            of: viewController(),
            as: .image(
                drawHierarchyInKeyWindow: true, // Required for CMP rendering
                precision: precision,
                perceptualPrecision: perceptualPrecision,
                size: ViewImageConfig.iPhoneSe.size,
                traits: ViewImageConfig.iPhoneSe.traits
            ),
            named: "iPhoneSe",
            record: isRecording,
            snapshotDirectory: String(snapshotDirectory),
            testName: fileName
        )

        XCTAssertNil(iPhoneSEResult)
    }

    func assertBitkeySnapshot(
        image: UIImage,
        fileName: String = #function,
        precision: Float = 0.995,
        perceptualPrecision: Float = 0.98
    ) {
        let result = verifySnapshot(
            of: image,
            as: .image(
                precision: precision,
                perceptualPrecision: perceptualPrecision
            ),
            record: isRecording,
            testName: fileName
        )

        XCTAssertNil(result)
    }

    func assertBitkeySnapshot(
        pdf: PDFDocument,
        fileName: String = #function,
        precision _: Float = 0.995,
        perceptualPrecision _: Float = 0.98
    ) {
        let result = verifySnapshot(
            of: pdf,
            as: .pdf,
            record: isRecording,
            testName: fileName
        )

        XCTAssertNil(result)
    }

}

// MARK: -

private extension Snapshotting where Value == PDFDocument, Format == PDFDocument {
    static var pdf: Snapshotting {
        return .init(
            pathExtension: "pdf",
            diffing: .init(
                toData: { $0.dataRepresentation()! },
                fromData: { PDFDocument(data: $0)! },
                diff: { old, new in
                    let oldImages = old.pageImages
                    let newImages = new.pageImages

                    if oldImages.count != newImages.count {
                        return ("Different number of pages", [])
                    }

                    for (oldImage, newImage) in zip(oldImages, newImages) {
                        if let imageDiff = Diffing.image.diff(oldImage, newImage) {
                            return imageDiff
                        }
                    }

                    return nil
                }
            )
        )
    }
}

extension PDFDocument {
    var pageImages: [UIImage] {
        var images: [UIImage] = []

        for pageIndex in 0 ..< pageCount {
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
