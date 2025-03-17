This module contains domain specific components for creating the Emergency Access Kit PDF, saving it to customer's cloud file storage, and reading it from the customer's cloud file storage.

Currently, the Android PDF snapshot is be created manually:
1. Run the `EmergencyAccessKitSnapshotTest` in IntelliJ.
2. Go to the Device Explorer > data > data > build.wallet.domain.emergency.access.kit.impl.test > files.
3. Right-click on "Emergency Access Kit.pdf" and Save As.
4. Copy the PDF to `app/domain/emergency-access-kit/impl/src/commonTest/snapshots/Emergency Access Kit.pdf`.

Similarly, the iOS PDF snapshot is created manually (see BKR-1052 for reason):
1. Go to `EmergencyAccessKitPdfSnapshotTests.test_eak_pdf()` and temporarily comment out the first `throw XCTSkip…` line.
2. Run the iOS snapshots in record mode.
3. Commit the updated `test_eak_pdf.1.pdf` file.