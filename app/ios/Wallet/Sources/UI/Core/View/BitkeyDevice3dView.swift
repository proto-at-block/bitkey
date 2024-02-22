import Shared
import SwiftUI
import SceneKit

/**
 * View showing a full screen 3D SceneView of the Bitkey Device that can be interacted with.
 * iOS only, no Android equivalent.
 */
struct BitkeyDevice3dView: View {
    @SwiftUI.State
    private var isSceneLoading: Bool = true

    var onClose: () -> Void
    var body: some View {
        ZStack(alignment: .center) {
            // Bottom of the ZStack: the scene view, hidden for an arbitrary amount of time to let it first load
            SceneView(scene: SCNScene(named: "bitkey.scn"),
                options: [ .autoenablesDefaultLighting, .temporalAntialiasingEnabled, .allowsCameraControl ]
            )
            .opacity(isSceneLoading ? 0 : 1)
            .transition(.opacity)

            // Middle of ZStack: a loading icon, only shown while we delay showing the SceneView to let it load
            if isSceneLoading {
                RotatingLoadingIcon(size: .avatar, tint: .white)
                    .opacity(0.2)
                    .transition(.opacity)
            }

            // Top of ZStack: toolbar view to show a close button
            ToolbarScreenView(onClose: onClose)
        }
        .background(.black)
        .onAppear {
            // Delay showing the SceneView for 0.6 seconds to allow it to fully load
            // Otherwise it will pop in
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
                withAnimation {
                    isSceneLoading = false
                }
            }
        }
    }
}

// MARK: -

private struct ToolbarScreenView: View {
    var onClose: () -> Void
    var body: some View {
        VStack {
            ToolbarView(
                viewModel: .init(
                    leadingAccessory: .IconAccessory(
                        model: .init(
                            iconModel: .init(
                                iconImage: .LocalImage(icon: .smalliconx),
                                iconSize: .accessory,
                                iconBackgroundType: IconBackgroundTypeCircle(
                                    circleSize: .regular,
                                    color: .translucentwhite
                                ),
                                iconTint: .ontranslucent,
                                text: nil
                            ),
                            onClick: ClickCompanion.shared.standardClick(onClick: onClose),
                            enabled: true
                        )
                    ),
                    middleAccessory: nil,
                    trailingAccessory: nil
                )
            )
            .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
            .padding(.vertical, DesignSystemMetrics.verticalPadding)

            Spacer()
        }
    }
}
