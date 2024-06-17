import PhoneNumberKit
import Shared
import XCTest

@testable import Wallet

class PhoneNumberValidatorImplTests: XCTestCase {

    private let countryCodeGuesser = CountryCodeGuesserFake()
    private let phoneNumberKit = PhoneNumberKit()
    private lazy var validator = PhoneNumberValidatorImpl(
        countryCodeGuesser: countryCodeGuesser,
        phoneNumberLibBindings: PhoneNumberLibBindingsImpl()
    )

    func testExampleNationalNumber() {
        countryCodeGuesser.fakeCountryCode = "US"
        XCTAssertEqual(validator.exampleFormattedNumberForCurrentRegion(), "+1 201-555-0123")

        for country in phoneNumberKit.allValidCountries() {
            countryCodeGuesser.fakeCountryCode = country
            XCTAssertNotNil(validator.exampleFormattedNumberForCurrentRegion())
        }
    }

    func testValidatePhoneNumberWithRawNumber() {
        for country in phoneNumberKit.allCountries() {
            if let validNumber = phoneNumberKit.getExampleNumber(forCountry: country) {
                XCTAssertNotNil(
                    validator.validatePhoneNumber(number: phoneNumberKit.format(
                        validNumber,
                        toType: .international
                    ))
                )
            }
        }
    }

    func testValidatePhoneNumberWithRawNumberInvalidString() {
        XCTAssertNil(validator.validatePhoneNumber(number: "foo-bar"))
    }

    func testValidatePhoneNumberWithE164Format() {
        phoneNumberKit.allCountries()
            .compactMap { phoneNumberKit.getExampleNumber(forCountry: $0) }
            .map { phoneNumberKit.format($0, toType: .e164) }
            .forEach { formattedE164Value in
                XCTAssertNotNil(
                    validator.validatePhoneNumber(number: formattedE164Value)
                )
            }
    }

}

// MARK: -

private extension PhoneNumberKit {
    func allValidCountries() -> [String] {
        return allCountries().filter { $0 != "001" }
    }
}

// MARK: -

private class CountryCodeGuesserFake: CountryCodeGuesser {
    var fakeCountryCode = "US"
    func countryCode() -> String {
        return fakeCountryCode
    }
}
