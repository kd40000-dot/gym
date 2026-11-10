package com.kd40000.gym;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Single-purpose, offline QR display. No account data leaves this phone. */
public final class MainActivity extends Activity {
    // Official Dracula base palette, with an accessible softened-foreground text tone.
    private static final int BG = Color.rgb(40, 42, 54);          // #282A36
    private static final int PANEL = Color.rgb(68, 71, 90);      // #44475A
    private static final int FOREGROUND = Color.rgb(248, 248, 242); // #F8F8F2
    private static final int PURPLE = Color.rgb(189, 147, 249);  // #BD93F9
    private static final int PINK = Color.rgb(255, 121, 198);    // #FF79C6
    private static final int CYAN = Color.rgb(139, 233, 253);    // #8BE9FD
    private static final int ORANGE = Color.rgb(255, 184, 108);  // #FFB86C
    private static final int SECONDARY = Color.rgb(209, 211, 224);
    private static final int ACCENT = PURPLE;
    private static final long ROTATION_MS = 10_000L;
    private static final DateTimeFormatter UTC_CLOCK =
        DateTimeFormatter.ofPattern("HH:mm:ss 'UTC'", Locale.ROOT).withZone(ZoneOffset.UTC);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (!showingCode) return;
            if (SystemClock.elapsedRealtime() >= nextRefreshElapsed) refreshCode();
            updateCountdown();
            handler.postDelayed(this, 250);
        }
    };

    private SecureStore store;
    private MemberConfig config;
    private boolean showingCode;
    private boolean restoreFailed;
    private long nextRefreshElapsed;
    private ImageView qrView;
    private TextView countdownText;
    private TextView generatedText;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        store = new SecureStore(this);
        try {
            config = store.load();
        } catch (Exception e) {
            restoreFailed = true;
            config = null;
        }
        if (config != null && config.isValid()) showCodeScreen();
        else showSettingsScreen();
    }

    @Override protected void onResume() {
        super.onResume();
        if (showingCode) {
            refreshCode();
            handler.removeCallbacks(clockTick);
            handler.post(clockTick);
        }
    }

    @Override protected void onPause() {
        handler.removeCallbacks(clockTick);
        WindowManager.LayoutParams window = getWindow().getAttributes();
        window.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(window);
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(clockTick);
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (!showingCode && config != null && config.isValid()) {
            showCodeScreen();
        } else {
            super.onBackPressed();
        }
    }

    private void showCodeScreen() {
        showingCode = true;
        handler.removeCallbacks(clockTick);

        WindowManager.LayoutParams window = getWindow().getAttributes();
        window.screenBrightness = 1.0f;
        getWindow().setAttributes(window);

        LinearLayout page = vertical();
        page.setBackgroundColor(BG);
        applySystemBarPadding(page, 14, 14, 14, 14);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text("SYNERGYM", 22, PINK, true);
        heading.setLetterSpacing(0.08f);
        toolbar.addView(heading, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button settings = button("Settings", false);
        settings.setContentDescription("Edit membership settings");
        settings.setOnClickListener(v -> showSettingsScreen());
        toolbar.addView(settings);
        page.addView(toolbar);

        TextView caption = text("MEMBERSHIP ENTRY", 12, CYAN, true);
        caption.setLetterSpacing(0.16f);
        caption.setPadding(0, dp(12), 0, 0);
        page.addView(caption);

        FrameLayout center = new FrameLayout(this);
        LinearLayout.LayoutParams centerLayout = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        page.addView(center, centerLayout);

        qrView = new ImageView(this);
        qrView.setContentDescription("Current, automatically refreshing gym QR code");
        qrView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qrView.setBackgroundColor(FOREGROUND);
        qrView.setPadding(dp(6), dp(6), dp(6), dp(6));

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int square = Math.max(dp(160), Math.min(screenWidth - dp(28),
            screenHeight - dp(215)));
        FrameLayout.LayoutParams qrLayout = new FrameLayout.LayoutParams(square, square,
            Gravity.CENTER);
        center.addView(qrView, qrLayout);

        countdownText = text("Refreshing...", 20, FOREGROUND, true);
        countdownText.setGravity(Gravity.CENTER);
        page.addView(countdownText);

        generatedText = text("", 13, PURPLE, false);
        generatedText.setGravity(Gravity.CENTER);
        generatedText.setPadding(0, dp(7), 0, dp(8));
        page.addView(generatedText);

        TextView help = text("Offline  •  Keep this screen open at the scanner",
            12, SECONDARY, false);
        help.setGravity(Gravity.CENTER);
        help.setPadding(0, 0, 0, dp(8));
        page.addView(help);

        setContentView(page);
        refreshCode();
        handler.post(clockTick);
    }

    private void refreshCode() {
        if (!showingCode || config == null || !config.isValid()) return;
        try {
            Instant time = Instant.now();
            String signed = QrPayload.generate(config, time);
            int imageSize = Math.min(1100, Math.max(512,
                getResources().getDisplayMetrics().widthPixels));
            Bitmap image = QrCodeRenderer.render(signed, imageSize);
            qrView.setImageBitmap(image);
            generatedText.setText("Generated " + UTC_CLOCK.format(time));
            nextRefreshElapsed = SystemClock.elapsedRealtime() + ROTATION_MS;
            updateCountdown();
        } catch (Exception e) {
            qrView.setImageDrawable(null);
            generatedText.setText("QR generation failed. Check membership settings.");
            nextRefreshElapsed = SystemClock.elapsedRealtime() + ROTATION_MS;
        }
    }

    private void updateCountdown() {
        if (countdownText == null) return;
        long leftMs = Math.max(0, nextRefreshElapsed - SystemClock.elapsedRealtime());
        long seconds = (leftMs + 999) / 1000;
        countdownText.setText("Next code in " + seconds + "s");
    }

    private void showSettingsScreen() {
        showingCode = false;
        handler.removeCallbacks(clockTick);
        WindowManager.LayoutParams window = getWindow().getAttributes();
        window.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(window);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout page = vertical();
        applySystemBarPadding(page, 24, 28, 24, 30);
        scroll.addView(page);

        TextView title = text("Gym QR", 30, PINK, true);
        page.addView(title);
        TextView subtitle = text("One-time membership setup", 16, CYAN, false);
        subtitle.setPadding(0, dp(6), 0, dp(16));
        page.addView(subtitle);

        if (restoreFailed) {
            TextView warning = text("The saved signing key could not be unlocked. Re-enter your setup details.",
                14, ORANGE, false);
            warning.setPadding(0, 0, 0, dp(14));
            page.addView(warning);
        }

        TextView explanation = text(
            "Enter the values from your existing working Tasker/Termux QR setup. "
            + "The signing key is encrypted locally with Android Keystore. "
            + "Nothing is sent to any server.", 14, SECONDARY, false);
        explanation.setPadding(0, 0, 0, dp(20));
        page.addView(explanation);

        EditText member = input(page, "Member ID",
            config == null ? "" : config.memberId, InputType.TYPE_CLASS_NUMBER);
        EditText superclub = input(page, "Superclub ID",
            config == null ? "0" : config.superclubId, InputType.TYPE_CLASS_NUMBER);
        EditText club = input(page, "Club ID",
            config == null ? "" : config.clubId, InputType.TYPE_CLASS_NUMBER);
        EditText tagx = input(page, "Tag (16 hexadecimal characters)",
            config == null ? "" : config.tagx,
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        EditText key = input(page, "HMAC signing key (literal text)",
            config == null ? "" : config.signingKey,
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        Button save = button("Save and show QR", true);
        LinearLayout.LayoutParams saveLayout = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        saveLayout.topMargin = dp(18);
        page.addView(save, saveLayout);
        save.setOnClickListener(v -> {
            MemberConfig entered = new MemberConfig(
                member.getText().toString(), superclub.getText().toString(),
                club.getText().toString(), tagx.getText().toString(),
                key.getText().toString());
            if (!entered.isValid()) {
                toast("Check the numeric IDs, 16-digit hex tag, and signing key.");
                return;
            }
            try {
                store.save(entered);
                config = entered;
                restoreFailed = false;
                showCodeScreen();
            } catch (Exception e) {
                toast("Unable to save encrypted settings: " + e.getClass().getSimpleName());
            }
        });

        TextView note = text(
            "Codes use UTC time. Keep your phone's automatic date and time enabled. "
            + "A generated QR code does not guarantee that the gym will accept it.",
            12, SECONDARY, false);
        note.setPadding(0, dp(24), 0, dp(10));
        page.addView(note);

        if (config != null && config.isValid()) {
            Button cancel = button("Back to QR", false);
            page.addView(cancel);
            cancel.setOnClickListener(v -> showCodeScreen());
        }
        setContentView(scroll);
    }

    private EditText input(LinearLayout page, String label, String value, int type) {
        TextView name = text(label, 13, CYAN, true);
        name.setPadding(0, dp(12), 0, dp(4));
        page.addView(name);

        EditText field = new EditText(this);
        field.setInputType(type);
        field.setSingleLine(true);
        field.setTextColor(FOREGROUND);
        field.setHintTextColor(SECONDARY);
        field.setSelectAllOnFocus(true);
        field.setTextSize(17);
        field.setPadding(dp(12), dp(8), dp(12), dp(8));
        field.setBackground(rounded(PANEL, dp(10)));
        field.setText(value);
        field.setContentDescription(label);
        page.addView(field, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        return field;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sizeSp);
        text.setTextColor(color);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private Button button(String value, boolean primary) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(value);
        button.setTextSize(14);
        button.setTextColor(primary ? BG : FOREGROUND);
        button.setBackground(rounded(primary ? ACCENT : PANEL, dp(10)));
        button.setPadding(dp(15), dp(8), dp(15), dp(8));
        return button;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        return shape;
    }

    private void applySystemBarPadding(View view, int left, int top, int right, int bottom) {
        view.setPadding(dp(left), dp(top), dp(right), dp(bottom));
        if (Build.VERSION.SDK_INT >= 30) {
            view.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(dp(left) + bars.left, dp(top) + bars.top,
                    dp(right) + bars.right, dp(bottom) + bars.bottom);
                return insets;
            });
        }
    }

    private int dp(int dimension) {
        return Math.round(dimension * getResources().getDisplayMetrics().density);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }
}
