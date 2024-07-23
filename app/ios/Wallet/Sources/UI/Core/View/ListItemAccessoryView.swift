import Foundation
import Shared
import SwiftUI

// MARK: -

public struct ListItemAccessoryView: View {

    // MARK: - Public Properties

    public let viewModel: ListItemAccessory

    // MARK: - Life Cycle

    public init(viewModel: ListItemAccessory) {
        self.viewModel = viewModel
    }

    // MARK: - View

    public var body: some View {
        switch viewModel {
        case let accessory as ListItemAccessoryIconAccessory:
            if let iconOnClick = accessory.onClick {
                Button {
                    iconOnClick()
                } label: {
                    IconView(model: accessory.model)
                        .padding(.all, accessory.iconPadding?.intValue.f ?? 0)
                }
            } else {
                IconView(model: accessory.model)
                    .padding(.all, accessory.iconPadding?.intValue.f ?? 0)
            }

        case let accessory as ListItemAccessorySwitchAccessory:
            switchAccessoryView(model: accessory.model)

        case let accessory as ListItemAccessoryButtonAccessory:
            ButtonView(model: accessory.model)

        case let accessory as ListItemAccessoryTextAccessory:
            ModeledText(model: .standard(accessory.text, font: .body2Regular, textAlignment: nil))
                .padding(.trailing, 12)

        case let accessory as ListItemAccessoryCircularCharacterAccessory:
            CircularCharacterAccessoryView(viewModel: accessory)
                .padding(.trailing, 4)

        default:
            fatalError("Unhandled ListItemAccessory case: \(viewModel)")
        }
    }

    // MARK: - Private Methods

    private func switchAccessoryView(model: SwitchModel) -> some View {
        Toggle(
            "",
            isOn: .init(
                get: { model.checked },
                set: { newValue in model.onCheckedChange(.init(bool: newValue)) }
            )
        )
        .tint(.bitkeyPrimary)
        .labelsHidden()
        .disabled(!model.enabled)
        .ifNonnull(model.testTag) { view, testTag in
            view.accessibilityIdentifier(testTag)
        }
    }

}

private struct CircularCharacterAccessoryView: View {
    let viewModel: ListItemAccessoryCircularCharacterAccessory
    var body: some View {
        ModeledText(model: .standard(viewModel.text, font: .label3, textAlignment: nil))
            .frame(width: 24, height: 24)
            .background(Circle().foregroundColor(Color.foreground10))
    }
}
