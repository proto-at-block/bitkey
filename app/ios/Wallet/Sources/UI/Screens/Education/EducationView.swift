import Foundation
import Shared
import SwiftUI

public struct EducationView: View {
    
    // MARK: - Private Properties

    private let viewModel: EducationBodyModel
    
    // MARK: - Life Cycle

    public init(viewModel: EducationBodyModel) {
        self.viewModel = viewModel
    }
    
    public var body: some View {
        VStack(alignment: .center) {
            ExplainerToolbar(viewModel: viewModel)
            EducationItemView(item: viewModel)
                .transition(.opacity.animation(.linear))
                .id(viewModel.title)
                .contentShape(Rectangle()) // this helps ensure the blank area is tappable as well
                .onTapGesture {
                    viewModel.onClick()
                }
        }.padding(20)
    }
}

// MARK: -

private struct ExplainerToolbar: View {
    
    // MARK: - Private Properties

    private let viewModel: EducationBodyModel
    
    // MARK: - Life Cycle

    public init(viewModel: EducationBodyModel) {
        self.viewModel = viewModel
    }
    
    var body: some View {
        HStack {
            IconButtonView(model: 
                .init(
                    iconModel: .init(
                        iconImage: .LocalImage(icon: .smalliconx),
                        iconSize: .accessory,
                        iconBackgroundType: IconBackgroundTypeCircle(
                            circleSize: .regular,
                            color: .foreground10
                        ),
                        iconTint: nil,
                        iconOpacity: nil,
                        iconTopSpacing: nil,
                        text: nil
                    ),
                    onClick: StandardClick { viewModel.onDismiss() } ,
                    enabled: true
                )
            )
            Spacer(minLength: 20)
            ProgressView(value: viewModel.progressPercentage)
                .frame(height: 8.0)
                .scaleEffect(x: 1, y: 2, anchor: .center)
                .clipShape(RoundedRectangle(cornerRadius: 4))
                .tint(.primary)
            Spacer(minLength: 52)
        }
    }
}

// MARK: -

private struct EducationItemView : View {
    
    private let item: EducationBodyModel
    
    public init(item: EducationBodyModel) {
        self.item = item
    }
    
    var body: some View {
        VStack {
            Spacer()

            ModeledText(model: .standard(item.title, font: .body1Medium, textAlignment: .center))
            if let subtitle = item.subtitle {
                ModeledText(model: .standard(subtitle, font: .body2Regular, textAlignment: .center, textColor: .foreground60))
                    .padding(.top, 16)
            }
            
            Spacer()
            
            VStack {
                if let primaryButton = item.primaryButton {
                    ButtonView(model: primaryButton)
                }
                if let secondaryButton = item.secondaryButton {
                    ButtonView(model: secondaryButton)
                }
            }
        }
    }
}
