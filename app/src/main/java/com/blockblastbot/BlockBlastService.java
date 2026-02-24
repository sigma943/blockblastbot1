package com.blockblastbot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import java.util.List;

public class BlockBlastService extends AccessibilityService {

    private static final String TAG = "BlockBlastBot";
    private static volatile boolean botActive = false;
    private static volatile int moveCount = 0;
    private static BlockBlastService instance;

    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isBusy = false;

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
            scheduleNextMove(300);
        }
    }

    @Override
    public void onInterrupt() {
        botActive = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        botActive = false;
        instance = null;
    }

    private void scheduleNextMove(long delayMs) {
        if (!botActive || isBusy) return;
        handler.postDelayed(this::performMove, delayMs);
    }

    private void performMove() {
        if (!botActive || isBusy) return;
        isBusy = true;

        takeScreenshot(DEFAULT_DISPLAY,
            getMainExecutor(),
            new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult screenshot) {
                    try {
                        Bitmap bmp = Bitmap.wrapHardwareBuffer(
                            screenshot.getHardwareBuffer(), null);
                        if (bmp != null) {
                            Bitmap softBmp = bmp.copy(Bitmap.Config.ARGB_8888, false);
                            processScreenshot(softBmp);
                            softBmp.recycle();
                            bmp.recycle();
                        }
                        screenshot.getHardwareBuffer().close();
                    } catch (Exception e) {
                        Log.e(TAG, "Screenshot error", e);
                    }
                    isBusy = false;
                    if (botActive) scheduleNextMove(500);
                }

                @Override
                public void onFailure(int errorCode) {
                    Log.e(TAG, "Screenshot failed: " + errorCode);
                    isBusy = false;
                    if (botActive) scheduleNextMove(1000);
                }
            });
    }

    private void processScreenshot(Bitmap bmp) {
        if (bmp == null) return;

        BoardDetector.DetectionResult result = BoardDetector.detect(bmp);
        if (!result.valid) return;

        List<AIEngine.Shape> pieces = result.pieces;
        if (pieces.isEmpty()) return;

        AIEngine.Move move = AIEngine.getBestMove(result.board, pieces);
        if (move == null) return;

        int screenW = bmp.getWidth();
        int screenH = bmp.getHeight();

        float[] from = BoardDetector.getPieceCenter(screenW, screenH, move.shapeIdx);
        float[] to   = BoardDetector.getBoardCellCenter(screenW, screenH, move.row, move.col);

        executeDrag(from[0], from[1], to[0], to[1]);
        moveCount++;
    }

    private void executeDrag(float x1, float y1, float x2, float y2) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);

        GestureDescription.StrokeDescription stroke =
            new GestureDescription.StrokeDescription(path, 0, 600);

        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(stroke)
            .build();

        dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                Log.d(TAG, "Gesture OK");
            }
            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                Log.d(TAG, "Gesture cancelled");
            }
        }, null);
    }

    public static void setBotActive(boolean active) {
        botActive = active;
        if (active && instance != null) {
            instance.scheduleNextMove(1000);
        }
        if (!active) moveCount = 0;
    }

    public static int getMoveCount() { return moveCount; }
    public static boolean isBotActive() { return botActive; }
}
