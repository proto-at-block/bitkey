module WalletPods

    def local_pod(name, **kwargs)
      pod name, path: "Sources/#{name}/#{name}.podspec", **kwargs
    end

    def local_pod_impl_public(name, **kwargs)
      pod "#{name}Public", path: "Sources/#{name}/Public/#{name}Public.podspec"
      pod "#{name}Impl", path: "Sources/#{name}/Impl/#{name}Impl.podspec", **kwargs
    end

    # Adds test spec xctest bundles to the appropriate scheme for test targets named `UnitTests`.
    def add_unit_test_spec_targets_to_all_tests_scheme(installer)
      project = installer.aggregate_targets.map(&:user_project).first

      unit_tests_scheme = Xcodeproj::XCScheme.new(File.join(project.path, 'xcshareddata', 'xcschemes', 'WalletTests.xcscheme'))

      installer.development_pod_targets(installer.generated_pod_targets).each do |pod_target|
        pod_target_installation_result = installer.target_installation_results.pod_target_installation_results[pod_target.name]
        next unless pod_target_installation_result

        pod_target_installation_result.test_native_targets.each do |test_native_target|
          next unless iphone_target?(test_native_target) && test_native_target.name.end_with?("UnitTests")

          configure_test_target(test_native_target, unit_tests_scheme)
        end
      end

      sanitize_scheme(unit_tests_scheme)
      unit_tests_scheme.save!
    end

    def configure_test_target(test_native_target, scheme)
      existing_buildable, testable = find_existing_entry(scheme, test_native_target)
      testable ||= Xcodeproj::XCScheme::TestAction::TestableReference.new(test_native_target)
      testable.test_execution_ordering = 'random'

      container_name = "container:Pods/#{test_native_target.project.path.basename}"

      if existing_buildable
        existing_buildable.xml_element.attributes['ReferencedContainer'] = container_name
        scheme.test_action.add_testable(testable)
      else
        testable.buildable_references.each do |buildable|
          buildable.xml_element.attributes['ReferencedContainer'] = container_name
        end
        scheme.test_action.add_testable(testable)
      end
    end

    def find_existing_entry(scheme, test_native_target)
      existing_testable = nil
      existing_buildable = nil
      scheme.test_action.testables.each do |testable|
        existing_buildable = testable.buildable_references.find do |buildable|
          buildable.xml_element.attributes['BlueprintName'] == test_native_target.name
        end

        if existing_buildable
          existing_testable = testable
          break
        end
      end
      return existing_buildable, existing_testable
    end

    # Sort the buildable and testable references within the specified scheme.
    def sanitize_scheme(scheme)
      def sort_key(entry)
        # If we cannot identify the key, use a constant key so the entries sort to the end of the list.
        entry.buildable_references.first.xml_element.attributes['BlueprintName'] || "zzzzz"
      end
      scheme.build_action.entries = scheme.build_action.entries.sort { |l, r| sort_key(l) <=> sort_key(r) } unless scheme.build_action.entries.nil?
      scheme.test_action.testables = scheme.test_action.testables.sort { |l, r| sort_key(l) <=> sort_key(r) } unless scheme.test_action.testables.nil?
    end

    def iphone_target?(native_target)
      native_target.build_configurations[0].build_settings["IPHONEOS_DEPLOYMENT_TARGET"] != nil
    end

end

module Pod

  class Podfile

    # Podfiles can specify whether a pod should be integrated into a target for a project-level
    # build configuration and allows mapping project-level build configurations to built-in :debug
    # or :release build configurations.  However, Podspecs can only specify whether a pod should be
    # integrated for :debug or :release configurations. This module provides methods for
    # specifying build configurations semantically.
    #
    # Pod::Podfile::Configuration should only be used when configuring a Podfile.
    module Configuration

      # Podfile dependencies configured with the `tests` configuration are only be made
      # available to Podfile targets using the 'Debug' configuration from the xcode project.
      def self.tests
        ['Debug']
      end

      # Podfile dependencies configured with the `debug_menu` configuration are only be made
      # available to Podfile targets NOT using the 'AppStore' configuration from the xcode project.
      def self.debug_menu
        %w[Debug Dogfood]
      end

    end

  end

  # rubocop:disable Metrics/ClassLength
  class Spec

    # See Pod::Podfile::Configuration documentation
    #
    # Pod::Spec::Configuration should only be used when configuring a podspec.
    module Configuration

      # Podspec dependencies configured with the `tests` configuration are only be made
      # available to Podfile targets using the :debug configuration.
      def self.tests
        ['Debug']
      end

      # Podspec dependencies configured with the `debug_menu` configuration are:
      # - always available to Podfile targets using the :debug configuration
      # - only available to Podfile targets using the :release configuration for the
      #   'dogfood' release variant
      def self.debug_menu
        if ENV['RELEASE_VARIANT'] == 'dogfood'
          %w[Debug Release]
        else
          ['Debug']
        end
      end

    end

    def standard_xcconfig(warnings_as_errors: true, is_testing_framework: false)
      # Enable DEBUG_MENU for Debug builds to enable testing DEBUG_MENU
      # specific functionality such as fake phone numbers.
      debug_xcconfig = {
        'GCC_PREPROCESSOR_DEFINITIONS[config=Debug]' => ' $(inherited) DEBUG_MENU=1',
        'OTHER_SWIFT_FLAGS[config=Debug]' => ' $(inherited) -DDEBUG_MENU',
      }

      # Treat warnings as errors.
      warnings_as_errors_value = warnings_as_errors ? 'YES' : 'NO'
      warnings_as_errors_xcconfig = {
        'GCC_TREAT_WARNINGS_AS_ERRORS' => warnings_as_errors_value,
        'SWIFT_TREAT_WARNINGS_AS_ERRORS' => warnings_as_errors_value,
      }

      testing_framework_xcconfig = is_testing_framework ? { "ENABLE_TESTING_SEARCH_PATHS" => "YES" } : {}

      debug_xcconfig
        .dup
        .merge(warnings_as_errors_xcconfig)
        .merge(testing_framework_xcconfig)
        .dup
    end

    def configure(name, summary: 'See README.md', version: '1.0.0-LOCAL', license: { :type => 'Proprietary', :text => '© Block, Inc.' }, warnings_as_errors: true, deployment_target: '13.0', additional_source_extensions: [], lint: true, is_testing_framework: false) # rubocop:disable Metrics/ParameterLists
      # Required Metadata
      self.name                      = name
      self.version                   = version
      self.homepage                  = "https://github.com/squareup/wallet/tree/main/ios/Sources/#{name}"
      self.source                    = { :git => 'Not published', :tag => "CocoaPods/#{name}/#{version}" }
      self.license                   = license
      self.summary                   = summary
      self.authors                   = 'Block'

      # Build Configuration
      self.static_framework          = true
      self.ios.deployment_target     = deployment_target
      self.swift_version             = '5.0'
      self.pod_target_xcconfig       = standard_xcconfig(warnings_as_errors: warnings_as_errors, is_testing_framework: is_testing_framework)

      extensions = %w[h c m swift].concat(additional_source_extensions).join(',')
      self.source_files = "Sources/**/*.{#{extensions}}"
    end

    # Creates a test spec called 'UnitTests' with a standard configuration.
    def unit_tests()
      base_xcconfig = self.consumer(:ios).pod_target_xcconfig
      self.test_spec 'UnitTests' do |t|
        t.pod_target_xcconfig = test_xcconfig(base_xcconfig)
        t.source_files = 'UnitTests/**/*.{h,c,m,swift}'
        t.framework = 'XCTest'
        yield(t) if block_given?
      end
    end

    def test_xcconfig(base_xcconfig)
      # Disable checks for extension-only API usage. Tests never run in an extension.
      test_xcconfig = {
        'APPLICATION_EXTENSION_API_ONLY' => 'NO',
      }

      base_xcconfig
        .dup
        .merge(test_xcconfig)
        .dup
    end

    def standard_resource_bundle(access_level: 'internal', generate_images: false, additional_extensions: [])
      resource_root = 'Resources'

      extensions = %w[strings xcassets]

      if generate_images
        images_xcassets_path = "#{resource_root}/Images.xcassets"
        generate_image_bindings(access_level: access_level, xcassets_path: images_xcassets_path)
      end

      extensions += additional_extensions

      bundle_name = "#{self.name}Resources"
      glob = "#{resource_root}/**/*.{#{extensions.join(',')}}"

      self.resource_bundle = { bundle_name => glob }
    end

  end
  # rubocop:enable Metrics/ClassLength

end
