import Foundation
import Shared
import SwiftUI

// MARK: -

public struct ToolbarView: View {

    // MARK: - Public Properties

    public var viewModel: ToolbarModel

    // MARK: - Life Cycle

    public init(viewModel: ToolbarModel) {
        self.viewModel = viewModel
    }

    // MARK: - View

    public var body: some View {
        HStack {
            viewModel.leadingAccessory.viewOrPlaceholder
            Spacer()
            viewModel.middleAccessory.map { middleAccessory in
                VStack(spacing: 2) {
                    ModeledText(model: .standard(
                        middleAccessory.title,
                        font: .title2,
                        textAlignment: .center
                    ))
                    middleAccessory.subtitle.map {
                        ModeledText(model: .standard(
                            $0,
                            font: .title3,
                            textAlignment: .center,
                            textColor: .mask
                        ))
                    }
                }.frame(maxWidth: .infinity)
            }
            Spacer()
            viewModel.trailingAccessory.viewOrPlaceholder
        }
        .frame(height: DesignSystemMetrics.toolbarHeight)
    }

}

// MARK: -

public struct ToolbarIconView: View {

    // MARK: - Public Types

    public enum Metrics {
        static let size = 32.f
    }

    // MARK: - Private Properties

    private let viewModel: IconButtonModel

    // MARK: - Life Cycle

    public init(viewModel: IconButtonModel) {
        self.viewModel = viewModel
    }

    // MARK: - View

    public var body: some View {
        Button(action: viewModel.onClick.invoke) {
            IconView(model: viewModel.iconModel)
        }
    }

}

// MARK: -

public struct ToolbarAccessoryView: View {

    let viewModel: ToolbarAccessoryModel

    public var body: some View {
        switch viewModel {
        case let accessory as ToolbarAccessoryModel.IconAccessory:
            ToolbarIconView(viewModel: accessory.model)

        case let accessory as ToolbarAccessoryModel.ButtonAccessory:
            ButtonView(model: accessory.model)
                .frame(height: 32)

        default:
            fatalError("Unhandled toolbar leading accessory")
        }
    }

}

// MARK: -

extension ToolbarAccessoryModel? {

    var viewOrPlaceholder: some View {
        guard let model = self else {
            // Returning a placeholder space keeps the header text actually centered
            return AnyView(Spacer(minLength: ToolbarIconView.Metrics.size))
        }

        return AnyView(ToolbarAccessoryView(viewModel: model))
    }

}
