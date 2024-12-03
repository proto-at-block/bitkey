This module contains components for validating and formatting phone numbers.

Each platform implements these components and injects them through `ActivityComponentImpl.kt`

- Android and JVM uses https://github.com/google/libphonenumber
- iOS uses https://github.com/marmelroy/PhoneNumberKit
