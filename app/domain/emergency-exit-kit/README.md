This module contains domain specific components for creating the Emergency Exit Kit PDF, saving it to customer's cloud file storage, and reading it from the customer's cloud file storage.

Currently, the Android PDF snapshot is be created manually:
1. Run the `EmergencyExitKitSnapshotTest` in IntelliJ.
2. Go to the Device Explorer > data > data > build.wallet.domain.emergency.exit.kit.impl.test > files.
3. Right-click on "Emergency Exit Kit.pdf" and Save As.
4. Copy the PDF to `app/domain/emergency-exit-kit/impl/src/commonTest/snapshots/Emergency Exit Kit.pdf`.

Similarly, the iOS PDF snapshot is created manually (see BKR-1052 for reason):
1. Go to `EmergencyExitKitPdfSnapshotTests.test_eek_pdf()` and temporarily comment out the first `throw XCTSkip…` line.
2. Run the iOS snapshots in record mode.
3. Commit the updated `test_eek_pdf.1.pdf` file.