import Foundation
import Shared
import SwiftUI

// MARK: -

public struct CardView: View {

    // MARK: - Private Properties

    @SwiftUI.State
    private var scaleEffect = 1.f

    // MARK: - Public Properties

    public var viewModel: CardModel

    @Binding
    public var height: CGFloat?
    
    @Binding
    public var titleSubtitleToIconSpacing: CGFloat?
    
    init(
        scaleEffect: CGFloat = 1.f,
        viewModel: CardModel,
        withHeight: Binding<CGFloat?> = .constant(nil),
        withTitleSubtitleToIconSpacing: Binding<CGFloat?> = .constant(nil)
    ) {
        self.scaleEffect = scaleEffect
        self.viewModel = viewModel
        self._height = withHeight
        self._titleSubtitleToIconSpacing = withTitleSubtitleToIconSpacing
    }
    
    // MARK: - View

    public var body: some View {
        CardContentView(
            viewModel: viewModel,
            overridenTitleToSubtitleSpacing: titleSubtitleToIconSpacing
        )
            .background(style: viewModel.style)
            .frame(maxWidth: .infinity)
            .scaleEffect(scaleEffect)
            .frame(height: height)
            .onTapGesture {
                viewModel.onClick?()
            }
            .onChange(of: viewModel.animation) { animation in
                guard let animationList = animation else {
                    return
                }

                var waitDuration: TimeInterval = 0
                for animationSet in animationList {
                    DispatchQueue.main.asyncAfter(deadline: .now() + waitDuration) {
                        withAnimation(.easeInOut(duration: animationSet.durationInSeconds)) {
                            for animation in animationSet.animations {
                                switch animation {
                                case let heightAnimation as CardModelAnimationSetAnimationHeight:
                                    height = CGFloat(heightAnimation.value)
                                case let scaleAnimation as CardModelAnimationSetAnimationScale:
                                    scaleEffect = CGFloat(scaleAnimation.value)
                                default:
                                    break
                                }
                            }
                        }
                    }
                    waitDuration += animationSet.durationInSeconds
                }
            }
    }

}

// MARK: -

private extension View {
    @ViewBuilder
    func background(style: CardModel.CardStyle) -> some View {
        switch style {
        case _ as CardModel.CardStyleOutline:
            self
                .background(.background)
                .cornerRadius(16)
                .shadow(color: .black.opacity(0.10), radius: 1, x: 0, y: 0)
                .shadow(color: .black.opacity(0.04), radius: 8, x: 0, y: 3)

        case let gradient as CardModel.CardStyleGradient:
            let backgroundColor = switch gradient.backgroundColor {
            case .some(.warning):
                Color.warning
            default:
                Color(red: 0.96, green: 0.97, blue: 1)
            }
            self
                .background(backgroundColor)
                .cornerRadius(16)
                .shadow(color: Color(red: 0.3, green: 0.33, blue: 0.37).opacity(0.04), radius: 4, x: 0, y: 3)
                .shadow(color: Color(red: 0.74, green: 0.81, blue: 0.94), radius: 0.5, x: 0, y: 0)

        default:
            fatalError("Unexpected card style: \(style)")
        }
    }
}

// MARK: -

struct CardView_Previews: PreviewProvider {
    static var previews: some View {
        VStack(spacing: 12) {
            CardView(
                viewModel: DeviceUpdateCardModelKt.DeviceUpdateCardModel(onUpdateDevice: {})
            )

            CardView(
                viewModel: CloudBackupHealthCardModelKt.CloudBackupHealthCardModel(
                    title: "Problem with iCloud\naccount access", onActionClick: {})
            )

            CardView(
                viewModel: GettingStartedCardModelKt.GettingStartedCardModel(
                    animations: nil,
                    taskModels: [
                        .init(task: .init(id: .invitetrustedcontact, state: .complete), isEnabled: true, onClick: {}),
                        .init(task: .init(id: .enablespendinglimit, state: .incomplete), isEnabled: true, onClick: {}),
                        .init(task: .init(id: .addbitcoin, state: .incomplete), isEnabled: true, onClick: {}),
                        .init(task: .init(id: .addadditionalfingerprint, state: .incomplete), isEnabled: true, onClick: {})
                    ]
                )
            )
            
            CardView(
                viewModel: .init(
                    heroImage: nil,
                    title: LabelModelStringWithStyledSubstringModel.Companion()
                        .from(string: "Bitkey approval required", boldedSubstrings: []),
                    subtitle: nil,
                    leadingImage: CardModelCardImageStaticImage(icon: .smalliconbitkey, iconTint: nil),
                    trailingButton: nil,
                    content: nil,
                    style: CardModel.CardStyleOutline(),
                    onClick: nil,
                    animation: nil
                )
            )
        }.padding(20)
    }
}
