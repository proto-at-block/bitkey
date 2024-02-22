source "https://rubygems.org"
ruby "~> 3.2"

gem "colorize"  # For hubkins release script
gem "fastlane"  # For hubkins release script
gem "xcodeproj", "~> 1.21"
gem "xcpretty", "~> 0.3.0"  # For xcodebuild in GHA

plugins_path = File.join(File.dirname(__FILE__), 'fastlane', 'Pluginfile')
eval_gemfile(plugins_path) if File.exist?(plugins_path)
