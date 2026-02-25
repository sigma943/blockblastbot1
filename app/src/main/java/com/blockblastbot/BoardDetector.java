package com.blockblastbot;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.ArrayList;
import java.util.List;

public class BoardDetector {

    public static final int GRID_SIZE = 8;

    // Dokladne wartosci zmierzone z screenshota 1280x2772
    private static final float BOARD_TOP    = 0.235f;
    private static final float BOARD_BOTTOM = 0.645f;
    private static final float BOARD_LEFT   = 0.056f;
    private static final float BOARD_RIGHT  = 0.944f;
    private static final float PIECES_TOP   = 0.740f;
    private static final float PIECES_BOTTOM = 0.800f;

    // Centra 3 klockow (% szerokosci)
    private static final float[] PIECE_CENTERS_X = {0.159f, 0.475f, 0.841f};

    public static class DetectionResult {
        public boolean[][] board = new boolean[GRID_SIZE][GRID_SIZE];
        public List<AIEngine.Shape> pieces = new ArrayList<>();
        public boolean valid = false;
    }

    public static DetectionResult detect(Bitmap screenshot) {
        DetectionResult result = new DetectionResult();
        if (screenshot == null) return result;

        int w = screenshot.getWidth();
        int h = screenshot.getHeight();

        // Wykryj plansze
        int bTop   = (int)(h * BOARD_TOP);
        int bBot   = (int)(h * BOARD_BOTTOM);
        int bLeft  = (int)(w * BOARD_LEFT);
        int bRight = (int)(w * BOARD_RIGHT);
        int cellW  = (bRight - bLeft) / GRID_SIZE;
        int cellH  = (bBot - bTop) / GRID_SIZE;

        // Kolor tla planszy (ciemny) vs klocki (jasne)
        // Probkuj srodek kazdej komorki
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int cx = bLeft + col * cellW + cellW / 2;
                int cy = bTop + row * cellH + cellH / 2;
                // Sprawdz srednia 3x3 pikseli
                float br = avgBrightness(screenshot, cx, cy, 5);
                // Tlo planszy: brightness < 60, klocki: > 80
                result.board[row][col] = br > 70;
            }
        }

        // Wykryj klocki
        int pTop = (int)(h * PIECES_TOP);
        int pBot = (int)(h * PIECES_BOTTOM);
        int slotW = (int)(w * 0.28f); // szerokosc slotu klocka

        for (int i = 0; i < 3; i++) {
            int cx = (int)(w * PIECE_CENTERS_X[i]);
            int x1 = cx - slotW/2;
            int x2 = cx + slotW/2;
            x1 = Math.max(0, x1);
            x2 = Math.min(w-1, x2);
            int[][] cells = detectPieceShape(screenshot, x1, pTop, x2, pBot);
            if (cells != null && cells.length > 0) {
                result.pieces.add(new AIEngine.Shape(cells, i));
            }
        }

        int filled = 0;
        for (boolean[] row : result.board)
            for (boolean c : row) if (c) filled++;

        result.valid = filled > 0 && filled < 62;
        return result;
    }

    private static float avgBrightness(Bitmap bmp, int cx, int cy, int radius) {
        float sum = 0;
        int count = 0;
        for (int dy = -radius; dy <= radius; dy += radius) {
            for (int dx = -radius; dx <= radius; dx += radius) {
                int x = cx + dx;
                int y = cy + dy;
                if (x >= 0 && x < bmp.getWidth() && y >= 0 && y < bmp.getHeight()) {
                    int p = bmp.getPixel(x, y);
                    sum += (Color.red(p) + Color.green(p) + Color.blue(p)) / 3f;
                    count++;
                }
            }
        }
        return count > 0 ? sum / count : 0;
    }

    private static float getBrightness(Bitmap bmp, int x, int y) {
        if (x < 0 || x >= bmp.getWidth() || y < 0 || y >= bmp.getHeight()) return 0;
        int p = bmp.getPixel(x, y);
        return (Color.red(p) + Color.green(p) + Color.blue(p)) / 3f;
    }

    private static int[][] detectPieceShape(Bitmap bmp, int x1, int y1, int x2, int y2) {
        int pw = x2 - x1;
        int ph = y2 - y1;
        if (pw <= 0 || ph <= 0) return null;

        // Tlo gry: RGB ~(170, 78, 82)
        int bgR = 170, bgG = 78, bgB = 82;
        int gs = 5;
        int cw = Math.max(1, pw / gs);
        int ch = Math.max(1, ph / gs);
        int step = Math.max(1, cw / 4);

        int[][] counts = new int[gs][gs];
        int[][] total  = new int[gs][gs];

        for (int y = y1; y < y2; y += step) {
            for (int x = x1; x < x2; x += step) {
                if (x >= bmp.getWidth() || y >= bmp.getHeight()) continue;
                int p = bmp.getPixel(x, y);
                int r = Color.red(p), g = Color.green(p), b = Color.blue(p);
                int gr = Math.min(gs-1, (y-y1)/ch);
                int gc = Math.min(gs-1, (x-x1)/cw);
                total[gr][gc]++;
                // Klocek = nie jest tlem gry
                boolean isBg = Math.abs(r-bgR)<35 && Math.abs(g-bgG)<25 && Math.abs(b-bgB)<25;
                if (!isBg && (r+g+b) > 150) counts[gr][gc]++;
            }
        }

        List<int[]> cells = new ArrayList<>();
        int minR = gs, minC = gs;
        for (int r = 0; r < gs; r++) {
            for (int c = 0; c < gs; c++) {
                if (total[r][c] > 0 && (double)counts[r][c]/total[r][c] > 0.35) {
                    cells.add(new int[]{r, c});
                    if (r < minR) minR = r;
                    if (c < minC) minC = c;
                }
            }
        }

        if (cells.isEmpty()) return null;
        int fr = minR, fc = minC;
        int[][] res = new int[cells.size()][2];
        for (int i = 0; i < cells.size(); i++) {
            res[i][0] = cells.get(i)[0] - fr;
            res[i][1] = cells.get(i)[1] - fc;
        }
        return res;
    }

    public static float[] getPieceCenter(int sw, int sh, int idx) {
        float cx = sw * PIECE_CENTERS_X[idx];
        float cy = sh * (PIECES_TOP + (PIECES_BOTTOM - PIECES_TOP) / 2f);
        return new float[]{cx, cy};
    }

    public static float[] getBoardCellCenter(int sw, int sh, int row, int col) {
        int bTop  = (int)(sh * BOARD_TOP);
        int bLeft = (int)(sw * BOARD_LEFT);
        int bW    = (int)(sw * (BOARD_RIGHT - BOARD_LEFT));
        int bH    = (int)(sh * (BOARD_BOTTOM - BOARD_TOP));
        int cw    = bW / GRID_SIZE;
        int ch    = bH / GRID_SIZE;
        return new float[]{bLeft + col*cw + cw/2f, bTop + row*ch + ch/2f};
    }
}
