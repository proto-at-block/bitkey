import Foundation
import Shared
import SwiftUI

// MARK: -

public struct FormHeaderView: View {

    // MARK: - Public Types

    public enum HeadlineFont {
        case title1 // Used in full screen forms
        case title2 // Used in bottom sheet forms

        var font: FontTheme {
            switch self {
            case .title1: return FontTheme.title1
            case .title2: return FontTheme.title2
            }
        }
    }

    // MARK: - Private Properties

    private let headlineFont: HeadlineFont
    private let headlineTextColor: Color
    private let sublineTextColor: Color

    private let viewModel: FormHeaderModel

    // MARK: - Life Cycle

    public init(
        viewModel: FormHeaderModel,
        headlineFont: HeadlineFont,
        headlineTextColor: Color = .foreground,
        sublineTextColor: Color = .foreground60
    ) {
        self.viewModel = viewModel
        self.headlineFont = headlineFont
        self.headlineTextColor = headlineTextColor
        self.sublineTextColor = sublineTextColor
    }

    // MARK: - View

    public var body: some View {
        VStack(alignment: viewModel.alignment.alignment) {
            viewModel.iconModel.map { iconModel in
                VStack {
                    IconView(model: iconModel)
                    Spacer()
                        .frame(height: viewModel.alignment == .leading ? 16 : 8)
                }
            }

            viewModel.headline.map { headline in
                ModeledText(
                    model: .standard(
                        headline,
                        font: headlineFont.font,
                        textAlignment: viewModel.alignment.textAlignment,
                        textColor: headlineTextColor
                    )
                ).fixedSize(horizontal: false, vertical: true)
            }

            viewModel.sublineModel.map { subline in
                VStack {
                    Spacer()
                        .frame(height: 8)
                    ModeledText(
                        model: .standard(
                            .stringWithSubstring(subline, font: viewModel.sublineTreatment.font),
                            font: viewModel.sublineTreatment.font,
                            textAlignment: viewModel.alignment.textAlignment,
                            textColor: sublineTextColor
                        )
                    ).fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

}

// MARK: -

private extension FormHeaderModel.Alignment {

    var alignment: HorizontalAlignment {
        switch self {
        case .center: return .center
        case .leading: return .leading
        default: fatalError("Unsupported alignment")
        }
    }

    var textAlignment: TextAlignment {
        switch self {
        case .center: return .center
        case .leading: return .leading
        default: fatalError("Unsupported alignment")
        }
    }

}

// MARK: -

private extension FormHeaderModel.SublineTreatment {

    var font: FontTheme {
        switch self {
        case .regular: return .body2Regular
        case .mono: return .body2Mono
        case .small: return .body3Regular
        default: fatalError("Unsupported subline treatment")
        }
    }

}
