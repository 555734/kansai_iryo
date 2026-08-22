package com.triai.browser;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
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
    private final Button[] tabButtons = new Button[3];
    private final boolean[] canGoBack = new boolean[3];
    private final boolean[] canGoForward = new boolean[3];

    private int activeTab = 0;
    private boolean testMode = false;
    private TextView statusText;
    private Button backButton;
    private Button forwardButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        testMode = getIntent() != null && getIntent().getBooleanExtra(EXTRA_TEST_MODE, false);
        configureSystemBars();

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        activeTab = clampTab(prefs.getInt(PREF_ACTIVE_TAB, 0));

        if (runtime == null) {
            GeckoRuntimeSettings runtimeSettings = new GeckoRuntimeSettings.Builder()
                    .remoteDebuggingEnabled(false)
                    .build();
            runtime = GeckoRuntime.create(this, runtimeSettings);
        }

        View root = buildUi();
        setContentView(root);
        applySystemBarInsets(root);

        createBrowserSessions();
        switchTo(activeTab);
    }

    private void configureSystemBars() {
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);

        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    /**
     * Android 15+ draws targetSdk 35+ apps edge-to-edge. Keep our browser chrome
     * outside status/navigation bars and display cutouts explicitly.
     */
    private void applySystemBarInsets(View root) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                Insets insets = windowInsets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                );
                view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
                return WindowInsets.CONSUMED;
            });
            root.requestApplyInsets();
        } else {
            root.setFitsSystemWindows(true);
        }
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setPadding(dp(7), dp(6), dp(7), dp(4));

        for (int i = 0; i < NAMES.length; i++) {
            final int tabIndex = i;
            Button button = new Button(this);
            button.setText(NAMES[i]);
            button.setTextSize(13);
            button.setAllCaps(false);
            button.setSingleLine(true);
            button.setGravity(Gravity.CENTER);
            button.setPadding(dp(6), 0, dp(6), 0);
            button.setBackgroundResource(R.drawable.tab_background);
            button.setOnClickListener(v -> switchTo(tabIndex));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(40), 1f);
            params.setMargins(dp(3), 0, dp(3), 0);
            tabs.addView(button, params);
            tabButtons[i] = button;
        }
        root.addView(tabs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        statusText = new TextView(this);
        statusText.setTextSize(10);
        statusText.setTextColor(Color.DKGRAY);
        statusText.setSingleLine(true);
        statusText.setPadding(dp(12), 0, dp(12), dp(3));
        root.addView(statusText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        FrameLayout browserStack = new FrameLayout(this);
        browserStack.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams browserParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        root.addView(browserStack, browserParams);

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

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(7), dp(4), dp(7), dp(4));

        backButton = makeControlButton("←", v -> goBack());
        forwardButton = makeControlButton("→", v -> goForward());
        Button reloadButton = makeControlButton("↻", v -> currentSession().reload());
        Button homeButton = makeControlButton("⌂", v -> currentSession().loadUri(URLS[activeTab]));

        controls.addView(backButton, controlParams());
        controls.addView(forwardButton, controlParams());
        controls.addView(reloadButton, controlParams());
        controls.addView(homeButton, controlParams());

        root.addView(controls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        return root;
    }

    private Button makeControlButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(19);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setBackgroundResource(R.drawable.control_background);
        button.setOnClickListener(listener);
        return button;
    }

    private LinearLayout.LayoutParams controlParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(40), 1f);
        params.setMargins(dp(4), 0, dp(4), 0);
        return params;
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
                    if (index == activeTab) updateNavButtons();
                }

                @Override
                public void onCanGoForward(GeckoSession session, boolean value) {
                    canGoForward[index] = value;
                    if (index == activeTab) updateNavButtons();
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
            tabButtons[i].setSelected(selected);
            tabButtons[i].setTextColor(selected ? Color.WHITE : Color.BLACK);
        }

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putInt(PREF_ACTIVE_TAB, activeTab)
                .apply();

        statusText.setText(NAMES[activeTab] + "  •  open");
        updateNavButtons();
    }

    private void updateNavButtons() {
        backButton.setEnabled(canGoBack[activeTab]);
        forwardButton.setEnabled(canGoForward[activeTab]);
        backButton.setAlpha(canGoBack[activeTab] ? 1.0f : 0.35f);
        forwardButton.setAlpha(canGoForward[activeTab] ? 1.0f : 0.35f);
    }

    private void goBack() {
        if (canGoBack[activeTab]) currentSession().goBack();
    }

    private void goForward() {
        if (canGoForward[activeTab]) currentSession().goForward();
    }

    private GeckoSession currentSession() {
        return sessions[activeTab];
    }

    @Override
    public void onBackPressed() {
        if (canGoBack[activeTab]) {
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
