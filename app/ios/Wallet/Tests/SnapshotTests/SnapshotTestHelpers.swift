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

    /**
     * - parameter usesVisualEffect: Whether the view being tested uses a visual effect (like a blur)
     */
    func assertBitkeySnapshots(
        view: some View,
        screenModel: ScreenModel? = nil,
        usesVisualEffect: Bool = false,
        fileName: String = #function,
        precision: Float = 0.9992
    ) {
        let viewController: UIViewController = if let screenModel {
            SwiftUIWrapperViewController(view, screenModel: screenModel)
        } else {
            UIHostingController(rootView: view)
        }

        let iPhoneSEResult = verifySnapshot(
            matching: viewController,
            as: .image(
                drawHierarchyInKeyWindow: usesVisualEffect,
                precision: precision,
                perceptualPrecision: precision,
                size: ViewImageConfig.iPhoneSe.size,
                traits: ViewImageConfig.iPhoneSe.traits
            ),
            named: "iPhoneSe",
            record: isRecording,
            testName: fileName
        )

        XCTAssertNil(iPhoneSEResult)

        let iPhone15ProMax = verifySnapshot(
            matching: viewController,
            as: .image(
                drawHierarchyInKeyWindow: usesVisualEffect,
                precision: precision,
                perceptualPrecision: precision,
                size: ViewImageConfig.iPhone13ProMax.size,
                traits: ViewImageConfig.iPhone13Pro.traits
            ),
            named: "iPhoneProMax",
            record: isRecording,
            testName: fileName
        )

        XCTAssertNil(iPhone15ProMax)
    }

    func assertBitkeySnapshot(
        image: UIImage,
        fileName: String = #function,
        precision: Float = 0.995
    ) {
        let result = verifySnapshot(
            of: image,
            as: .image(
                precision: precision,
                perceptualPrecision: precision
            ),
            record: isRecording,
            testName: fileName
        )

        XCTAssertNil(result)
    }

    func assertBitkeySnapshot(
        pdf: PDFDocument,
        fileName: String = #function,
        precision _: Float = 0.995
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

class TestSuspendFunction: KotlinSuspendFunction0 {
    func invoke() async throws -> Any? { return nil }
}

// MARK: -

extension ScreenModel {
    static func snapshotTest(
        statusBannerModel: StatusBannerModel? = nil
    ) -> ScreenModel {
        return ScreenModel(
            // Dummy body model
            body: SplashBodyModel(
                bitkeyWordMarkAnimationDelay: 0,
                bitkeyWordMarkAnimationDuration: 0,
                eventTrackerScreenInfo: nil
            ),
            tabBar: nil,
            onTwoFingerDoubleTap: nil,
            presentationStyle: .root,
            colorMode: .dark,
            alertModel: nil,
            statusBannerModel: statusBannerModel,
            toastModel: nil,
            bottomSheetModel: nil,
            systemUIModel: nil
        )
    }
}

// MARK: -

extension StatusBannerModel {
    static func snapshotTest() -> StatusBannerModel {
        return StatusBannerModel(
            title: "Offline",
            subtitle: "Balance last updated at 9:43pm",
            onClick: {}
        )
    }
}

// MARK: -

extension ButtonModel {
    static func snapshotTest(
        text: String,
        isEnabled: Bool = true,
        isLoading: Bool = false,
        leadingIcon: Icon? = nil,
        treatment: ButtonModel.Treatment = .primary,
        size: ButtonModel.Size = .footer
    ) -> ButtonModel {
        return ButtonModel(
            text: text,
            isEnabled: isEnabled,
            isLoading: isLoading,
            leadingIcon: leadingIcon,
            treatment: treatment,
            size: size,
            testTag: nil,
            onClick: StandardClick {}
        )
    }
}

// MARK: -

extension ListModel {

    static let transactionsSnapshotTest = ListModel(
        headerText: "Recent activity",
        sections: [
            ListGroupModel(
                header: nil,
                items: [.snapshotTestOutgoing, .snapshotTestIncoming],
                style: .none,
                headerTreatment: .secondary,
                footerButton: nil
            ),
            ListGroupModel(
                header: nil,
                items: [.snapshotTestOutgoing, .snapshotTestIncoming],
                style: .none,
                headerTreatment: .secondary,
                footerButton: nil
            ),
        ]
    )

}

// MARK: -

extension ListItemModel {

    static let snapshotTestOutgoing = TransactionItemModelKt.TransactionItemModel(
        truncatedRecipientAddress: "Txn Id Here",
        date: "Pending",
        amount: "$90.50",
        amountEquivalent: "121,075 sats",
        incoming: false,
        isPending: false,
        onClick: {}
    )

    static let snapshotTestIncoming = TransactionItemModelKt.TransactionItemModel(
        truncatedRecipientAddress: "Txn Id Here",
        date: "Pending",
        amount: "$23.50",
        amountEquivalent: "45,075 sats",
        incoming: true,
        isPending: false,
        onClick: {}
    )

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
