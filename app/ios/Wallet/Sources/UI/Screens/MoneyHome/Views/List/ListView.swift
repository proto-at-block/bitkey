import Foundation
import Shared
import SwiftUI

// MARK: -

public struct ListView: View {

    // MARK: - Private Types

    private enum Metrics {
        static let interSectionSpacing = 0.f
    }

    // MARK: - Public Properties

    public var model: ListModel

    // MARK: - View

    public var body: some View {
        VStack(spacing: 0) {
            model.headerText.map { headerText in
                ModeledText(model: .standard(headerText, font: .title2))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding([.top], 16)
                    .padding([.bottom], 8)
            }
            VStack(spacing: Metrics.interSectionSpacing) {
                ForEach(Array(zip(model.sections.indices, model.sections)), id: \.0) { index, section in
                    ListGroupView(viewModel: section)
                }
            }
        }
    }

}
