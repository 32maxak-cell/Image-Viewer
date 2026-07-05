// image_viewer.cs
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.Processing;
using SixLabors.ImageSharp.PixelFormats;

class ImageViewer
{
    static string Colorize(string text, string color)
    {
        return color + text + "\x1b[0m";
    }

    static string RgbToAnsi(byte r, byte g, byte b)
    {
        return $"\x1b[38;2;{r};{g};{b}m";
    }

    static List<string> GetImageFiles(string path)
    {
        if (File.Exists(path)) return new List<string>{path};
        if (Directory.Exists(path))
        {
            var exts = new HashSet<string>{".jpg",".jpeg",".png",".gif",".bmp",".tiff",".webp"};
            return Directory.GetFiles(path)
                .Where(f => exts.Contains(Path.GetExtension(f).ToLower()))
                .OrderBy(f => f)
                .ToList();
        }
        return new List<string>();
    }

    static void DisplayImage(Image<Rgb24> img, double zoom, int rotate, bool flipH, bool flipV, bool asciiMode, int termW, int termH)
    {
        Console.Clear();
        var clone = img.Clone();
        if (rotate == 90) clone.Mutate(ctx => ctx.Rotate(90));
        else if (rotate == 180) clone.Mutate(ctx => ctx.Rotate(180));
        else if (rotate == 270) clone.Mutate(ctx => ctx.Rotate(-90));
        if (flipH) clone.Mutate(ctx => ctx.Flip(FlipMode.Horizontal));
        if (flipV) clone.Mutate(ctx => ctx.Flip(FlipMode.Vertical));

        int w = clone.Width, h = clone.Height;
        int maxW = termW - 2, maxH = termH - 6;
        double scale = zoom;
        if (scale == 1.0)
        {
            double sx = (double)maxW / w, sy = (double)maxH / h;
            scale = Math.Min(sx, sy);
            if (scale > 1.0) scale = 1.0;
        }
        int newW = Math.Max(1, (int)(w * scale));
        int newH = Math.Max(1, (int)(h * scale));
        clone.Mutate(ctx => ctx.Resize(newW, newH));

        Console.WriteLine($"\x1b[1m📷 Image Viewer | {newW}×{newH}\x1b[0m");
        Console.WriteLine($"Zoom: {zoom:F1}x | Rotate: {rotate}° | Flip: {flipH}/{flipV}");
        Console.WriteLine("Controls: ← → (nav)  +/- (zoom)  r (rotate)  h/v (flip)  f (mode)  q (quit)");

        if (asciiMode)
        {
            string chars = " .:-=+*#%@";
            for (int y = 0; y < newH; y++)
            {
                for (int x = 0; x < newW; x++)
                {
                    var pixel = clone[x, y];
                    double gray = 0.299 * pixel.R + 0.587 * pixel.G + 0.114 * pixel.B;
                    int idx = (int)((gray / 255) * (chars.Length - 1));
                    Console.Write(chars[idx]);
                }
                Console.WriteLine();
            }
        }
        else
        {
            for (int y = 0; y < newH; y++)
            {
                for (int x = 0; x < newW; x++)
                {
                    var pixel = clone[x, y];
                    Console.Write(RgbToAnsi(pixel.R, pixel.G, pixel.B) + "██");
                }
                Console.WriteLine("\x1b[0m");
            }
        }
    }

    static int Main(string[] args)
    {
        string path = ".";
        double zoom = 1.0;
        int rotate = 0;
        bool asciiMode = false;
        string convertFormat = null, outputFile = null;

        for (int i = 0; i < args.Length; i++)
        {
            if (args[i] == "-z" && i+1 < args.Length) zoom = double.Parse(args[++i]);
            else if (args[i] == "-r" && i+1 < args.Length) rotate = int.Parse(args[++i]);
            else if (args[i] == "-a") asciiMode = true;
            else if (args[i] == "-c" && i+1 < args.Length) convertFormat = args[++i];
            else if (args[i] == "-o" && i+1 < args.Length) outputFile = args[++i];
            else if (args[i] == "-h" || args[i] == "--help")
            {
                Console.WriteLine("Usage: image_viewer <path> [-z zoom] [-r rotate] [-a] [-c format] [-o output]");
                return 0;
            }
            else path = args[i];
        }

        var files = GetImageFiles(path);
        if (files.Count == 0) { Console.WriteLine("Нет изображений."); return 1; }
        int idx = 0, flipH = 0, flipV = 0;
        var img = Image.Load<Rgb24>(files[idx]);

        if (!string.IsNullOrEmpty(convertFormat) && !string.IsNullOrEmpty(outputFile))
        {
            img.Save(outputFile);
            Console.WriteLine($"✅ Изображение сохранено в {outputFile}");
            return 0;
        }

        int termW = Console.WindowWidth, termH = Console.WindowHeight;

        while (true)
        {
            DisplayImage(img, zoom, rotate, flipH == 1, flipV == 1, asciiMode, termW, termH);
            var key = Console.ReadKey(true);
            if (key.KeyChar == 'q') break;
            else if (key.Key == ConsoleKey.Add || key.KeyChar == '+') zoom = Math.Min(3.0, zoom + 0.2);
            else if (key.Key == ConsoleKey.Subtract || key.KeyChar == '-') zoom = Math.Max(0.2, zoom - 0.2);
            else if (key.KeyChar == 'r') rotate = (rotate + 90) % 360;
            else if (key.KeyChar == 'h') flipH = 1 - flipH;
            else if (key.KeyChar == 'v') flipV = 1 - flipV;
            else if (key.KeyChar == 'f') asciiMode = !asciiMode;
            else if (key.Key == ConsoleKey.RightArrow && idx < files.Count-1) { idx++; img = Image.Load<Rgb24>(files[idx]); }
            else if (key.Key == ConsoleKey.LeftArrow && idx > 0) { idx--; img = Image.Load<Rgb24>(files[idx]); }
        }
        return 0;
    }
}
