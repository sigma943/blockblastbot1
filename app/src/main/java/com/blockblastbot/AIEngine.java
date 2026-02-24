package com.blockblastbot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AIEngine {

    public static final int GRID_SIZE = 8;

    public static class Shape {
        public int[][] cells;
        public int idx;
        public Shape(int[][] cells, int idx) {
            this.cells = cells;
            this.idx = idx;
        }
    }

    public static class GameState {
        public boolean[][] grid;
        public List<Shape> shapes;
        public int score;
        public int combo;

        public GameState(boolean[][] grid, List<Shape> shapes) {
            this.grid = copyGrid(grid);
            this.shapes = shapes;
            this.score = 0;
            this.combo = 0;
        }
    }

    public static class Move {
        public int shapeIdx;
        public int row, col;
        public Move(int shapeIdx, int row, int col) {
            this.shapeIdx = shapeIdx;
            this.row = row;
            this.col = col;
        }
    }

    public static boolean[][] copyGrid(boolean[][] grid) {
        boolean[][] copy = new boolean[GRID_SIZE][GRID_SIZE];
        for (int i = 0; i < GRID_SIZE; i++) {
            copy[i] = Arrays.copyOf(grid[i], GRID_SIZE);
        }
        return copy;
    }

    public static boolean canPlace(boolean[][] grid, Shape shape, int row, int col) {
        for (int[] cell : shape.cells) {
            int r = row + cell[0];
            int c = col + cell[1];
            if (r < 0 || r >= GRID_SIZE || c < 0 || c >= GRID_SIZE) return false;
            if (grid[r][c]) return false;
        }
        return true;
    }

    public static int clearLines(boolean[][] grid) {
        int cleared = 0;
        for (int r = 0; r < GRID_SIZE; r++) {
            boolean full = true;
            for (int c = 0; c < GRID_SIZE; c++) {
                if (!grid[r][c]) { full = false; break; }
            }
            if (full) { Arrays.fill(grid[r], false); cleared++; }
        }
        for (int c = 0; c < GRID_SIZE; c++) {
            boolean full = true;
            for (int r = 0; r < GRID_SIZE; r++) {
                if (!grid[r][c]) { full = false; break; }
            }
            if (full) {
                for (int r = 0; r < GRID_SIZE; r++) grid[r][c] = false;
                cleared++;
            }
        }
        return cleared;
    }

    private static int countSafeMoves(boolean[][] grid, List<Shape> shapes) {
        int safeMoves = 0;
        for (Shape shape : shapes) {
            for (int r = 0; r < GRID_SIZE; r++) {
                for (int c = 0; c < GRID_SIZE; c++) {
                    if (!canPlace(grid, shape, r, c)) continue;
                    boolean[][] tempGrid = copyGrid(grid);
                    for (int[] cell : shape.cells) {
                        tempGrid[r + cell[0]][c + cell[1]] = true;
                    }
                    boolean othersCanPlace = true;
                    for (Shape other : shapes) {
                        if (other == shape) continue;
                        boolean found = false;
                        outer:
                        for (int r2 = 0; r2 < GRID_SIZE; r2++) {
                            for (int c2 = 0; c2 < GRID_SIZE; c2++) {
                                if (canPlace(tempGrid, other, r2, c2)) {
                                    found = true;
                                    break outer;
                                }
                            }
                        }
                        if (!found) { othersCanPlace = false; break; }
                    }
                    if (othersCanPlace) safeMoves++;
                }
            }
        }
        return safeMoves;
    }

    private static int dfs(boolean[][] grid, boolean[][] visited, int r, int c) {
        if (r < 0 || r >= GRID_SIZE || c < 0 || c >= GRID_SIZE) return 0;
        if (visited[r][c] || grid[r][c]) return 0;
        visited[r][c] = true;
        return 1 + dfs(grid, visited, r-1, c) + dfs(grid, visited, r+1, c)
                 + dfs(grid, visited, r, c-1) + dfs(grid, visited, r, c+1);
    }

    private static int detectPattern(boolean[][] grid) {
        int score = 0;
        boolean[][] visited = new boolean[GRID_SIZE][GRID_SIZE];
        int emptyRegions = 0;
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                if (!visited[r][c] && !grid[r][c]) {
                    int regionSize = dfs(grid, visited, r, c);
                    emptyRegions++;
                    if (regionSize == 1) score -= 4000;
                    else if (regionSize == 2) score -= 3000;
                    else if (regionSize == 3) score -= 2000;
                    else if (regionSize < 6) score -= 1000;
                    else if (regionSize >= 20) score += 300;
                    else if (regionSize >= 12) score += 150;
                    else if (regionSize >= 8) score += 80;
                }
            }
        }
        if (emptyRegions > 5) score -= 10000;
        else if (emptyRegions > 4) score -= 5000;
        else if (emptyRegions > 3) score -= 2000;
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                if (!grid[r][c]) {
                    int blocked = 0;
                    if (r == 0 || grid[r-1][c]) blocked++;
                    if (r == GRID_SIZE-1 || grid[r+1][c]) blocked++;
                    if (c == 0 || grid[r][c-1]) blocked++;
                    if (c == GRID_SIZE-1 || grid[r][c+1]) blocked++;
                    if (blocked >= 4) score -= 6000;
                    else if (blocked >= 3) score -= 3000;
                }
            }
        }
        for (int r = 6; r < 8; r++) {
            boolean empty = true;
            for (int c = 0; c < GRID_SIZE; c++) {
                if (grid[r][c]) { empty = false; break; }
            }
            if (empty) score += 600;
        }
        for (int i = 0; i < GRID_SIZE; i++) {
            if (!grid[0][i]) score += 100;
            if (!grid[GRID_SIZE-1][i]) score += 100;
            if (!grid[i][0]) score += 100;
            if (!grid[i][GRID_SIZE-1]) score += 100;
        }
        for (int r = 0; r < GRID_SIZE; r++) {
            int filled = 0;
            for (int c = 0; c < GRID_SIZE; c++) if (grid[r][c]) filled++;
            if (filled == 7) score += 1200;
            else if (filled == 6) score += 500;
        }
        for (int c = 0; c < GRID_SIZE; c++) {
            int filled = 0;
            for (int r = 0; r < GRID_SIZE; r++) if (grid[r][c]) filled++;
            if (filled == 7) score += 1200;
            else if (filled == 6) score += 500;
        }
        return score;
    }

    public static double evaluateImmortal(boolean[][] grid, List<Shape> shapes, int combo) {
        double value = 0;
        int mobility = countSafeMoves(grid, shapes);
        if (mobility == 0) return -100000;
        value += mobility * 100;
        int filled = 0;
        for (boolean[] row : grid) for (boolean cell : row) if (cell) filled++;
        double fillPercent = (filled / 64.0) * 100;
        if (fillPercent > 70) value -= 20000;
        else if (fillPercent > 60) value -= 10000;
        else if (fillPercent > 50) value -= 5000;
        else if (fillPercent > 40) value -= 2000;
        else if (fillPercent > 30) value -= 800;
        else if (fillPercent > 20) value -= 200;
        value += (64 - filled) * 60;
        value += detectPattern(grid) * 2;
        int almostFull = 0;
        for (int r = 0; r < GRID_SIZE; r++) {
            int f = 0;
            for (int c = 0; c < GRID_SIZE; c++) if (grid[r][c]) f++;
            if (f == 7) { value += 1500; almostFull++; }
            else if (f == 6) value += 600;
            else if (f == 5) value += 200;
        }
        for (int c = 0; c < GRID_SIZE; c++) {
            int f = 0;
            for (int r = 0; r < GRID_SIZE; r++) if (grid[r][c]) f++;
            if (f == 7) { value += 1500; almostFull++; }
            else if (f == 6) value += 600;
            else if (f == 5) value += 200;
        }
        if (almostFull >= 2) value += almostFull * 1000;
        value += combo * 200;
        return value;
    }

    public static Move getBestMove(boolean[][] grid, List<Shape> shapes) {
        if (shapes.isEmpty()) return null;
        int beamWidth = 30;
        List<Object[]> beam = new ArrayList<>();
        for (int i = 0; i < shapes.size(); i++) {
            Shape shape = shapes.get(i);
            for (int r = 0; r < GRID_SIZE; r++) {
                for (int c = 0; c < GRID_SIZE; c++) {
                    if (!canPlace(grid, shape, r, c)) continue;
                    boolean[][] newGrid = copyGrid(grid);
                    for (int[] cell : shape.cells) {
                        newGrid[r + cell[0]][c + cell[1]] = true;
                    }
                    int lines = clearLines(newGrid);
                    List<Shape> remaining = new ArrayList<>(shapes);
                    remaining.remove(i);
                    double score = evaluateImmortal(newGrid, remaining, lines > 0 ? 1 : 0);
                    beam.add(new Object[]{newGrid, remaining, new Move(shape.idx, r, c), score, lines > 0 ? 1 : 0});
                }
            }
        }
        if (beam.isEmpty()) return null;
        beam.sort((a, b) -> Double.compare((double)b[3], (double)a[3]));
        if (beam.size() > beamWidth) beam = beam.subList(0, beamWidth);
        for (int depth = 1; depth < shapes.size(); depth++) {
            List<Object[]> newBeam = new ArrayList<>();
            for (Object[] state : beam) {
                boolean[][] sg = (boolean[][]) state[0];
                @SuppressWarnings("unchecked")
                List<Shape> rem = (List<Shape>) state[1];
                Move firstMove = (Move) state[2];
                int combo = (int) state[4];
                if (rem.isEmpty()) { newBeam.add(state); continue; }
                for (int i = 0; i < rem.size(); i++) {
                    Shape shape = rem.get(i);
                    for (int r = 0; r < GRID_SIZE; r++) {
                        for (int c = 0; c < GRID_SIZE; c++) {
                            if (!canPlace(sg, shape, r, c)) continue;
                            boolean[][] newGrid = copyGrid(sg);
                            for (int[] cell : shape.cells) {
                                newGrid[r + cell[0]][c + cell[1]] = true;
                            }
                            int lines = clearLines(newGrid);
                            int newCombo = combo + (lines > 0 ? 1 : 0);
                            List<Shape> newRem = new ArrayList<>(rem);
                            newRem.remove(i);
                            double sc = evaluateImmortal(newGrid, newRem, newCombo);
                            newBeam.add(new Object[]{newGrid, newRem, firstMove, sc, newCombo});
                        }
                    }
                }
            }
            if (!newBeam.isEmpty()) {
                newBeam.sort((a, b) -> Double.compare((double)b[3], (double)a[3]));
                if (newBeam.size() > beamWidth) newBeam = newBeam.subList(0, beamWidth);
                beam = newBeam;
            }
        }
        if (beam.isEmpty()) return null;
        return (Move) beam.get(0)[2];
    }
}
