import Foundation
import Shared
import SwiftUI

// MARK: -

struct ExplainerView: View {

    // MARK: - Private Properties

    private let viewModel: FormMainContentModelExplainer

    // MARK: - Life Cycle

    init(viewModel: FormMainContentModelExplainer) {
        self.viewModel = viewModel
    }

    // MARK: - View

    var body: some View {
        VStack(spacing: 24) {
            ForEach(Array(zip(viewModel.items.indices, viewModel.items)), id: \.0) { _, item in
                StatementView(viewModel: item)
            }
        }
    }

}

// MARK: -

private struct StatementView: View {

    // MARK: - Private Properties

    private let viewModel: FormMainContentModelExplainer.Statement

    // MARK: - Life Cycle

    init(viewModel: FormMainContentModelExplainer.Statement) {
        self.viewModel = viewModel
    }

    // MARK: - View

    var body: some View {
        HStack(alignment: .top, spacing: 16) {
            if let icon = viewModel.leadingIcon {
                Image(uiImage: icon.uiImage)
                    .frame(width: icon.uiImage.size.width)
            }
            VStack(alignment: .leading, spacing: 4) {
                viewModel.title.map { ModeledText(model: .standard($0, font: .body2Bold)) }
                LabelView(viewModel: viewModel.body)
            }
            Spacer()
        }
    }

}

// MARK: -

private struct LabelView: View {

    // MARK: - Private Properties

    private let viewModel: LabelModel

    // MARK: - Life Cycle

    init(viewModel: LabelModel) {
        self.viewModel = viewModel
    }

    // MARK: - View

    var body: some View {
        switch viewModel {
        case let model as LabelModelStringModel:
            ModeledText(model: .standard(model.string, font: .body2Regular))

        case let model as LabelModelStringWithStyledSubstringModel:
            ModeledText(
                model: .standard(
                    .stringWithSubstring(model, font: .body2Regular),
                    font: .body2Regular
                )
            )
            
        case let model as LabelModelLinkSubstringModel:
            ModeledText(
                model: .linkedText(
                    textContent: .linkedText(string: model.markdownString(), links: model.linkedSubstrings),
                    font: FontTheme.body2Regular
                )
            )

        default:
            fatalError("Unexpected Kotlin LabelModel")
        }
    }
}
