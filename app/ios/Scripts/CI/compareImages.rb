#!/usr/bin/env ruby

require 'mini_magick'

def compare_images(img1_path, img2_path)
    # Ensure both images exist before attempting to open them
    return Float::INFINITY unless File.exist?(img1_path) && File.exist?(img2_path)

    image1 = MiniMagick::Image.open(img1_path)
    image2 = MiniMagick::Image.open(img2_path)

    # Using 'compare' command to get the absolute error metric
    begin
      result = MiniMagick::Tool::Compare.new do |compare|
        compare.metric "ae"
        compare << img1_path
        compare << img2_path
        compare << "null:"  # Output goes to null, we are interested in the output from stdout
      end
      result.to_f
    rescue MiniMagick::Error => e
      Float::INFINITY
    end
  end

  def process_images(snapshot_dir, image_list_file)
    # Read the image list from the file
    all_images = File.readlines(image_list_file).map(&:strip)

    # Separate images into categories
    reference_images = all_images.select { |img| File.basename(img).start_with?('reference_') }
    failure_images = all_images.select { |img| File.basename(img).start_with?('failure_') }
    snapshot_images = Dir.glob("#{snapshot_dir}/*.png")

    # Interate over the reference images based on the ID of the failure to get the correct name for the failing image
    reference_images.each do |reference_image|
      id = File.basename(reference_image).split('_', 3).last.chomp('.png')
      puts "Processing #{id}"
      best_match = nil
      lowest_diff = Float::INFINITY

      snapshot_images.each do |image|
        diff = compare_images(reference_image, image)
        if diff < lowest_diff
          lowest_diff = diff
          best_match = image
        end
      end

      matched_filename = File.basename(best_match) if best_match
      failure_image = failure_images.find { |img| File.basename(img).include?("_#{id}.png") }

      if best_match && failure_image
        File.delete(best_match)
        # Rename the failure image to the matched image's name and move it to the snapshot_dir
        new_path = "#{snapshot_dir}/#{matched_filename}"
        File.rename(failure_image, new_path)
        puts "Updated snapshot #{matched_filename}"
      else
        puts "No visually matching image found for #{File.basename(reference_image)} in the snapshots directory."
      end
    end
  end

# Check if filename is provided
if ARGV.length != 2
  puts "Usage: ruby compareImages.rb <snapshot_dir> <image_list_file>"
  exit
end

snapshot_dir = ARGV[0]
image_list_file = ARGV[1]

process_images(snapshot_dir, image_list_file)