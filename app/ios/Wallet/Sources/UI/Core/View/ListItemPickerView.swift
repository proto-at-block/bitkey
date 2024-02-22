import Foundation
import Shared
import SwiftUI

// MARK: -

public struct ListItemPickerView<Option: AnyObject>: View {

    let viewModel: ListItemPickerMenu<Option>

    @ViewBuilder
    let content: () -> ListItemContentView

    public var body: some View {
        Menu {
            Picker(
                selection: .init(get: { viewModel.selectedItem }, set: { newOption in
                    if let newOption {
                        viewModel.onOptionSelected(newOption.option)
                    }
                }),
                label: EmptyView()
            ) {
                ForEach(viewModel.items, id: \.self) { option in
                    Text(viewModel.titleSelector(option.option))
                        .tag(Optional.some(option))
                }
            }
            .pickerStyle(.inline)
        } label: {
            content()
        }
    }

}
