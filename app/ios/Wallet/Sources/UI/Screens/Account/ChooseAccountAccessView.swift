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
        ZStack {
            // Bottom of ZStack: background video
            BackgroundVideoView()

            // Top of ZStack: logo, text and buttons
            VStack(spacing: 0) {
                // Logo
                HStack {
                    Button {
                        viewModel.onLogoClick()
                    } label: {
                        Image(uiImage: .bitkeyFullLogo)
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(height: 25)
                            .foregroundColor(.white)
                            .accessibility(identifier: "logo")
                    }
                    Spacer()
                }
                .padding(.horizontal, DesignSystemMetrics.horizontalPadding)

                Spacer()

                VStack {
                    VStack {
                        ModeledText(model: .standard(viewModel.title, font: .display3, textColor: .white))

                        Spacer()
                            .frame(height: 4)

                        ModeledText(model: .standard(viewModel.subtitle, font: .body2Regular, textColor: .white))

                        Spacer()
                            .frame(height: 16)

                        VStack(spacing: 16) {
                            ForEach(viewModel.buttons, id: \.text) {
                                ButtonView(model: $0)
                            }
                        }
                    }
                    .padding(.horizontal, DesignSystemMetrics.horizontalPadding)

                }.background(
                    LinearGradient(
                        gradient: .init(colors: [.clear, .black.opacity(0.8)]),
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
            }
        }
    }
}

private struct BackgroundVideoView : View {
    private let player = AVPlayer(url: .welcomeVideoUrl)
    var body: some View {
        VideoView(videoPlayer: player, videoGravity: .resizeAspectFill)
           .frame(maxHeight: .infinity)
           .ignoresSafeArea()
           .onAppear {
               // Set up observer to loop video
               NotificationCenter.default.addObserver(
                   forName: .AVPlayerItemDidPlayToEndTime,
                   object: player.currentItem,
                   queue: nil
               ) { notification in
                   player.seek(to: .zero)
                   player.play()
               }
           }
           .onDisappear {
               // Remove observer used to loop video
               NotificationCenter.default.removeObserver(
                   self,
                   name: .AVPlayerItemDidPlayToEndTime,
                   object: player.currentItem
               )
           }
    }
}
