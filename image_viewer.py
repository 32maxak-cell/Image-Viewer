# image_viewer.py
#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os
import sys
import argparse
from PIL import Image, ImageOps
import math
import time
import platform

# ANSI-цвета
COLORS = {
    'reset': '\033[0m',
    'bold': '\033[1m'
}

def rgb_to_ansi(r, g, b):
    return f'\033[38;2;{r};{g};{b}m'

class ImageViewer:
    def __init__(self, path, zoom=1.0, rotate=0, ascii_mode=False):
        self.path = path
        self.zoom = zoom
        self.rotate = rotate
        self.ascii_mode = ascii_mode
        self.files = self.get_image_files()
        self.current_idx = 0
        self.flip_h = False
        self.flip_v = False
        self.term_width = self.get_terminal_size()[0]
        self.term_height = self.get_terminal_size()[1]
        self.load_image()

    def get_terminal_size(self):
        try:
            import shutil
            cols, rows = shutil.get_terminal_size()
            return cols, rows
        except:
            return 80, 24

    def get_image_files(self):
        if os.path.isfile(self.path):
            return [self.path]
        elif os.path.isdir(self.path):
            exts = ('.jpg', '.jpeg', '.png', '.gif', '.bmp', '.tiff', '.webp')
            return sorted([os.path.join(self.path, f) for f in os.listdir(self.path)
                          if f.lower().endswith(exts)])
        return []

    def load_image(self):
        if not self.files:
            print("Нет изображений для отображения.")
            sys.exit(1)
        self.current_file = self.files[self.current_idx]
        self.img = Image.open(self.current_file).convert('RGB')
        self.original = self.img.copy()
        self.apply_transforms()

    def apply_transforms(self):
        self.img = self.original.copy()
        if self.rotate == 90:
            self.img = self.img.rotate(-90, expand=True)
        elif self.rotate == 180:
            self.img = self.img.rotate(180, expand=True)
        elif self.rotate == 270:
            self.img = self.img.rotate(90, expand=True)
        if self.flip_h:
            self.img = ImageOps.mirror(self.img)
        if self.flip_v:
            self.img = ImageOps.flip(self.img)

    def resize_for_terminal(self):
        w, h = self.img.size
        max_w = self.term_width - 2
        max_h = self.term_height - 6
        # Масштабирование
        scale = self.zoom
        if scale == 1.0:
            # Авто-масштаб
            scale_x = max_w / w
            scale_y = max_h / h
            scale = min(scale_x, scale_y, 1.0)
        new_w = int(w * scale)
        new_h = int(h * scale)
        if new_w < 1: new_w = 1
        if new_h < 1: new_h = 1
        return self.img.resize((new_w, new_h), Image.Resampling.LANCZOS)

    def to_ascii(self, img):
        chars = [' ', '.', ':', '-', '=', '+', '*', '#', '%', '@']
        gray = img.convert('L')
        pixels = gray.getdata()
        ascii_img = []
        w, h = img.size
        for i in range(h):
            row = []
            for j in range(w):
                brightness = pixels[i*w + j]
                idx = int(brightness / 255 * (len(chars)-1))
                row.append(chars[idx])
            ascii_img.append(''.join(row))
        return ascii_img

    def to_color_blocks(self, img):
        pixels = img.getdata()
        w, h = img.size
        blocks = []
        for i in range(h):
            row = []
            for j in range(w):
                r, g, b = pixels[i*w + j]
                row.append(rgb_to_ansi(r, g, b) + '██')
            row.append(COLORS['reset'])
            blocks.append(''.join(row))
        return blocks

    def display(self):
        os.system('clear' if platform.system() != 'Windows' else 'cls')
        img_resized = self.resize_for_terminal()
        w, h = img_resized.size
        print(f"\033[1m📷 {os.path.basename(self.current_file)}  |  {w}×{h}  |  [{self.current_idx+1}/{len(self.files)}]\033[0m")
        print(f"Масштаб: {self.zoom:.1f}x | Поворот: {self.rotate}° | Flipped: {self.flip_h}/{self.flip_v}")
        print("Управление: ← → (навигация)  +/- (масштаб)  r (поворот)  h/v (flip)  f (режим)  q (выход)")

        if self.ascii_mode:
            lines = self.to_ascii(img_resized)
            for line in lines:
                print(line)
        else:
            blocks = self.to_color_blocks(img_resized)
            for line in blocks:
                print(line)

    def next_image(self):
        if self.current_idx < len(self.files) - 1:
            self.current_idx += 1
            self.load_image()

    def prev_image(self):
        if self.current_idx > 0:
            self.current_idx -= 1
            self.load_image()

    def run_interactive(self):
        import termios
        import tty
        import sys

        def getch():
            fd = sys.stdin.fileno()
            old = termios.tcgetattr(fd)
            try:
                tty.setraw(fd)
                ch = sys.stdin.read(1)
                if ch == '\x1b':
                    ch2 = sys.stdin.read(2)
                    if ch2 == '[A': return 'up'
                    elif ch2 == '[B': return 'down'
                    elif ch2 == '[D': return 'left'
                    elif ch2 == '[C': return 'right'
                    else: return ch + ch2
                return ch
            finally:
                termios.tcsetattr(fd, termios.TCSADRAIN, old)

        while True:
            self.display()
            key = getch()
            if key == 'q':
                break
            elif key == 'left':
                self.prev_image()
            elif key == 'right':
                self.next_image()
            elif key == '+':
                self.zoom = min(3.0, self.zoom + 0.2)
            elif key == '-':
                self.zoom = max(0.2, self.zoom - 0.2)
            elif key == 'r':
                self.rotate = (self.rotate + 90) % 360
                self.apply_transforms()
            elif key == 'h':
                self.flip_h = not self.flip_h
                self.apply_transforms()
            elif key == 'v':
                self.flip_v = not self.flip_v
                self.apply_transforms()
            elif key == 'f':
                self.ascii_mode = not self.ascii_mode

def main():
    parser = argparse.ArgumentParser(description="Image Viewer")
    parser.add_argument('path', help='Файл или папка с изображениями')
    parser.add_argument('-z', '--zoom', type=float, default=1.0, help='Масштаб')
    parser.add_argument('-r', '--rotate', type=int, choices=[0, 90, 180, 270], default=0, help='Поворот')
    parser.add_argument('-a', '--ascii', action='store_true', help='ASCII-режим')
    parser.add_argument('-c', '--convert', help='Конвертировать в формат (png, jpg, bmp, webp)')
    parser.add_argument('-o', '--output', help='Выходной файл для конвертации')
    args = parser.parse_args()

    viewer = ImageViewer(args.path, args.zoom, args.rotate, args.ascii)
    if args.convert and args.output:
        # Конвертация
        viewer.load_image()
        viewer.img.save(args.output, format=args.convert.upper())
        print(f"✅ Изображение сохранено в {args.output}")
        return

    try:
        viewer.run_interactive()
    except KeyboardInterrupt:
        print("\nВыход.")
        sys.exit(0)

if __name__ == '__main__':
    main()
