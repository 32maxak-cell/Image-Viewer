// image_viewer.java
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.*;

public class image_viewer {
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";

    private static String rgbToAnsi(int r, int g, int b) {
        return String.format("\u001B[38;2;%d;%d;%dm", r, g, b);
    }

    private static String[] getImageFiles(String path) {
        File f = new File(path);
        if (f.isFile()) return new String[]{path};
        if (f.isDirectory()) {
            String[] exts = {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".tiff", ".webp"};
            return Arrays.stream(f.list())
                .filter(name -> {
                    String ext = name.substring(name.lastIndexOf('.')).toLowerCase();
                    for (String e : exts) if (e.equals(ext)) return true;
                    return false;
                })
                .map(name -> f.getPath() + File.separator + name)
                .sorted()
                .toArray(String[]::new);
        }
        return new String[0];
    }

    private static BufferedImage applyTransform(BufferedImage img, int rotate, boolean flipH, boolean flipV) {
        int w = img.getWidth(), h = img.getHeight();
        BufferedImage result = img;
        if (rotate == 90 || rotate == 180 || rotate == 270) {
            // Простой поворот через AffineTransform
            AffineTransform tx = AffineTransform.getRotateInstance(Math.toRadians(rotate), w/2.0, h/2.0);
            if (rotate == 90 || rotate == 270) {
                result = new BufferedImage(h, w, BufferedImage.TYPE_INT_RGB);
            } else {
                result = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            }
            Graphics2D g2d = result.createGraphics();
            g2d.setTransform(tx);
            g2d.drawImage(img, 0, 0, null);
            g2d.dispose();
            w = result.getWidth(); h = result.getHeight();
        }
        if (flipH || flipV) {
            AffineTransform tx = AffineTransform.getScaleInstance(flipH ? -1 : 1, flipV ? -1 : 1);
            tx.translate(flipH ? -w : 0, flipV ? -h : 0);
            BufferedImage flipped = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = flipped.createGraphics();
            g2d.setTransform(tx);
            g2d.drawImage(result, 0, 0, null);
            g2d.dispose();
            result = flipped;
        }
        return result;
    }

    private static BufferedImage resize(BufferedImage img, int newW, int newH) {
        Image scaled = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.drawImage(scaled, 0, 0, null);
        g2d.dispose();
        return resized;
    }

    public static void main(String[] args) throws Exception {
        String path = ".";
        double zoom = 1.0;
        int rotate = 0;
        boolean asciiMode = false;
        String convertFormat = null, outputFile = null;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-z") && i+1 < args.length) zoom = Double.parseDouble(args[++i]);
            else if (args[i].equals("-r") && i+1 < args.length) rotate = Integer.parseInt(args[++i]);
            else if (args[i].equals("-a")) asciiMode = true;
            else if (args[i].equals("-c") && i+1 < args.length) convertFormat = args[++i];
            else if (args[i].equals("-o") && i+1 < args.length) outputFile = args[++i];
            else if (args[i].equals("-h") || args[i].equals("--help")) {
                System.out.println("Usage: java image_viewer <path> [-z zoom] [-r rotate] [-a] [-c format] [-o output]");
                return;
            } else path = args[i];
        }

        String[] files = getImageFiles(path);
        if (files.length == 0) { System.err.println("Нет изображений."); System.exit(1); }
        int idx = 0, flipH = 0, flipV = 0;
        BufferedImage img = ImageIO.read(new File(files[idx]));

        if (convertFormat != null && outputFile != null) {
            ImageIO.write(img, convertFormat, new File(outputFile));
            System.out.println("✅ Изображение сохранено в " + outputFile);
            return;
        }

        int termW = 80, termH = 24; // можно определить через терминал, но для простоты оставим

        while (true) {
            BufferedImage transformed = applyTransform(img, rotate, flipH == 1, flipV == 1);
            int w = transformed.getWidth(), h = transformed.getHeight();
            int maxW = termW - 2, maxH = termH - 6;
            double scale = zoom;
            if (scale == 1.0) {
                double sx = (double)maxW / w, sy = (double)maxH / h;
                scale = Math.min(sx, sy);
                if (scale > 1.0) scale = 1.0;
            }
            int newW = Math.max(1, (int)(w * scale));
            int newH = Math.max(1, (int)(h * scale));
            BufferedImage resized = resize(transformed, newW, newH);

            System.out.print("\033[H\033[2J");
            System.out.println(BOLD + "📷 Image Viewer | " + newW + "×" + newH + RESET);
            System.out.printf("Zoom: %.1fx | Rotate: %d° | Flip: %d/%d\n", zoom, rotate, flipH, flipV);
            System.out.println("Controls: ← → (nav)  +/- (zoom)  r (rotate)  h/v (flip)  f (mode)  q (quit)");

            if (asciiMode) {
                String chars = " .:-=+*#%@";
                for (int y = 0; y < newH; y++) {
                    for (int x = 0; x < newW; x++) {
                        int rgb = resized.getRGB(x, y);
                        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                        double gray = 0.299 * r + 0.587 * g + 0.114 * b;
                        int idxChar = (int)(gray / 255 * (chars.length() - 1));
                        System.out.print(chars.charAt(idxChar));
                    }
                    System.out.println();
                }
            } else {
                for (int y = 0; y < newH; y++) {
                    for (int x = 0; x < newW; x++) {
                        int rgb = resized.getRGB(x, y);
                        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                        System.out.print(rgbToAnsi(r, g, b) + "██");
                    }
                    System.out.println(RESET);
                }
            }

            int key = System.in.read();
            if (key == 'q') break;
            else if (key == '+') zoom = Math.min(3.0, zoom + 0.2);
            else if (key == '-') zoom = Math.max(0.2, zoom - 0.2);
            else if (key == 'r') rotate = (rotate + 90) % 360;
            else if (key == 'h') flipH = 1 - flipH;
            else if (key == 'v') flipV = 1 - flipV;
            else if (key == 'f') asciiMode = !asciiMode;
            else if (key == 27) {
                // стрелка: читаем ещё два байта
                byte[] seq = new byte[2];
                System.in.read(seq);
                if (seq[0] == '[' && seq[1] == 'D' && idx > 0) {
                    idx--;
                    img = ImageIO.read(new File(files[idx]));
                } else if (seq[0] == '[' && seq[1] == 'C' && idx < files.length-1) {
                    idx++;
                    img = ImageIO.read(new File(files[idx]));
                }
            }
        }
    }
}
