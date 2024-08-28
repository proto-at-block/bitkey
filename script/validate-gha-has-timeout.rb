require 'yaml'
require 'find'

def validate_timeout_in_file(file_path, debug = false)
  puts "Validating #{file_path}..."
  content = YAML.load_file(file_path) rescue (puts "  [ERROR] Failed to parse #{file_path}"; return false)
  jobs = content['jobs'] || (puts "No jobs found in #{file_path}"; return true)

  jobs.all? do |job_name, job_details|
    if job_details.key?('uses')
      puts "  [INFO] Job '#{job_name}' uses a reusable workflow ('uses' keyword)." if debug
      true
    elsif job_details['timeout-minutes']
      puts "  [OK] Job '#{job_name}' has 'timeout-minutes' set to #{job_details['timeout-minutes']}" if debug
      true
    else
      puts "  [ERROR] Job '#{job_name}' is missing 'timeout-minutes'"
      false
    end
  end
end

def validate_all_files_in_directory(directory, debug = false)
  Find.find(directory).select { |path| path =~ /.*\.ya?ml$/ }.all? { |path| validate_timeout_in_file(path, debug) }
end

if ARGV.length < 1 || ARGV.length > 2
  puts "Usage: ruby validate-gha-has-timeout.rb <directory> [--debug]"
  exit 1
end

directory, debug = ARGV[0], ARGV.include?('--debug')

unless Dir.exist?(directory)
  puts "Directory '#{directory}' does not exist."
  exit 1
end

if validate_all_files_in_directory(directory, debug)
  puts "All jobs have valid 'timeout-minutes'."
  exit 0
else
  puts "Some jobs are missing 'timeout-minutes', have an invalid value, or use a reusable workflow."
  exit 1
end
