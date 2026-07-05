#!/usr/bin/env ruby
# image_viewer.rb
# encoding: UTF-8

require 'rmagick'
include Magick
require 'io/console'

COLORS = {
  reset: "\e[0m",
  bold: "\e[1m"
}

def rgb_to_ansi(r, g, b)
  "\e[38;2;#{r};#{g};#{b}m"
end

def get_image_files(path)
  return [path] if File.file?(path)
  if File.directory?(path)
    exts = %w[.jpg .jpeg .png .gif .bmp .tiff .webp]
    Dir.entries(path)
       .select { |f| exts.include?(File.extname(f).downcase) }
       .map { |f| File.join(path, f) }
       .sort
  else
    []
  end
end

def display_image(img, zoom, rotate, flip_h, flip_v, ascii_mode, term_w, term_h)
  system('clear') || system('cls')
  w, h = img.columns, img.rows
  max_w = term_w - 2
  max_h = term_h - 6
  scale = zoom == 1.0 ? [max_w.to_f / w, max_h.to_f / h, 1.0].min : zoom
  new_w = [(w * scale).to_i, 1].max
  new_h = [(h * scale).to_i, 1].max

  processed = img.scale(new_w, new_h)
  processed = processed.rotate(rotate) if rotate != 0
  processed = processed.flip! if flip_h
  processed = processed.flop! if flip_v

  puts "#{COLORS[:bold]}📷 Image Viewer | #{new_w}×#{new_h}#{COLORS[:reset]}"
  puts "Zoom: #{zoom.round(1)}x | Rotate: #{rotate}° | Flip: #{flip_h}/#{flip_v}"
  puts "Controls: ← → (nav)  +/- (zoom)  r (rotate)  h/v (flip)  f (mode)  q (quit)"

  if ascii_mode
    chars = ' .:-=+*#%@'
    processed.to_blob { self.format = 'GRAY' }.bytes.each_slice(new_w) do |row|
      puts row.map { |b| chars[(b.to_f / 255 * (chars.length-1)).to_i] }.join
    end
  else
    pixels = processed.get_pixels(0, 0, new_w, new_h)
    new_h.times do |y|
      line = ''
      new_w.times do |x|
        pixel = pixels[y * new_w + x]
        line += rgb_to_ansi(pixel.red / 257, pixel.green / 257, pixel.blue / 257) + '██'
      end
      puts line + COLORS[:reset]
    end
  end
end

def getch
  STDIN.getch
end

def main
  path = '.'
  zoom = 1.0
  rotate = 0
  ascii_mode = false
  convert_format = nil
  output_file = nil

  i = 0
  while i < ARGV.size
    arg = ARGV[i]
    case arg
    when '-z' then zoom = ARGV[i+1].to_f; i += 1
    when '-r' then rotate = ARGV[i+1].to_i; i += 1
    when '-a' then ascii_mode = true
    when '-c' then convert_format = ARGV[i+1]; i += 1
    when '-o' then output_file = ARGV[i+1]; i += 1
    when '-h', '--help'
      puts "Usage: ruby image_viewer.rb <path> [-z zoom] [-r rotate] [-a] [-c format] [-o output]"
      return
    else path = arg
    end
    i += 1
  end

  files = get_image_files(path)
  if files.empty?
    puts "Нет изображений."
    return 1
  end

  idx = 0
  flip_h, flip_v = false, false
  img = Image.read(files[idx]).first

  if convert_format && output_file
    img.write(output_file)
    puts "✅ Изображение сохранено в #{output_file}"
    return
  end

  term_w = IO.console.winsize[1]
  term_h = IO.console.winsize[0]

  loop do
    display_image(img, zoom, rotate, flip_h, flip_v, ascii_mode, term_w, term_h)
    key = getch
    case key
    when 'q' then break
    when '+' then zoom = [3.0, zoom + 0.2].min
    when '-' then zoom = [0.2, zoom - 0.2].max
    when 'r' then rotate = (rotate + 90) % 360
    when 'h' then flip_h = !flip_h
    when 'v' then flip_v = !flip_v
    when 'f' then ascii_mode = !ascii_mode
    when "\e"
      seq = STDIN.read_nonblock(3) rescue nil
      if seq == '[D' && idx > 0
        idx -= 1
        img = Image.read(files[idx]).first
      elsif seq == '[C' && idx < files.size-1
        idx += 1
        img = Image.read(files[idx]).first
      end
    end
  end
end

main if __FILE__ == $0
