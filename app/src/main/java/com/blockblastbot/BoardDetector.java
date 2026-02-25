package com.blockblastbot;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.ArrayList;
import java.util.List;

public class BoardDetector {

    public static final int GRID_SIZE = 8;

    // Wartości dla ekranu 1280x2772
    private static final float BOARD_TOP    = 0.24f;
    private static final float BOARD_BOTTOM = 0.38f;
    private static final float BOARD_LEFT   = 0.04f;
    private static final float BOARD_RIGHT  = 0.96f;
    private static final float PIECES_TOP   = 0.74f;
    private static final float PIECES_BOTTOM = 0.79f;

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

        int bTop   = (int)(h * BOARD_TOP);
        int bBot   = (int)(h * BOARD_BOTTOM);
        int bLeft  = (int)(w * BOARD_LEFT);
        int bRight = (int)(w * BOARD_RIGHT);
        int cellW  = (bRight - bLeft) / GRID_SIZE;
        int cellH  = (bBot - bTop) / GRID_SIZE;

        float bgBr = getBrightness(screenshot, bLeft + 2, bTop + 2);

        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int cx = bLeft + col * cellW + cellW / 2;
                int cy = bTop + row * cellH + cellH / 2;
                result.board[row][col] = getBrightness(screenshot, cx, cy) > bgBr + 25;
            }
        }

        int pTop   = (int)(h * PIECES_TOP);
        int pBot   = (int)(h * PIECES_BOTTOM);
        int pLeft  = (int)(w * 0.04f);
        int pRight = (int)(w * 0.96f);
        int slotW  = (pRight - pLeft) / 3;

        for (int i = 0; i < 3; i++) {
            int x1 = pLeft + i * slotW;
            int x2 = x1 + slotW;
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

    private static float getBrightness(Bitmap bmp, int x, int y) {
        if (x < 0 || x >= bmp.getWidth() || y < 0 || y >= bmp.getHeight()) return 128;
        int p = bmp.getPixel(x, y);
        return (Color.red(p) + Color.green(p) + Color.blue(p)) / 3f;
    }

    private static int[][] detectPieceShape(Bitmap bmp, int x1, int y1, int x2, int y2) {
        int pw = x2 - x1;
        int ph = y2 - y1;
        if (pw <= 0 || ph <= 0) return null;

        float bg = getBrightness(bmp, x1 + pw/2, y1 + ph/2);
        int step = Math.max(1, pw / 20);
        int gs = 5;
        int cw = Math.max(1, pw / gs);
        int ch = Math.max(1, ph / gs);

        int[][] counts = new int[gs][gs];
        int[][] total  = new int[gs][gs];

        for (int y = y1; y < y2; y += step) {
            for (int x = x1; x < x2; x += step) {
                int gr = Math.min(gs-1, (y-y1)/ch);
                int gc = Math.min(gs-1, (x-x1)/cw);
                total[gr][gc]++;
                if (getBrightness(bmp, x, y) > bg + 30) counts[gr][gc]++;
            }
        }

        List<int[]> cells = new ArrayList<>();
        int minR = gs, minC = gs;
        for (int r = 0; r < gs; r++) {
            for (int c = 0; c < gs; c++) {
                if (total[r][c] > 0 && (double)counts[r][c]/total[r][c] > 0.3) {
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
        int pTop  = (int)(sh * PIECES_TOP);
        int pLeft = (int)(sw * 0.04f);
        int pw    = (int)(sw * 0.92f);
        int ph    = (int)(sh * (PIECES_BOTTOM - PIECES_TOP));
        int slotW = pw / 3;
        return new float[]{pLeft + idx * slotW + slotW/2f, pTop + ph/2f};
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
