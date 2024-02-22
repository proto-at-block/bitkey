import Foundation
import Shared
import SwiftUI

// MARK: -

public struct LoadingView: View {

    // MARK: - Private Properties

    private let viewModel: LoadingBodyModel

    // MARK: - Life Cycle

    public init(viewModel: LoadingBodyModel) {
        self.viewModel = viewModel
    }

    // MARK: - View

    public var body: some View {
        switch viewModel.style {
        case _ as LoadingBodyModelStyleExplicit:
            ExplicitLoadingView(viewModel: viewModel)

        case _ as LoadingBodyModelStyleImplicit:
            ImplicitLoadingView(viewModel: viewModel)

        default:
            fatalError("Unexpected success screen style case")
        }
    }
}

// MARK: -

struct ExplicitLoadingView: View {
    let viewModel: LoadingBodyModel
    var body: some View {
        VStack(spacing: 16) {
            Spacer()
                .frame(height: 24)
            RotatingLoadingIcon(size: .avatar, tint: .black)
                .frame(maxWidth: .infinity, alignment: .leading)
            viewModel.message.map {
                ModeledText(model: .standard($0, font: .title1))
            }
            Spacer()
        }
        .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
        .padding(.vertical, DesignSystemMetrics.verticalPadding)
    }
}

// MARK: -

struct ImplicitLoadingView: View {
    let viewModel: LoadingBodyModel
    var body: some View {
        VStack(alignment: .center, spacing: 16) {
            Spacer()
            RotatingLoadingIcon(size: .avatar, tint: .black)
            viewModel.message.map {
                ModeledText(model: .standard($0, font: .title1, textAlignment: .center))
            }
            Spacer()
        }
    }
}
