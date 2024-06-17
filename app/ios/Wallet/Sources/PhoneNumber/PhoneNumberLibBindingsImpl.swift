import PhoneNumberKit
import Shared

public final class PhoneNumberLibBindingsImpl: PhoneNumberLibBindings {

    // MARK: - Life Cycle

    public init() {}

    // MARK: - Private Properties

    private let phoneNumberKit = PhoneNumberKit()

    // MARK: - PhoneNumberLibBindings

    public var supportedCountryDialingCodes: Set<KotlinInt> {
        let dialingCodes = phoneNumberKit.allCountries()
            .compactMap { phoneNumberKit.countryCode(for: $0) }
            .map { KotlinInt(int: Int32($0)) }
        return Set(dialingCodes)
    }

    public func countryDialingCodeFromIsoCode(countryIsoCode: String) -> Int32 {
        return Int32(phoneNumberKit.countryCode(for: countryIsoCode) ?? 1)
    }

    public func exampleNumber(countryDialingCode: Int32) -> PhoneNumberLibPhoneNumber? {
        guard let region = mainRegionForCountryCode(countryDialingCode: countryDialingCode),
              let libPhoneNumber = phoneNumberKit.getExampleNumber(forCountry: region)
        else {
            return nil
        }
        return PhoneNumberLibPhoneNumberImpl(libPhoneNumber: libPhoneNumber)
    }

    public func isValidNumber(number: PhoneNumberLibPhoneNumber) -> Bool {
        let libPhoneNumber = (number as! PhoneNumberLibPhoneNumberImpl).libPhoneNumber
        return phoneNumberKit.isValidPhoneNumber(libPhoneNumber.numberString)
    }

    public func mainRegionForCountryCode(countryDialingCode: Int32) -> String? {
        return phoneNumberKit.mainCountry(forCode: UInt64(countryDialingCode))
    }

    public func parse(numberToParse: String, defaultRegion: String) -> PhoneNumberLibPhoneNumber? {
        do {
            let libPhoneNumber = try phoneNumberKit.parse(numberToParse, withRegion: defaultRegion)
            return PhoneNumberLibPhoneNumberImpl(libPhoneNumber: libPhoneNumber)
        } catch {
            return nil
        }
    }

    public func formatPartialPhoneNumber(countryDialingCode: Int32, rawNumber: String) -> String {
        let defaultRegion = mainRegionForCountryCode(countryDialingCode: countryDialingCode)
            ?? PhoneNumberKit.defaultRegionCode()
        let formattedNationalNumber = PartialFormatter(
            defaultRegion: defaultRegion,
            withPrefix: false
        )
        .formatPartial(rawNumber)
        return "+\(countryDialingCode) \(formattedNationalNumber)"
    }

    public func format(
        phoneNumber: PhoneNumberLibPhoneNumber,
        format: PhoneNumberLibFormat
    ) -> String {
        let libPhoneNumber = (phoneNumber as! PhoneNumberLibPhoneNumberImpl).libPhoneNumber
        switch format {
        case .e164:
            return PhoneNumberKit().format(libPhoneNumber, toType: .e164)
        case .international:
            return PhoneNumberKit().format(libPhoneNumber, toType: .international)
        default:
            fatalError("Unsupported phone number format")
        }
    }

}
