import Shared
import SwiftUI

// MARK: -

struct CardContentDrillList: View {

    // MARK: - Private Types

    private enum Metrics {
        static let height = 40.f
        static let rowHorizontalSpacing = 8.f
        static let imageSize = 24.f
    }

    // MARK: - Public Properties

    var viewModel: CardModelCardContentDrillList

    // MARK: - View

    var body: some View {
        VStack {
            ForEach(Array(zip(viewModel.items.indices, viewModel.items)), id: \.0) { index, itemModel in
                VStack {
                    ListItemView(viewModel: itemModel, verticalPadding: 0)
                        .frame(minHeight: Metrics.height)
                    if index < viewModel.items.count - 1 {
                        Rectangle()
                            .fill(Color.foreground10)
                            .frame(height: 1)
                    }
                }
            }
        }
    }

}
