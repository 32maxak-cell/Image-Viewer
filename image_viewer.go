// image_viewer.go
package main

import (
	"bufio"
	"flag"
	"fmt"
	"image"
	"image/color"
	_ "image/gif"
	_ "image/jpeg"
	_ "image/png"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"

	"github.com/disintegration/imaging"
	"golang.org/x/term"
)

const (
	reset = "\033[0m"
	bold  = "\033[1m"
)

func rgbToAnsi(r, g, b uint8) string {
	return fmt.Sprintf("\033[38;2;%d;%d;%dm", r, g, b)
}

func getImageFiles(path string) []string {
	var files []string
	info, err := os.Stat(path)
	if err != nil {
		return files
	}
	if !info.IsDir() {
		return []string{path}
	}
	exts := map[string]bool{".jpg": true, ".jpeg": true, ".png": true, ".gif": true, ".bmp": true, ".tiff": true, ".webp": true}
	entries, _ := os.ReadDir(path)
	for _, e := range entries {
		if !e.IsDir() {
			ext := strings.ToLower(filepath.Ext(e.Name()))
			if exts[ext] {
				files = append(files, filepath.Join(path, e.Name()))
			}
		}
	}
	sort.Strings(files)
	return files
}

func displayImage(img image.Image, zoom float64, rotate int, flipH, flipV bool, asciiMode bool, termW, termH int) {
	// Применяем трансформации
	var processed image.Image = img
	if rotate == 90 {
		processed = imaging.Rotate90(processed)
	} else if rotate == 180 {
		processed = imaging.Rotate180(processed)
	} else if rotate == 270 {
		processed = imaging.Rotate270(processed)
	}
	if flipH {
		processed = imaging.FlipH(processed)
	}
	if flipV {
		processed = imaging.FlipV(processed)
	}
	// Масштабирование
	bounds := processed.Bounds()
	w, h := bounds.Dx(), bounds.Dy()
	maxW := termW - 2
	maxH := termH - 6
	scale := zoom
	if scale == 1.0 {
		scaleX := float64(maxW) / float64(w)
		scaleY := float64(maxH) / float64(h)
		scale = min(scaleX, scaleY)
		if scale > 1.0 {
			scale = 1.0
		}
	}
	newW := int(float64(w) * scale)
	newH := int(float64(h) * scale)
	if newW < 1 {
		newW = 1
	}
	if newH < 1 {
		newH = 1
	}
	resized := imaging.Resize(processed, newW, newH, imaging.Lanczos)

	fmt.Print("\033[H\033[2J")
	fmt.Printf("%s📷 Image Viewer | %d×%d%s\n", bold, newW, newH, reset)
	fmt.Printf("Zoom: %.1fx | Rotate: %d° | Flip: %t/%t\n", zoom, rotate, flipH, flipV)
	fmt.Println("Controls: ← → (nav)  +/- (zoom)  r (rotate)  h/v (flip)  f (mode)  q (quit)")

	if asciiMode {
		chars := " .:-=+*#%@"
		for y := 0; y < newH; y++ {
			for x := 0; x < newW; x++ {
				c := resized.At(x, y)
				r, g, b, _ := c.RGBA()
				gray := 0.299*float64(r>>8) + 0.587*float64(g>>8) + 0.114*float64(b>>8)
				idx := int(gray / 255.0 * float64(len(chars)-1))
				fmt.Print(string(chars[idx]))
			}
			fmt.Println()
		}
	} else {
		for y := 0; y < newH; y++ {
			for x := 0; x < newW; x++ {
				c := resized.At(x, y)
				r, g, b, _ := c.RGBA()
				fmt.Print(rgbToAnsi(uint8(r>>8), uint8(g>>8), uint8(b>>8)) + "██")
			}
			fmt.Println(reset)
		}
	}
}

func min(a, b float64) float64 {
	if a < b {
		return a
	}
	return b
}

func getch() byte {
	oldState, _ := term.MakeRaw(int(os.Stdin.Fd()))
	defer term.Restore(int(os.Stdin.Fd()), oldState)
	var buf [1]byte
	os.Stdin.Read(buf[:])
	return buf[0]
}

func main() {
	var path string
	var zoom float64
	var rotate int
	var asciiMode bool
	var convertFormat string
	var outputFile string
	flag.StringVar(&path, "path", ".", "Файл или папка")
	flag.Float64Var(&zoom, "z", 1.0, "Масштаб")
	flag.IntVar(&rotate, "r", 0, "Поворот")
	flag.BoolVar(&asciiMode, "a", false, "ASCII-режим")
	flag.StringVar(&convertFormat, "c", "", "Конвертировать в формат")
	flag.StringVar(&outputFile, "o", "", "Выходной файл")
	flag.Parse()
	if flag.NArg() > 0 {
		path = flag.Arg(0)
	}

	files := getImageFiles(path)
	if len(files) == 0 {
		fmt.Println("Нет изображений.")
		return
	}

	idx := 0
	flipH, flipV := false, false
	img, err := imaging.Open(files[idx])
	if err != nil {
		fmt.Println("Ошибка загрузки:", err)
		return
	}

	if convertFormat != "" && outputFile != "" {
		err := imaging.Save(img, outputFile)
		if err == nil {
			fmt.Printf("✅ Изображение сохранено в %s\n", outputFile)
		}
		return
	}

	termW, termH, _ := term.GetSize(int(os.Stdin.Fd()))

	for {
		displayImage(img, zoom, rotate, flipH, flipV, asciiMode, termW, termH)
		key := getch()
		switch key {
		case 'q':
			return
		case '+':
			if zoom < 3.0 {
				zoom += 0.2
			}
		case '-':
			if zoom > 0.2 {
				zoom -= 0.2
			}
		case 'r':
			rotate = (rotate + 90) % 360
		case 'h':
			flipH = !flipH
		case 'v':
			flipV = !flipV
		case 'f':
			asciiMode = !asciiMode
		case 27:
			// стрелка
			buf := make([]byte, 2)
			os.Stdin.Read(buf)
			if buf[0] == '[' {
				if buf[1] == 'D' && idx > 0 {
					idx--
					img, _ = imaging.Open(files[idx])
				} else if buf[1] == 'C' && idx < len(files)-1 {
					idx++
					img, _ = imaging.Open(files[idx])
				}
			}
		}
	}
}
