package com.blockblastbot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import java.io.FileOutputStream;
import java.util.List;

public class BlockBlastService extends AccessibilityService {

    private static final String TAG = "BlockBlastBot";
    private static volatile boolean botActive = false;
    private static volatile int moveCount = 0;
    private static BlockBlastService instance;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isBusy = false;
    private int debugCount = 0;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "Service connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!botActive || isBusy) return;
        if (event.getPackageName() != null &&
            event.getPackageName().toString().contains("block")) {
            scheduleNextMove(500);
        }
    }

    @Override public void onInterrupt() { botActive = false; }
    @Override public void onDestroy() { super.onDestroy(); botActive = false; instance = null; }

    private void scheduleNextMove(long delay) {
        if (!botActive || isBusy) return;
        handler.postDelayed(this::performMove, delay);
    }

    private void performMove() {
        if (!botActive || isBusy) return;
        isBusy = true;

        takeScreenshot(android.view.Display.DEFAULT_DISPLAY,
            getMainExecutor(),
            new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult screenshot) {
                    try {
                        Bitmap bmp = Bitmap.wrapHardwareBuffer(screenshot.getHardwareBuffer(), null);
                        if (bmp != null) {
                            Bitmap soft = bmp.copy(Bitmap.Config.ARGB_8888, false);
                            processScreenshot(soft);
                            soft.recycle();
                            bmp.recycle();
                        }
                        screenshot.getHardwareBuffer().close();
                    } catch (Exception e) { Log.e(TAG, "Error", e); }
                    isBusy = false;
                    if (botActive) scheduleNextMove(800);
                }

                @Override
                public void onFailure(int errorCode) {
                    Log.e(TAG, "Screenshot failed: " + errorCode);
                    isBusy = false;
                    if (botActive) scheduleNextMove(1500);
                }
            });
    }

    private void processScreenshot(Bitmap bmp) {
        if (bmp == null) return;
        int sw = bmp.getWidth();
        int sh = bmp.getHeight();

        BoardDetector.DetectionResult result = BoardDetector.detect(bmp);

        Log.d(TAG, "Detection: valid=" + result.valid +
            " pieces=" + result.pieces.size() +
            " screen=" + sw + "x" + sh);

        // Zapisz debug screenshot co 5 prob
        if (debugCount < 3) {
            saveDebugScreenshot(bmp, result, sw, sh);
            debugCount++;
        }

        if (!result.valid || result.pieces.isEmpty()) return;

        AIEngine.Move move = AIEngine.getBestMove(result.board, result.pieces);
        if (move == null) { Log.d(TAG, "No move found"); return; }

        float[] from = BoardDetector.getPieceCenter(sw, sh, move.shapeIdx);
        float[] to   = BoardDetector.getBoardCellCenter(sw, sh, move.row, move.col);

        Log.d(TAG, "Move: piece" + move.shapeIdx +
            " from(" + (int)from[0] + "," + (int)from[1] + ")" +
            " to[" + move.row + "," + move.col + "]" +
            " (" + (int)to[0] + "," + (int)to[1] + ")");

        executeDrag(from[0], from[1], to[0], to[1]);
        moveCount++;
    }

    private void saveDebugScreenshot(Bitmap bmp, BoardDetector.DetectionResult result, int sw, int sh) {
        try {
            Bitmap debug = bmp.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(debug);
            Paint paint = new Paint();

            // Narysuj siatke planszy
            paint.setColor(0xFF00FF00);
            paint.setStrokeWidth(3);
            paint.setStyle(Paint.Style.STROKE);
            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    float[] center = BoardDetector.getBoardCellCenter(sw, sh, row, col);
                    int cellW = sw / 10;
                    canvas.drawRect(center[0]-cellW/2, center[1]-cellW/2,
                                   center[0]+cellW/2, center[1]+cellW/2, paint);
                }
            }

            // Narysuj centra klockow
            paint.setColor(0xFFFF0000);
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 3; i++) {
                float[] center = BoardDetector.getPieceCenter(sw, sh, i);
                canvas.drawCircle(center[0], center[1], 20, paint);
            }

            String path = "/sdcard/debug_bot_" + debugCount + ".png";
            FileOutputStream out = new FileOutputStream(path);
            debug.compress(Bitmap.CompressFormat.PNG, 90, out);
            out.close();
            debug.recycle();
            Log.d(TAG, "Saved debug: " + path);
        } catch (Exception e) {
            Log.e(TAG, "Debug save failed", e);
        }
    }

        private void executeDrag(float x1, float y1, float x2, float y2) {
        Path pressPath = new Path();
        pressPath.moveTo(x1, y1);
        GestureDescription.StrokeDescription press =
            new GestureDescription.StrokeDescription(pressPath, 0, 400, true);
        Path dragPath = new Path();
        dragPath.moveTo(x1, y1);
        dragPath.lineTo(x2, y2);
        GestureDescription.StrokeDescription drag =
            press.continueStroke(dragPath, 0, 800, false);
        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(drag).build();
        dispatchGesture(gesture, null, null);
    }

    public static void setBotActive(boolean active) {
        botActive = active;
        if (active && instance != null) instance.scheduleNextMove(1000);
        if (!active) moveCount = 0;
    }

    public static int getMoveCount() { return moveCount; }
    public static boolean isBotActive() { return botActive; }
}
