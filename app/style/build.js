const StyleDictionary = require('style-dictionary');

StyleDictionary.extend({
  format: {
    composeColor: require('./formats/composeColor'),
    composeIcons: require('./formats/composeIcons'),
    composeLabelType: require('./formats/composeLabelType'),
    iosColor: require('./formats/iosColor'),
    iosFont: require('./formats/iosFont'),
    iosIcons: require('./formats/iosIcons'),
    sharedIcons: require('./formats/sharedIcons')
  },
  "source": ["tokens/**/*.json"],
  "platforms": {
    "android": {
      "transformGroup": "compose",
      "buildPath": "../android/ui/core/public/src/main/kotlin/build/wallet/ui/tokens/",
      "files": [
        {
          "destination": "StyleDictionaryColors.kt",
          "format": "composeColor",
          "filter": {
            "attributes": {
              "category": "color"
            }
          },
          "options": {
            "outputReferences": true
          }
        },
        {
          "destination": "StyleDictionaryIcons.kt",
          "format": "composeIcons",
          "filter": {
            "attributes": {
              "category": "icons"
            }
          },
          "options": {
            "outputReferences": true
          }
        },
        {
          "destination": "LabelType.kt",
          "format": "composeLabelType",
          "filter": {
            "attributes": {
              "category": "font"
            }
          },
          "options": {
            "outputReferences": true
          }
        }
      ]
    },
    "ios": {
      "transformGroup": "ios-swift-separate",
      "buildPath": "../ios/Wallet/Sources/UI/DesignSystem/gen/",
      "files": [
        {
          "destination": "Color.swift",
          "format": "iosColor",
          "filter": {
            "attributes": {
              "category": "color"
            }
          },
          "options": {
            "outputReferences": true
          }
        },
        {
          "destination": "Font.swift",
          "format": "iosFont",
          "filter": {
            "attributes": {
              "category": "font"
            }
          },
          "options": {
            "outputReferences": true
          }
        },
        {
          "destination": "Icons.swift",
          "format": "iosIcons",
          "filter": {
            "attributes": {
              "category": "icons"
            }
          },
          "options": {
            "outputReferences": true
          }
        }
      ]
    },
    "shared": {
        "transformGroup": "compose",
        "buildPath": "../shared/ui/core/public/src/commonMain/kotlin/build/wallet/statemachine/core/",
        "files": [
          {
            "destination": "Icon.kt",
            "format": "sharedIcons",
            "filter": {
              "attributes": {
                "category": "icons"
              }
            },
            "options": {
              "outputReferences": true
            }
          }
        ]
      }
  }
}).buildAllPlatforms();
