// image_viewer.js
#!/usr/bin/env node
'use strict';

const fs = require('fs');
const path = require('path');
const sharp = require('sharp');
const { execSync } = require('child_process');
const readline = require('readline');

const COLORS = {
    reset: '\x1b[0m',
    bold: '\x1b[1m'
};

function rgbToAnsi(r, g, b) {
    return `\x1b[38;2;${r};${g};${b}m`;
}

function getImageFiles(dir) {
    if (fs.statSync(dir).isFile()) return [dir];
    const exts = ['.jpg', '.jpeg', '.png', '.gif', '.bmp', '.tiff', '.webp'];
    const files = fs.readdirSync(dir)
        .filter(f => exts.includes(path.extname(f).toLowerCase()))
        .map(f => path.join(dir, f))
        .sort();
    return files;
}

async function displayImage(imgPath, zoom, rotate, flipH, flipV, asciiMode, termW, termH) {
    console.clear();
    let img = sharp(imgPath);
    const meta = await img.metadata();
    let w = meta.width, h = meta.height;

    // Поворот
    if (rotate === 90) { img = img.rotate(90); [w, h] = [h, w]; }
    else if (rotate === 180) { img = img.rotate(180); }
    else if (rotate === 270) { img = img.rotate(-90); [w, h] = [h, w]; }
    // Флип
    if (flipH) img = img.flop();
    if (flipV) img = img.flip();

    const maxW = termW - 2;
    const maxH = termH - 6;
    let scale = zoom;
    if (scale === 1.0) {
        const sx = maxW / w;
        const sy = maxH / h;
        scale = Math.min(sx, sy, 1);
    }
    const newW = Math.max(1, Math.round(w * scale));
    const newH = Math.max(1, Math.round(h * scale));
    img = img.resize(newW, newH, { kernel: sharp.kernel.lanczos2 });

    const buffer = await img.raw().toBuffer({ resolveWithObject: true });
    const data = buffer.data;
    const sw = buffer.info.width;
    const sh = buffer.info.height;

    console.log(`${COLORS.bold}📷 Image Viewer | ${sw}×${sh}${COLORS.reset}`);
    console.log(`Zoom: ${zoom.toFixed(1)}x | Rotate: ${rotate}° | Flip: ${flipH}/${flipV}`);
    console.log('Controls: ← → (nav)  +/- (zoom)  r (rotate)  h/v (flip)  f (mode)  q (quit)');

    if (asciiMode) {
        const chars = ' .:-=+*#%@';
        for (let y = 0; y < sh; y++) {
            let line = '';
            for (let x = 0; x < sw; x++) {
                const idx = (y * sw + x) * 3;
                const r = data[idx], g = data[idx+1], b = data[idx+2];
                const gray = 0.299 * r + 0.587 * g + 0.114 * b;
                const ci = Math.floor((gray / 255) * (chars.length - 1));
                line += chars[ci];
            }
            console.log(line);
        }
    } else {
        for (let y = 0; y < sh; y++) {
            let line = '';
            for (let x = 0; x < sw; x++) {
                const idx = (y * sw + x) * 3;
                const r = data[idx], g = data[idx+1], b = data[idx+2];
                line += rgbToAnsi(r, g, b) + '██';
            }
            line += COLORS.reset;
            console.log(line);
        }
    }
}

function getKey() {
    return new Promise(resolve => {
        const rl = readline.createInterface({
            input: process.stdin,
            output: process.stdout,
            terminal: true
        });
        process.stdin.setRawMode(true);
        process.stdin.once('data', (data) => {
            process.stdin.setRawMode(false);
            rl.close();
            resolve(data.toString());
        });
    });
}

async function main() {
    const args = process.argv.slice(2);
    let pathArg = '.', zoom = 1.0, rotate = 0, asciiMode = false;
    let convertFormat = '', outputFile = '';
    for (let i = 0; i < args.length; i++) {
        if (args[i] === '-z' && i+1 < args.length) zoom = parseFloat(args[++i]);
        else if (args[i] === '-r' && i+1 < args.length) rotate = parseInt(args[++i]);
        else if (args[i] === '-a') asciiMode = true;
        else if (args[i] === '-c' && i+1 < args.length) convertFormat = args[++i];
        else if (args[i] === '-o' && i+1 < args.length) outputFile = args[++i];
        else if (args[i] === '-h' || args[i] === '--help') {
            console.log('Usage: node image_viewer.js <path> [-z zoom] [-r rotate] [-a] [-c format] [-o output]');
            process.exit(0);
        } else pathArg = args[i];
    }

    const files = getImageFiles(pathArg);
    if (files.length === 0) { console.log('Нет изображений.'); process.exit(1); }
    let idx = 0, flipH = false, flipV = false;

    if (convertFormat && outputFile) {
        await sharp(files[idx]).toFile(outputFile);
        console.log(`✅ Изображение сохранено в ${outputFile}`);
        return;
    }

    const termW = process.stdout.columns || 80;
    const termH = process.stdout.rows || 24;

    while (true) {
        await displayImage(files[idx], zoom, rotate, flipH, flipV, asciiMode, termW, termH);
        const key = await getKey();
        if (key === 'q') break;
        else if (key === '+') zoom = Math.min(3.0, zoom + 0.2);
        else if (key === '-') zoom = Math.max(0.2, zoom - 0.2);
        else if (key === 'r') rotate = (rotate + 90) % 360;
        else if (key === 'h') flipH = !flipH;
        else if (key === 'v') flipV = !flipV;
        else if (key === 'f') asciiMode = !asciiMode;
        else if (key === '\x1b') {
            // стрелка
            const seq = await getKey();
            if (seq === '[D' && idx > 0) idx--;
            else if (seq === '[C' && idx < files.length-1) idx++;
        }
    }
}

main().catch(console.error);
