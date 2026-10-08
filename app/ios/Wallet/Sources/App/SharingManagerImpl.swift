import Shared
import UIKit

class SharingManagerImpl: SharingManager {
    private let presentationContext: ScenePresentationContext

    init(presentationContext: ScenePresentationContext) {
        self.presentationContext = presentationContext
    }

    // MARK: - SharingManager

    func shareText(text: String, title _: String, completion: ((KotlinBoolean) -> Void)? = nil) {
        let ac = UIActivityViewController(activityItems: [text], applicationActivities: nil)
        ac.completionWithItemsHandler = { _, completed, _, _ in
            if completed {
                completion?(KotlinBoolean(bool: completed))
            }
        }
        guard let presenter = topViewController() else {
            completion?(false)
            return
        }
        // Inherit theme from the root view controller (which respects app's theme preference)
        ac.overrideUserInterfaceStyle = presenter.overrideUserInterfaceStyle
        presenter.present(ac, animated: true)
    }

    func shareData(
        data: OkioByteString,
        mimeType: MimeType,
        title: String,
        completion: ((KotlinBoolean) -> Void)? = nil
    ) {
        guard let ext = mimeType.ext else {
            completion?(false)
            log(.error) { "Cannot share data of type \(mimeType.name)" }
            return
        }
        let fileName = "\(title).\(ext)"

        // Create a temporary file URL for the PDF data
        let tempDir = NSTemporaryDirectory()

        let tempFileURL = URL(fileURLWithPath: tempDir).appendingPathComponent(fileName)

        do {
            // Write the data to the temporary file
            try data.toData().write(to: tempFileURL)

            let ac = UIActivityViewController(
                activityItems: [tempFileURL],
                applicationActivities: nil
            )

            ac.completionWithItemsHandler = { _, completed, _, _ in
                if completed {
                    completion?(KotlinBoolean(bool: completed))
                }

                // Cleanup: Remove the temporary file after sharing
                try? FileManager.default.removeItem(at: tempFileURL)
            }
            guard let presenter = topViewController() else {
                try? FileManager.default.removeItem(at: tempFileURL)
                completion?(false)
                return
            }
            // Inherit theme from the root view controller (which respects app's theme preference)
            ac.overrideUserInterfaceStyle = presenter.overrideUserInterfaceStyle
            presenter.present(ac, animated: true)
        } catch {
            log(.error, error: error) { "Error sharing file." }
            completion?(false)
        }
    }

    func completed() {
        // no-op on iOS
    }

    // MARK: - Private Methods

    private func topViewController() -> UIViewController? {
        presentationContext.topViewController
    }

}
