import Shared
import SwiftUI

struct ItemPickerView: View {
    private enum Metrics {
        static let height = 56.f
        static let cornerRadius = 32.f
    }

    private let viewModel: FormMainContentModel.Picker

    init(viewModel: FormMainContentModel.Picker) {
        self.viewModel = viewModel
    }

    var body: some View {
        VStack(spacing: 8) {
            if let title = viewModel.title {
                ModeledText(model: .standard(title, font: .title2))
            }
            Menu {
                Picker(
                    selection: .init(get: { viewModel.fieldModel.selectedItem }, set: { newOption in
                        if let newOption {
                            viewModel.fieldModel.onOptionSelected(newOption.option)
                        }
                    }),
                    label: EmptyView()
                ) {
                    ForEach(viewModel.fieldModel.items, id: \.self) { option in
                        Text(viewModel.fieldModel.titleSelector(option.option))
                            .tag(Optional.some(option))
                    }
                }
                .pickerStyle(.inline)
            } label: {
                Text(viewModel.fieldModel.titleSelector(viewModel.fieldModel.selectedOption))
                    .frame(height: Metrics.height)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal)
                    .background(
                        RoundedRectangle(cornerRadius: Metrics.cornerRadius)
                            .fill(Color.foreground10)
                    )
                    .foregroundColor(.foreground)
                    .tint(.foreground)
            }
        }
    }
}
