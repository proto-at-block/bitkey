import Combine
import Foundation
import Shared
import SwiftUI

// MARK: -

struct FormViewBottomSheet: View {

    // MARK: - Private Properties

    @SwiftUI.State
    private var totalHeight: CGFloat = 0
    private let totalHeightSubject: PassthroughSubject<CGFloat, Never>

    private let viewModel: FormBodyModel

    // MARK: - Life Cycle

    init(viewModel: FormBodyModel, totalHeightSubject: PassthroughSubject<CGFloat, Never>) {
        self.viewModel = viewModel
        self.totalHeightSubject = totalHeightSubject
    }

    // MARK: - View

    var body: some View {
        VStack(alignment: .leading) {
            // Don't use a scroll view or else showing in the bottom sheet will not work
            FormContentView(
                toolbarModel: viewModel.toolbar,
                headerModel: viewModel.header,
                mainContentList: viewModel.mainContentList,
                renderContext: .sheet
            )

            if #available(iOS 16.0, *) {
                Spacer()
                    .frame(height: 24)
            } else {
                // On iOS 15, sheets will sometimes be forced to full screen due to their content
                // size.
                // In that case, we want a full size spacer to push the buttons to the bottom of the
                // screen.
                Spacer()
            }
            FormFooterView(viewModel: viewModel)
        }
        .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
        .padding(.vertical, 16)
        .background(GeometryReader { gp -> Color in
            DispatchQueue.main.async {
                // This allows us to dynamically size the bottom sheet to the height of the contents
                self.totalHeight = gp.size.height
            }
            return Color.clear
        })
        .onChange(of: totalHeight) { newHeight in
            totalHeightSubject.send(newHeight)
        }
        .onAppear {
            viewModel.onLoaded()
        }

        Spacer()
    }

}
