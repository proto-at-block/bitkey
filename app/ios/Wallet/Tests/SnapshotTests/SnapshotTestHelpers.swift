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
        precision: Float = 0.995
    ) {
        let viewController: UIViewController
        if let screenModel = screenModel {
            viewController = SwiftUIWrapperViewController(view, screenModel: screenModel)
        } else {
            viewController = UIHostingController(rootView: view)
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
        precision: Float = 0.995
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
            bottomSheetModel: nil,
            onTwoFingerTripleTap: nil,
            systemUIModel: nil
        )
    }
}

// MARK: -

extension StatusBannerModel {
    static func snapshotTest() -> StatusBannerModel {
        return StatusBannerModel(title: "Offline", subtitle: "Balance last updated at 9:43pm", onClick: {})
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
            onClick: ClickCompanion().standardClick { }
        )
    }
}

// MARK: -

extension ListModel {

    static let transactionsSnapshotTest = ListModel(
        headerText: "Recent activity",
        sections: [
            ListGroupModel(
                header: "Pending",
                items: [.snapshotTestOutgoing, .snapshotTestIncoming],
                style: .none, 
                footerButton: nil
            ),
            ListGroupModel(
                header: "Confirmed",
                items: [.snapshotTestOutgoing, .snapshotTestIncoming], 
                style: .none, 
                footerButton: nil
            )
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

fileprivate extension Snapshotting where Value == PDFDocument, Format == PDFDocument {
    static var pdf: Snapshotting {
        return .init(
            pathExtension: "pdf",
            diffing: .init(
                toData: { $0.dataRepresentation()! },
                fromData: { PDFDocument(data: $0)! },
                diff: { old, new in
                    if old.string != new.string {
                        // In the future we can improve this with more detailed diffing that checks for layout, attributes, etc (comparing the raw data bytes did not work as expected as they change with each creation).
                        return ("PDF string representations different", [])
                    } else {
                        return nil
                    }
                }
            )
        )
    }
}