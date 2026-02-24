package com.blockblastbot;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.ArrayList;
import java.util.List;

public class BoardDetector {

    public static final int GRID_SIZE = 8;

    private static final float BOARD_TOP    = 0.23f;
    private static final float BOARD_BOTTOM = 0.42f;
    private static final float BOARD_LEFT   = 0.04f;
    private static final float BOARD_RIGHT  = 0.96f;
    private static final float PIECES_TOP   = 0.73f;
    private static final float PIECES_BOTTOM = 0.80f;

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

        int boardTop    = (int)(h * BOARD_TOP);
        int boardBottom = (int)(h * BOARD_BOTTOM);
        int boardLeft   = (int)(w * BOARD_LEFT);
        int boardRight  = (int)(w * BOARD_RIGHT);
        int boardW = boardRight - boardLeft;
        int boardH = boardBottom - boardTop;
        int cellW = boardW / GRID_SIZE;
        int cellH = boardH / GRID_SIZE;

        float bgBrightness = getBrightness(screenshot, boardLeft + 2, boardTop + 2);

        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int cx = boardLeft + col * cellW + cellW / 2;
                int cy = boardTop + row * cellH + cellH / 2;
                float brightness = getBrightness(screenshot, cx, cy);
                result.board[row][col] = brightness > bgBrightness + 25;
            }
        }

        int piecesTop    = (int)(h * PIECES_TOP);
        int piecesBottom = (int)(h * PIECES_BOTTOM);
        int piecesLeft   = (int)(w * 0.04f);
        int piecesRight  = (int)(w * 0.96f);
        int pw = piecesRight - piecesLeft;
        int ph = piecesBottom - piecesTop;
        int slotW = pw / 3;

        for (int i = 0; i < 3; i++) {
            int x1 = piecesLeft + i * slotW;
            int x2 = x1 + slotW;
            int[][] cells = detectPieceShape(screenshot, x1, piecesTop, x2, piecesBottom);
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
        int piecesTop  = (int)(screenH * PIECES_TOP);
        int piecesLeft = (int)(screenW * 0.04f);
        int pw = (int)(screenW * 0.92f);
        int ph = (int)(screenH * (PIECES_BOTTOM - PIECES_TOP));
        int slotW = pw / 3;
        float cx = piecesLeft + pieceIdx * slotW + slotW / 2f;
        float cy = piecesTop + ph / 2f;
        return new float[]{cx, cy};
    }

    public static float[] getBoardCellCenter(int screenW, int screenH, int row, int col) {
        int boardTop  = (int)(screenH * BOARD_TOP);
        int boardLeft = (int)(screenW * BOARD_LEFT);
        int boardW = (int)(screenW * (BOARD_RIGHT - BOARD_LEFT));
        int boardH = (int)(screenH * (BOARD_BOTTOM - BOARD_TOP));
        int cellW = boardW / GRID_SIZE;
        int cellH = boardH / GRID_SIZE;
        float cx = boardLeft + col * cellW + cellW / 2f;
        float cy = boardTop + row * cellH + cellH / 2f;
        return new float[]{cx, cy};
    }
}
