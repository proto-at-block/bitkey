import Shared
import SwiftUI

// MARK: -

public struct FwupInstructionsView: View {

    // MARK: - Public Properties

    public var viewModel: FwupInstructionsBodyModel

    // MARK: - Lifecycle

    public init(viewModel: FwupInstructionsBodyModel) {
        self.viewModel = viewModel
    }

    // MARK: - View

    public var body: some View {
        GeometryReader { reader in
            VStack {
                ToolbarView(viewModel: viewModel.toolbarModel)
                    .padding(
                        .top,
                        reader.safeAreaInsets.top == 0 ? DesignSystemMetrics.verticalPadding : 0
                    )
                    .padding(.horizontal, DesignSystemMetrics.horizontalPadding)

                Spacer()

                ZStack {
                    Rectangle()
                        .fill(.white)
                        .roundTopCorners(radius: 30)
                        .ignoresSafeArea()

                    ContentView(viewModel: viewModel)
                        .padding(.top, 32)
                        .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
                        .padding(.bottom, DesignSystemMetrics.verticalPadding)
                }
                .fixedSize(horizontal: false, vertical: true)
            }
            .background {
                VStack {
                    Spacer()
                    VideoView(viewModel: .init(videoUrl: .bitkeyPairVideoUrl))
                        .frame(height: 640)
                        .padding(
                            .horizontal,
                            -100
                        ) // Let the video width extend beyond the edges a bit
                        .clipped()
                    Spacer(minLength: 250) // The minLength ensures enough space on smaller devices
                }
            }
            .background(.black)
        }
    }

}

// MARK: -

private struct ContentView: View {

    let viewModel: FwupInstructionsBodyModel

    var body: some View {
        VStack {
            FormHeaderView(
                viewModel: viewModel.headerModel,
                headlineFont: .title1,
                sublineTextColor: .foreground
            )
            Spacer()
                .frame(height: 32)
            ButtonView(model: viewModel.buttonModel)
        }
    }

}

// MARK: -

extension View {
    func roundTopCorners(radius: Int) -> some View {
        clipShape(RoundedCorners(radius: 30, corners: [.topLeft, .topRight]))
    }
}

struct RoundedCorners: Shape {
    let radius: Int
    let corners: UIRectCorner

    func path(in rect: CGRect) -> Path {
        Path(
            UIBezierPath(
                roundedRect: rect,
                byRoundingCorners: corners,
                cornerRadii: .init(width: radius, height: radius)
            ).cgPath
        )
    }
}
