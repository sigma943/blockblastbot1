package com.blockblastbot;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus, tvMoves;
    private Button btnToggle, btnAccessibility;
    private boolean botRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvMoves = findViewById(R.id.tvMoves);
        btnToggle = findViewById(R.id.btnToggle);
        btnAccessibility = findViewById(R.id.btnAccessibility);

        btnAccessibility.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        btnToggle.setOnClickListener(v -> {
            if (!isAccessibilityEnabled()) {
                tvStatus.setText("❌ Najpierw włącz dostępność!");
                return;
            }
            botRunning = !botRunning;
            BlockBlastService.setBotActive(botRunning);

            if (botRunning) {
                tvStatus.setText("Bot aktywny 🟢");
                btnToggle.setText("⏹ STOP BOT");
                btnToggle.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFFEF4444));
            } else {
                tvStatus.setText("Bot wyłączony");
                btnToggle.setText("▶ START BOT");
                btnToggle.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF10B981));
            }
        });

        tvMoves.postDelayed(new Runnable() {
            @Override
            public void run() {
                tvMoves.setText("Ruchy: " + BlockBlastService.getMoveCount());
                tvMoves.postDelayed(this, 500);
            }
        }, 500);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isAccessibilityEnabled()) {
            btnAccessibility.setText("✅ Dostępność włączona");
            btnAccessibility.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF065F46));
        } else {
            btnAccessibility.setText("⚙ Włącz dostępność");
            btnAccessibility.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF374151));
        }
    }

    private boolean isAccessibilityEnabled() {
        AccessibilityManager am = (AccessibilityManager)
            getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<android.accessibilityservice.AccessibilityServiceInfo> services =
            am.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (android.accessibilityservice.AccessibilityServiceInfo service : services) {
            if (service.getId().contains("com.blockblastbot")) {
                return true;
            }
        }
        return false;
    }
}
