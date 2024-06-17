import AVKit
import Foundation
import Shared
import SwiftUI

// MARK: -

public struct ChooseAccountAccessView: View {

    // MARK: - Private Properties

    private let viewModel: ChooseAccountAccessModel

    // MARK: - Life Cycle

    public init(viewModel: ChooseAccountAccessModel) {
        self.viewModel = viewModel
    }

    // MARK: - View

    public var body: some View {
        GeometryReader { reader in
            ZStack {
                // Bottom of ZStack: background video
                BackgroundVideoView()

                // Top of ZStack: logo, text and buttons
                VStack(spacing: 0) {
                    // Logo
                    HStack {
                        Image(uiImage: .bitkeyFullLogo)
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(height: 25)
                            .foregroundColor(.white)
                            .onTapGesture(perform: viewModel.onLogoClick)
                            .accessibility(identifier: "logo")
                        Spacer()
                    }
                    .padding(.horizontal, DesignSystemMetrics.horizontalPadding)

                    Spacer()

                    // Text and buttons
                    VStack {
                        VStack {
                            ModeledText(model: .standard(
                                viewModel.title,
                                font: .display3,
                                textColor: .white
                            ))

                            Spacer()
                                .frame(height: 4)

                            ModeledText(model: .standard(
                                viewModel.subtitle,
                                font: .body2Regular,
                                textColor: .white
                            ))

                            Spacer()
                                .frame(height: 16)

                            VStack(spacing: 16) {
                                ForEach(viewModel.buttons, id: \.text) {
                                    ButtonView(model: $0)
                                }
                            }
                        }
                        .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
                    }
                    .padding(
                        .bottom,
                        reader.safeAreaInsets.bottom == 0 ? DesignSystemMetrics.verticalPadding : 0
                    )
                    .background(
                        LinearGradient(
                            gradient: .init(colors: [.clear, .black.opacity(0.8)]),
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                }
                .padding(
                    .top,
                    reader.safeAreaInsets.top == 0 ? DesignSystemMetrics.verticalPadding : 0
                )
            }
        }
    }
}

private struct BackgroundVideoView: View {
    var body: some View {
        VideoView(viewModel: .init(
            videoUrl: .welcomeVideoUrl,
            videoGravity: .resizeAspectFill,
            videoShouldLoop: true,
            videoShouldPauseOnResignActive: true
        ))
        .frame(maxHeight: .infinity)
        .ignoresSafeArea()
    }
}
