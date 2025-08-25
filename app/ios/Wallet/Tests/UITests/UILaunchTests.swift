import XCTest

class UILaunchTests: XCTestCase {
    func testLaunchPerformance() throws {
        let measureOptions = XCTMeasureOptions()
        measureOptions.iterationCount = 10

        measure(metrics: [XCTApplicationLaunchMetric()], options: measureOptions) {
            XCUIApplication().launch()
        }
    }
}
