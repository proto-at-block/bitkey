import Shared
import SwiftUI

/**
 * A clickable `IconView` for the bitkey device 3D icon which shows the `BitkeyDevice3dView`
 */
struct BitkeyDevice3dClickableIconView: View {
    @ViewBuilder
    let iconView: () -> AnyView

    @SwiftUI.State
    private var isShowingBitkeyDevice3dView = false

    var body: some View {
        iconView()
            .onTapGesture { isShowingBitkeyDevice3dView = true }
            .popover(isPresented: $isShowingBitkeyDevice3dView) {
                BitkeyDevice3dView(
                    onClose: { isShowingBitkeyDevice3dView = false }
                )
            }
    }
}
