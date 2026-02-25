package com.blockblastbot;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.ArrayList;
import java.util.List;

public class BoardDetector {

    public static final int GRID_SIZE = 8;

    // Auto-calibrated values
    private static float boardTop    = 0.23f;
    private static float boardBottom = 0.42f;
    private static float boardLeft   = 0.04f;
    private static float boardRight  = 0.96f;
    private static float piecesTop   = 0.73f;
    private static float piecesBottom = 0.80f;
    private static boolean calibrated = false;

    public static class DetectionResult {
        public boolean[][] board = new boolean[GRID_SIZE][GRID_SIZE];
        public List<AIEngine.Shape> pieces = new ArrayList<>();
        public boolean valid = false;
    }

    public static DetectionResult detect(Bitmap screenshot) {
        DetectionResult result = new DetectionResult();
        if (screenshot == null) return result;

        if (!calibrated) autoCalibrate(screenshot);

        int w = screenshot.getWidth();
        int h = screenshot.getHeight();

        int bTop    = (int)(h * boardTop);
        int bBottom = (int)(h * boardBottom);
        int bLeft   = (int)(w * boardLeft);
        int bRight  = (int)(w * boardRight);
        int boardW  = bRight - bLeft;
        int boardH  = bBottom - bTop;
        int cellW   = boardW / GRID_SIZE;
        int cellH   = boardH / GRID_SIZE;

        float bgBrightness = getBrightness(screenshot, bLeft + 2, bTop + 2);

        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int cx = bLeft + col * cellW + cellW / 2;
                int cy = bTop + row * cellH + cellH / 2;
                result.board[row][col] = getBrightness(screenshot, cx, cy) > bgBrightness + 25;
            }
        }

        int pTop  = (int)(h * piecesTop);
        int pBot  = (int)(h * piecesBottom);
        int pLeft = (int)(w * 0.04f);
        int pRight = (int)(w * 0.96f);
        int pw = pRight - pLeft;
        int slotW = pw / 3;

        for (int i = 0; i < 3; i++) {
            int x1 = pLeft + i * slotW;
            int x2 = x1 + slotW;
            int[][] cells = detectPieceShape(screenshot, x1, pTop, x2, pBot);
            if (cells != null && cells.length > 0) {
                result.pieces.add(new AIEngine.Shape(cells, i));
            }
        }

        int filledCount = 0;
        for (boolean[] row : result.board)
            for (boolean c : row) if (c) filledCount++;

        result.valid = filledCount > 0 && filledCount < 62;
        return result;
    }

    private static void autoCalibrate(Bitmap bmp) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();

        // Find bright rows (blocks are brighter than background)
        int[] rowBrightness = new int[h];
        int step = Math.max(1, w / 50);
        for (int y = 0; y < h; y++) {
            int sum = 0;
            int count = 0;
            for (int x = w/4; x < 3*w/4; x += step) {
                sum += getBrightness(bmp, x, y);
                count++;
            }
            rowBrightness[y] = count > 0 ? sum / count : 0;
        }

        // Find overall background brightness
        int bgBr = rowBrightness[h/2];

        // Find board region (large area with many bright pixels)
        int boardStartY = -1, boardEndY = -1;
        int piecesStartY = -1, piecesEndY = -1;

        // Scan for bright regions
        int consecutiveBright = 0;
        int consecutiveDark = 0;
        List<int[]> brightRegions = new ArrayList<>();
        int regionStart = -1;

        for (int y = (int)(h * 0.1f); y < (int)(h * 0.95f); y++) {
            boolean bright = rowBrightness[y] > bgBr + 15;
            if (bright) {
                if (regionStart < 0) regionStart = y;
                consecutiveBright++;
                consecutiveDark = 0;
            } else {
                if (consecutiveDark > 20 && regionStart >= 0 && consecutiveBright > 30) {
                    brightRegions.add(new int[]{regionStart, y - consecutiveDark});
                    regionStart = -1;
                    consecutiveBright = 0;
                }
                consecutiveDark++;
            }
        }
        if (regionStart >= 0 && consecutiveBright > 30) {
            brightRegions.add(new int[]{regionStart, (int)(h * 0.95f)});
        }

        // Board = largest region, pieces = second largest after board
        if (brightRegions.size() >= 1) {
            // Sort by size
            brightRegions.sort((a, b) -> (b[1]-b[0]) - (a[1]-a[0]));
            int[] boardRegion = brightRegions.get(0);
            boardStartY = boardRegion[0];
            boardEndY = boardRegion[1];

            if (brightRegions.size() >= 2) {
                int[] piecesRegion = brightRegions.get(1);
                piecesStartY = piecesRegion[0];
                piecesEndY = piecesRegion[1];
            }
        }

        if (boardStartY > 0 && boardEndY > boardStartY) {
            boardTop    = (float)(boardStartY - 10) / h;
            boardBottom = (float)(boardEndY + 10) / h;
            boardTop    = Math.max(0.1f, boardTop);
            boardBottom = Math.min(0.9f, boardBottom);
        }

        if (piecesStartY > 0 && piecesEndY > piecesStartY) {
            piecesTop    = (float)(piecesStartY - 10) / h;
            piecesBottom = (float)(piecesEndY + 10) / h;
            piecesTop    = Math.max(0.5f, piecesTop);
            piecesBottom = Math.min(0.95f, piecesBottom);
        }

        calibrated = true;
        android.util.Log.d("BoardDetector",
            "Calibrated: board=" + boardTop + "-" + boardBottom +
            " pieces=" + piecesTop + "-" + piecesBottom);
    }

    private static float getBrightness(Bitmap bmp, int x, int y) {
        if (x < 0 || x >= bmp.getWidth() || y < 0 || y >= bmp.getHeight()) return 128;
        int pixel = bmp.getPixel(x, y);
        return (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3f;
    }

    private static int[][] detectPieceShape(Bitmap bmp, int x1, int y1, int x2, int y2) {
        int pw = x2 - x1;
        int ph = y2 - y1;
        if (pw <= 0 || ph <= 0) return null;

        float bg = getBrightness(bmp, x1 + pw/2, y1 + ph/2);
        int sampleStep = Math.max(1, pw / 20);
        int gridSize = 5;
        int cellW = Math.max(1, pw / gridSize);
        int cellH = Math.max(1, ph / gridSize);

        int[][] counts = new int[gridSize][gridSize];
        int[][] total  = new int[gridSize][gridSize];

        for (int y = y1; y < y2; y += sampleStep) {
            for (int x = x1; x < x2; x += sampleStep) {
                int gr = Math.min(gridSize-1, (y - y1) / cellH);
                int gc = Math.min(gridSize-1, (x - x1) / cellW);
                total[gr][gc]++;
                if (getBrightness(bmp, x, y) > bg + 30) counts[gr][gc]++;
            }
        }

        List<int[]> cells = new ArrayList<>();
        int minRow = gridSize, minCol = gridSize;
        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                if (total[r][c] > 0 && (double)counts[r][c]/total[r][c] > 0.3) {
                    cells.add(new int[]{r, c});
                    if (r < minRow) minRow = r;
                    if (c < minCol) minCol = c;
                }
            }
        }

        if (cells.isEmpty()) return null;
        int fr = minRow, fc = minCol;
        int[][] result = new int[cells.size()][2];
        for (int i = 0; i < cells.size(); i++) {
            result[i][0] = cells.get(i)[0] - fr;
            result[i][1] = cells.get(i)[1] - fc;
        }
        return result;
    }

    public static float[] getPieceCenter(int screenW, int screenH, int pieceIdx) {
        int pTop  = (int)(screenH * piecesTop);
        int pLeft = (int)(screenW * 0.04f);
        int pw    = (int)(screenW * 0.92f);
        int ph    = (int)(screenH * (piecesBottom - piecesTop));
        int slotW = pw / 3;
        float cx = pLeft + pieceIdx * slotW + slotW / 2f;
        float cy = pTop + ph / 2f;
        return new float[]{cx, cy};
    }

    public static float[] getBoardCellCenter(int screenW, int screenH, int row, int col) {
        int bTop  = (int)(screenH * boardTop);
        int bLeft = (int)(screenW * boardLeft);
        int bW    = (int)(screenW * (boardRight - boardLeft));
        int bH    = (int)(screenH * (boardBottom - boardTop));
        int cellW = bW / GRID_SIZE;
        int cellH = bH / GRID_SIZE;
        float cx = bLeft + col * cellW + cellW / 2f;
        float cy = bTop + row * cellH + cellH / 2f;
        return new float[]{cx, cy};
    }

    // Reset calibration (call when game restarts)
    public static void resetCalibration() {
        calibrated = false;
    }
}
