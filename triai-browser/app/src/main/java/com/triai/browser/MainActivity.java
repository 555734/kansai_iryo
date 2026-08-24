package com.triai.browser;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

public class MainActivity extends Activity {

    static final String EXTRA_TEST_MODE = "com.triai.browser.TEST_MODE";

    private static final String[] NAMES = {"ChatGPT", "Gemini", "Claude"};
    private static final String[] URLS = {
            "https://chatgpt.com/",
            "https://gemini.google.com/app",
            "https://claude.ai/new"
    };

    private static final String PREFS = "triai_prefs";
    private static final String PREF_ACTIVE_TAB = "active_tab";

    private static GeckoRuntime runtime;

    private final GeckoSession[] sessions = new GeckoSession[3];
    private final GeckoView[] webViews = new GeckoView[3];
    private final Button[] launcherItems = new Button[3];
    private final boolean[] canGoBack = new boolean[3];

    private int activeTab = 0;
    private boolean testMode = false;
    private FrameLayout launcherOverlay;
    private Button launcherButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        testMode = getIntent() != null && getIntent().getBooleanExtra(EXTRA_TEST_MODE, false);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        activeTab = clampTab(prefs.getInt(PREF_ACTIVE_TAB, 0));

        if (runtime == null) {
            GeckoRuntimeSettings runtimeSettings = new GeckoRuntimeSettings.Builder()
                    .remoteDebuggingEnabled(false)
                    .build();
            runtime = GeckoRuntime.create(this, runtimeSettings);
        }

        setContentView(buildUi());
        enterImmersiveFullscreen();
        createBrowserSessions();
        switchTo(activeTab);
    }

    private void enterImmersiveFullscreen() {
        Window window = getWindow();
        View decor = window.getDecorView();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = decor.getWindowInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        } else {
            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(params);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && launcherButton != null) {
            enterImmersiveFullscreen();
        }
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        FrameLayout browserStack = new FrameLayout(this);
        browserStack.setBackgroundColor(Color.WHITE);
        root.addView(browserStack, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        for (int i = 0; i < webViews.length; i++) {
            GeckoView view = new GeckoView(this);
            view.setId(View.generateViewId());
            view.setVisibility(View.GONE);
            view.setAutofillEnabled(true);
            browserStack.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            webViews[i] = view;
        }

        launcherOverlay = buildLauncherOverlay();
        launcherOverlay.setVisibility(View.GONE);
        root.addView(launcherOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        launcherButton = new Button(this);
        launcherButton.setId(R.id.launcher_button);
        launcherButton.setText("AI");
        launcherButton.setTextColor(Color.WHITE);
        launcherButton.setTextSize(13);
        launcherButton.setAllCaps(false);
        launcherButton.setPadding(0, 0, 0, 0);
        launcherButton.setAlpha(0.82f);
        launcherButton.setBackground(roundRect(0xE8171717, dp(24)));
        launcherButton.setOnClickListener(v -> showLauncher());

        FrameLayout.LayoutParams launcherParams = new FrameLayout.LayoutParams(dp(46), dp(46));
        launcherParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        launcherParams.setMarginEnd(dp(8));
        root.addView(launcherButton, launcherParams);

        return root;
    }

    private FrameLayout buildLauncherOverlay() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setId(R.id.launcher_overlay);
        overlay.setBackgroundColor(0x7A000000);
        overlay.setClickable(true);
        overlay.setOnClickListener(v -> hideLauncher());

        LinearLayout panel = new LinearLayout(this);
        panel.setId(R.id.launcher_panel);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(18));
        panel.setBackground(roundRect(0xF5FFFFFF, dp(22)));
        panel.setClickable(true);
        panel.setOnClickListener(v -> { });

        TextView title = new TextView(this);
        title.setText("AI Launcher");
        title.setTextColor(Color.BLACK);
        title.setTextSize(14);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(10));
        panel.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        for (int i = 0; i < NAMES.length; i++) {
            final int index = i;
            Button item = new Button(this);
            item.setId(index == 0 ? R.id.launcher_chatgpt
                    : index == 1 ? R.id.launcher_gemini
                    : R.id.launcher_claude);
            item.setText(NAMES[i]);
            item.setTextSize(16);
            item.setAllCaps(false);
            item.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            item.setPadding(dp(18), 0, dp(14), 0);
            item.setOnClickListener(v -> {
                switchTo(index);
                hideLauncher();
            });

            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(56)
            );
            if (i > 0) itemParams.topMargin = dp(8);
            panel.addView(item, itemParams);
            launcherItems[i] = item;
        }

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(dp(276),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        panelParams.gravity = Gravity.CENTER;
        overlay.addView(panel, panelParams);

        return overlay;
    }

    private GradientDrawable roundRect(int color, int radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    private void showLauncher() {
        launcherOverlay.setVisibility(View.VISIBLE);
        launcherButton.setVisibility(View.GONE);
    }

    private void hideLauncher() {
        launcherOverlay.setVisibility(View.GONE);
        launcherButton.setVisibility(View.VISIBLE);
        enterImmersiveFullscreen();
    }

    private void createBrowserSessions() {
        for (int i = 0; i < sessions.length; i++) {
            final int index = i;
            GeckoSession session = new GeckoSession();
            session.setContentDelegate(new GeckoSession.ContentDelegate() {});
            session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
                @Override
                public void onCanGoBack(GeckoSession session, boolean value) {
                    canGoBack[index] = value;
                }
            });

            session.open(runtime);
            webViews[i].setSession(session);
            sessions[i] = session;
            session.loadUri(testMode ? "about:blank" : URLS[i]);
        }
    }

    private void switchTo(int index) {
        activeTab = clampTab(index);

        for (int i = 0; i < sessions.length; i++) {
            boolean selected = i == activeTab;
            webViews[i].setVisibility(selected ? View.VISIBLE : View.GONE);
            if (sessions[i] != null) {
                sessions[i].setActive(selected);
                sessions[i].setFocused(selected);
            }
            if (launcherItems[i] != null) {
                launcherItems[i].setSelected(selected);
                launcherItems[i].setText((selected ? "✓  " : "    ") + NAMES[i]);
                launcherItems[i].setTextColor(selected ? Color.WHITE : Color.BLACK);
                launcherItems[i].setBackground(roundRect(
                        selected ? 0xFF171717 : 0xFFF0F0F0,
                        dp(16)
                ));
            }
        }

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putInt(PREF_ACTIVE_TAB, activeTab)
                .apply();
    }

    private GeckoSession currentSession() {
        return sessions[activeTab];
    }

    @Override
    public void onBackPressed() {
        if (launcherOverlay != null && launcherOverlay.getVisibility() == View.VISIBLE) {
            hideLauncher();
        } else if (canGoBack[activeTab]) {
            currentSession().goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            for (GeckoSession session : sessions) {
                if (session != null && session.isOpen()) session.close();
            }
        }
    }

    private int clampTab(int index) {
        return Math.max(0, Math.min(index, NAMES.length - 1));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
