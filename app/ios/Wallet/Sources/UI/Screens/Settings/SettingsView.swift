import Shared
import SwiftUI

// MARK: -

public struct SettingsView: View {

    // MARK: - Private Properties

    private let viewModel: SettingsBodyModel

    // MARK: - Life Cycle

    public init(viewModel: SettingsBodyModel) {
        self.viewModel = viewModel
    }

    // MARK: - View

    public var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 0) {
                ToolbarView(viewModel: viewModel.toolbarModel)

                VStack(spacing: 32) {
                    ForEach(viewModel.sectionModels, id: \.self.sectionHeaderTitle) { sectionModel in
                        SectionView(model: sectionModel)
                    }
                }.padding(.top, 16)

                Spacer()
            }
            .padding(.horizontal, 20)
        }
        .navigationBarHidden(true)
    }

}

// MARK: -

private struct SectionView: View {

    var model: SettingsBodyModel.SectionModel

    var body: some View {
        VStack(spacing: 0) {
            ModeledText(model: .standard(model.sectionHeaderTitle, font: .title3, textColor: .foreground60))
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, 8)

            ForEach(model.rowModels, id: \.self.title) { rowModel in
                VStack {
                    RowView(model: rowModel)
                        .padding(.top, 16)
                    // Manually add a separator since it behaves differently in iOS 15.0 (extra divider at top)
                    // and 16.0 and doesn't extend to the edges of the cell
                    Spacer()
                    Rectangle()
                        .fill(Color.foreground10)
                        .frame(height: 1)
                }
                .frame(minHeight: 56)
            }
        }
    }

}

// MARK: -

private struct RowView: View {

    var model: SettingsBodyModel.RowModel

    var body: some View {
        Button(action: model.onClick) {
            HStack {
                Image(uiImage: model.icon.uiImage)
                    .resizable()
                    .foregroundColor(model.isDisabled ? Color.foreground30 : Color.foreground)
                    .frame(width: 24, height: 24)
                Spacer(minLength: 8)
                ModeledText(model: .standard(model.title, font: .body2Medium, textColor: model.isDisabled ? Color.foreground30 : Color.foreground))
                    .frame(maxWidth: .infinity, alignment: .leading)
                if !model.isDisabled {
                    Spacer(minLength: 8)
                    Image(uiImage: .smallIconCaretRight)
                        .foregroundColor(.primaryForeground30)
                }
            }
        }
    }

}
