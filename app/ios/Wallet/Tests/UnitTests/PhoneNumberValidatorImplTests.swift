import PhoneNumberKit
import Shared
import Testing

@testable import Wallet

struct PhoneNumberValidatorImplTests {

    private let countryCodeGuesser = CountryCodeGuesserFake()
    private let phoneNumberKit = PhoneNumberKit()
    private var validator: PhoneNumberValidatorImpl {
        PhoneNumberValidatorImpl(
            countryCodeGuesser: countryCodeGuesser,
            phoneNumberLibBindings: PhoneNumberLibBindingsImpl()
        )
    }

    @Test
    func exampleNationalNumber() {
        countryCodeGuesser.fakeCountryCode = "US"
        #expect(validator.exampleFormattedNumberForCurrentRegion() == "+1 201-555-0123")

        for country in phoneNumberKit.allValidCountries() {
            countryCodeGuesser.fakeCountryCode = country
            #expect(validator.exampleFormattedNumberForCurrentRegion() != nil)
        }
    }

    @Test
    func validatePhoneNumberWithRawNumber() {
        for country in phoneNumberKit.allCountries() {
            if let validNumber = phoneNumberKit.getExampleNumber(forCountry: country) {
                #expect(
                    validator.validatePhoneNumber(number: phoneNumberKit.format(
                        validNumber,
                        toType: .international
                    )) != nil
                )
            }
        }
    }

    @Test
    func validatePhoneNumberWithRawNumberInvalidString() {
        #expect(validator.validatePhoneNumber(number: "foo-bar") == nil)
    }

    @Test
    func validatePhoneNumberWithE164Format() {
        phoneNumberKit.allCountries()
            .compactMap { phoneNumberKit.getExampleNumber(forCountry: $0) }
            .map { phoneNumberKit.format($0, toType: .e164) }
            .forEach { formattedE164Value in
                #expect(
                    validator.validatePhoneNumber(number: formattedE164Value) != nil
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
