// image_viewer.cpp
#include <opencv2/opencv.hpp>
#include <iostream>
#include <vector>
#include <string>
#include <filesystem>
#include <termios.h>
#include <unistd.h>
#include <fcntl.h>
#include <algorithm>

using namespace std;
using namespace cv;

namespace fs = std::filesystem;

string RESET = "\033[0m";
string BOLD = "\033[1m";

string rgbToAnsi(int r, int g, int b) {
    return "\033[38;2;" + to_string(r) + ";" + to_string(g) + ";" + to_string(b) + "m";
}

vector<string> getImageFiles(const string& path) {
    vector<string> files;
    if (fs::is_regular_file(path)) {
        files.push_back(path);
    } else if (fs::is_directory(path)) {
        vector<string> exts = {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".tiff", ".webp"};
        for (const auto& entry : fs::directory_iterator(path)) {
            string ext = entry.path().extension().string();
            transform(ext.begin(), ext.end(), ext.begin(), ::tolower);
            if (find(exts.begin(), exts.end(), ext) != exts.end()) {
                files.push_back(entry.path().string());
            }
        }
        sort(files.begin(), files.end());
    }
    return files;
}

char getch() {
    struct termios oldt, newt;
    char ch;
    tcgetattr(STDIN_FILENO, &oldt);
    newt = oldt;
    newt.c_lflag &= ~(ICANON | ECHO);
    tcsetattr(STDIN_FILENO, TCSANOW, &newt);
    ch = getchar();
    tcsetattr(STDIN_FILENO, TCSANOW, &oldt);
    return ch;
}

int getTerminalWidth() {
    struct winsize w;
    ioctl(STDOUT_FILENO, TIOCGWINSZ, &w);
    return w.ws_col;
}

int getTerminalHeight() {
    struct winsize w;
    ioctl(STDOUT_FILENO, TIOCGWINSZ, &w);
    return w.ws_row;
}

void displayImage(const Mat& img, int zoom, int rotate, bool flipH, bool flipV, bool asciiMode) {
    Mat display = img.clone();
    if (rotate == 90) rotate(display, display, ROTATE_90_CLOCKWISE);
    else if (rotate == 180) rotate(display, display, ROTATE_180);
    else if (rotate == 270) rotate(display, display, ROTATE_90_COUNTERCLOCKWISE);
    if (flipH) flip(display, display, 1);
    if (flipV) flip(display, display, 0);

    int w = display.cols, h = display.rows;
    int termW = getTerminalWidth() - 2;
    int termH = getTerminalHeight() - 6;
    double scale = zoom;
    if (scale == 1.0) {
        double scaleX = (double)termW / w;
        double scaleY = (double)termH / h;
        scale = min(scaleX, scaleY);
        if (scale > 1.0) scale = 1.0;
    }
    int newW = max(1, (int)(w * scale));
    int newH = max(1, (int)(h * scale));
    Mat resized;
    resize(display, resized, Size(newW, newH), 0, 0, INTER_LANCZOS4);

    cout << "\033[H\033[2J";
    cout << BOLD << "📷 Image Viewer | " << newW << "×" << newH << BOLD << RESET << endl;
    cout << "Zoom: " << zoom << "x | Rotate: " << rotate << "° | Flip: " << flipH << "/" << flipV << endl;
    cout << "Controls: ← → (nav)  +/- (zoom)  r (rotate)  h/v (flip)  f (mode)  q (quit)" << endl;

    if (asciiMode) {
        Mat gray;
        cvtColor(resized, gray, COLOR_BGR2GRAY);
        string chars = " .:-=+*#%@";
        for (int r = 0; r < gray.rows; ++r) {
            for (int c = 0; c < gray.cols; ++c) {
                int val = gray.at<uchar>(r, c);
                int idx = val * (chars.size()-1) / 255;
                cout << chars[idx];
            }
            cout << endl;
        }
    } else {
        for (int r = 0; r < resized.rows; ++r) {
            for (int c = 0; c < resized.cols; ++c) {
                Vec3b pixel = resized.at<Vec3b>(r, c);
                cout << rgbToAnsi(pixel[2], pixel[1], pixel[0]) << "██";
            }
            cout << RESET << endl;
        }
    }
}

int main(int argc, char* argv[]) {
    string path = ".";
    double zoom = 1.0;
    int rotate = 0;
    bool asciiMode = false;
    string convertFormat, outputFile;

    for (int i = 1; i < argc; ++i) {
        string arg = argv[i];
        if (arg == "-z" && i+1 < argc) zoom = stod(argv[++i]);
        else if (arg == "-r" && i+1 < argc) rotate = stoi(argv[++i]);
        else if (arg == "-a") asciiMode = true;
        else if (arg == "-c" && i+1 < argc) convertFormat = argv[++i];
        else if (arg == "-o" && i+1 < argc) outputFile = argv[++i];
        else if (arg == "-h" || arg == "--help") {
            cout << "Usage: image_viewer <path> [-z zoom] [-r rotate] [-a] [-c format] [-o output]" << endl;
            return 0;
        } else if (path == ".") path = arg;
    }

    auto files = getImageFiles(path);
    if (files.empty()) {
        cerr << "Нет изображений." << endl;
        return 1;
    }

    int idx = 0;
    bool flipH = false, flipV = false;
    Mat img = imread(files[idx]);
    if (img.empty()) { cerr << "Не удалось загрузить " << files[idx] << endl; return 1; }

    if (!convertFormat.empty() && !outputFile.empty()) {
        imwrite(outputFile, img);
        cout << "✅ Изображение сохранено в " << outputFile << endl;
        return 0;
    }

    while (true) {
        displayImage(img, zoom, rotate, flipH, flipV, asciiMode);
        char key = getch();
        if (key == 'q') break;
        else if (key == '+') zoom = min(3.0, zoom + 0.2);
        else if (key == '-') zoom = max(0.2, zoom - 0.2);
        else if (key == 'r') { rotate = (rotate + 90) % 360; }
        else if (key == 'h') { flipH = !flipH; }
        else if (key == 'v') { flipV = !flipV; }
        else if (key == 'f') { asciiMode = !asciiMode; }
        else if (key == 27) { // ESC
            char seq[2];
            if (getchar() == '[') {
                char c = getchar();
                if (c == 'D' && idx > 0) { idx--; img = imread(files[idx]); }
                else if (c == 'C' && idx < (int)files.size()-1) { idx++; img = imread(files[idx]); }
            }
        }
    }
    return 0;
}
