import Foundation
import Shared
import SwiftUI

// MARK: -

public struct FormContentView: View {

    // MARK: - Private Properties

    public let toolbarModel: ToolbarModel?
    public let headerModel: FormHeaderModel?
    public let mainContentList: [FormMainContentModel]
    public let renderContext: RenderContext

    // MARK: - View

    public var body: some View {
        // Toolbar
        if let toolbar = toolbarModel {
            ToolbarView(viewModel: toolbar)
        } else if renderContext == .screen {
            Spacer()
                .frame(height: DesignSystemMetrics.toolbarHeight)
        }

        Spacer()
            .frame(height: 16)

        // Header
        headerModel.map { header in
            VStack {
                FormHeaderView(
                    viewModel: header,
                    headlineFont: {
                        switch renderContext {
                        case .screen: return .title1
                        case .sheet: return .title2
                        default: return .title1
                        }
                    }()
                )
                Spacer()
                    .frame(height: 24)
            }
        }

        // Main Content
        VStack(spacing: 24) {
            ForEach(Array(zip(mainContentList.indices, mainContentList)), id: \.0) { _, mainContent in
                FormMainContentView(viewModel: mainContent)
            }
        }
    }

}
