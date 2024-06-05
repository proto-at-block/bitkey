
import Shared
import SwiftUI

/*
 A toast is a component that has a leading icon and title text
 It displays temporarily at the bottom of the screen with an animated appearance and dismissal
 */
struct ToastView: View {
    
    private let model: ToastModel
    
    init(
        model: ToastModel
    ) {
        self.model = model
    }
    
    
    var body: some View {
        VStack {
            Spacer()
            
            // Curved left and right corners on the top of the toast
            HStack {
                IconView(model:
                        .init(
                            iconImage: .LocalImage(icon: .subtractleft),
                            iconSize: .accessory,
                            iconBackgroundType: IconBackgroundTypeTransient(),
                            iconTint: nil,
                            iconOpacity: 1.00,
                            iconTopSpacing: nil,
                            text: nil
                        )
                )
                
                Spacer()

                IconView(model:
                        .init(
                            iconImage: .LocalImage(icon: .subtractright),
                            iconSize: .accessory,
                            iconBackgroundType: IconBackgroundTypeTransient(),
                            iconTint: nil,
                            iconOpacity: 1.00,
                            iconTopSpacing: nil,
                            text: nil
                        )
                )
            }
            .frame(maxWidth: .infinity, maxHeight: 18)
            .offset(CGSize(width: 0, height: 7))
            
            ZStack {
                HStack {
                    if let leadingIcon = model.leadingIcon {
                        IconView(model: leadingIcon)
                            .padding(.vertical, 18)
                            .padding(.horizontal, 8)
                            .if(model.whiteIconStroke, transform: { view in
                                view
                                    .frame(
                                        width: CGFloat(leadingIcon.iconSize.value - 4),
                                        height: CGFloat(leadingIcon.iconSize.value - 4))
                                    .background(Color.white)
                                    .clipShape(Circle())
                            })
                    }
                    
                    Text(model.title)
                        .font(FontTheme.body2Medium.font)
                        .foregroundColor(.white)
                        .padding(.vertical, 18)
                }
                .frame(maxWidth: .infinity, idealHeight: 76, alignment: .leading)
                .padding(EdgeInsets(top: 0, leading: 24, bottom: 24, trailing: 0))
            }
            .background(Color.black)
            .frame(maxWidth: .infinity)
        }
        .ignoresSafeArea()
        .frame(maxWidth: .infinity)
    }
}

struct ToastView_Previews: PreviewProvider {
    static var previews: some View {
        ToastView(model: .init(
                leadingIcon: IconModel(
                    iconImage: .LocalImage(icon: .smalliconcheckfilled),
                    iconSize: .accessory,
                    iconBackgroundType: IconBackgroundTypeTransient(),
                    iconTint: IconTint.success,
                    iconOpacity: 1.00,
                    iconTopSpacing: nil,
                    text: nil
                ),
                whiteIconStroke: true,
                title: "Fingerprint deleted",
                id: UUID().uuidString
            )
        )
    }
}

// Custom transition for toast dismissal
extension AnyTransition {
    static var moveOutToBottom: AnyTransition {
        AnyTransition.asymmetric(
            insertion: .move(edge: .bottom),
            removal: .move(edge: .top)
        )
    }
}
