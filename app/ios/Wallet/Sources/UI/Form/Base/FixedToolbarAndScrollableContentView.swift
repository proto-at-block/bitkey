import Shared
import SwiftUI

// MARK: -

/**
 * A view wrapper that shows a fixed toolbar at the top of the screen and content that scrolls underneath when necessary.
 */
public struct FixedToolbarAndScrollableContentView<Toolbar: View, Content: View>: View {

    // MARK: - Public Properties

    @ViewBuilder
    public let toolbar: () -> Toolbar

    @ViewBuilder
    public let content: () -> Content

    // MARK: - Private Properties

    @SwiftUI.State
    private var contentDoesOverflow = false

    /// The opacity of the toolbar view shown overlaid the main content with a blurred background
    /// Transitions to 1 as the view scrolls.
    @SwiftUI.State
    private var overlayToolbarOpacity = 0.f

    // MARK: - View

    public var body: some View {
        VStack {
            GeometryReader { geometry in
                ZStack {
                    // Bottom of the ZStack: the content and space for the toolbar
                    VStack(spacing: 16) {
                        // Toolbar
                        toolbar()

                        // Content
                        content()
                            .background {
                                GeometryReader { contentGeometry in
                                    Color.clear.onAppear {
                                        contentDoesOverflow = contentGeometry.size.height > geometry
                                            .size.height
                                    }
                                }
                            }
                            .wrappedInScrollView(when: contentDoesOverflow) { scrollOffset in
                                // Show the overlay toolbar with a blurred background when the view
                                // scrolls
                                overlayToolbarOpacity = modulate(
                                    watchedViewValue: scrollOffset,
                                    watchedViewStart: 16,
                                    watchedViewEnd: 0,
                                    appliedViewStart: 0,
                                    appliedViewEnd: 1.0,
                                    limit: false
                                )
                            }

                        Spacer()
                    }

                    // Top of the ZStack: the toolbar with a blurred effect
                    VStack {
                        ZStack {
                            VisualEffectView(effect: UIBlurEffect(style: .light))
                                .ignoresSafeArea()
                                .frame(height: DesignSystemMetrics.toolbarHeight)
                            toolbar()
                        }
                        Spacer()
                    }
                    .opacity(overlayToolbarOpacity)
                }
            }

            Spacer()
        }
    }

}

// MARK: -

private extension View {
    @ViewBuilder
    func wrappedInScrollView(
        when condition: Bool,
        onScroll: @escaping (_ scrollOffset: CGFloat) -> Void
    ) -> some View {
        if condition {
            ScrollView(showsIndicators: false) {
                self
                    .background {
                        GeometryReader { geometry in
                            Color.clear.preference(
                                key: ViewPreferenceKey.self,
                                value: geometry.frame(in: .named(UUID())).origin
                            )
                        }
                    }
                    .onPreferenceChange(ViewPreferenceKey.self) { position in
                        onScroll(position.y)
                    }
            }
        } else {
            self
        }
    }
}

private struct ViewPreferenceKey: SwiftUI.PreferenceKey {
    static var defaultValue: CGPoint { .zero }
    static func reduce(value _: inout CGPoint, nextValue _: () -> CGPoint) {
        // No-op
    }
}
