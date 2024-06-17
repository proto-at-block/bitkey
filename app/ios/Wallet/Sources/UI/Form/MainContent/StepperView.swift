import Foundation
import Shared
import SwiftUI

// MARK: -

struct StepperView: View {

    // MARK: - Private Properties

    private let viewModel: FormMainContentModel.StepperIndicator

    // MARK: - Life Cycle

    init(viewModel: FormMainContentModel.StepperIndicator) {
        self.viewModel = viewModel
    }

    // MARK: - View

    var body: some View {
        VStack {
            ProgressView(value: viewModel.progress)
                .progressViewStyle(LinearProgressViewStyle())
                .frame(height: 8.0)
                .scaleEffect(x: 1, y: 2, anchor: .center)
                .clipShape(RoundedRectangle(cornerRadius: 4))
                .tint(.primary)
            HStack {
                ForEach(Array(viewModel.labels.enumerated()), id: \.element) { index, label in
                    Text(label)
                        .font(.body)
                        .foregroundColor(.secondaryForeground)
                    if index < viewModel.labels.count - 1 {
                        Spacer()
                    }
                }
            }
        }
    }
}
