package com.triai.browser;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
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

import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

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
    private final GeckoView[] attachedViews = new GeckoView[3];
    private final GeckoView[] overviewViews = new GeckoView[3];
    private final TextView[] overviewHeaders = new TextView[3];
    private final Button[] launcherItems = new Button[3];
    private final boolean[] canGoBack = new boolean[3];

    private int activeTab = 0;
    private int overviewFocusedTab = 0;
    private boolean testMode = false;
    private boolean overviewMode = false;

    private FrameLayout launcherOverlay;
    private Button launcherButton;
    private Button launcherOverview;
    private ViewPager2 pager;
    private LinearLayout overviewContainer;
    private BrowserPagerAdapter pagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        testMode = getIntent() != null && getIntent().getBooleanExtra(EXTRA_TEST_MODE, false);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        activeTab = clampTab(prefs.getInt(PREF_ACTIVE_TAB, 0));
        overviewFocusedTab = activeTab;

        if (runtime == null) {
            GeckoRuntimeSettings runtimeSettings = new GeckoRuntimeSettings.Builder()
                    .remoteDebuggingEnabled(false)
                    .loginAutofillEnabled(true)
                    .build();
            runtime = GeckoRuntime.create(this, runtimeSettings);
        }

        createBrowserSessions();
        setContentView(buildUi());
        enterImmersiveFullscreen();
        pager.setCurrentItem(activeTab, false);
        updateActivePage(activeTab);
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

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        pager = new ViewPager2(this);
        pager.setId(R.id.ai_pager);
        pager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        pager.setUserInputEnabled(true);
        pager.setOffscreenPageLimit(2);
        pagerAdapter = new BrowserPagerAdapter();
        pager.setAdapter(pagerAdapter);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (!overviewMode) updateActivePage(position);
            }
        });
        root.addView(pager, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        overviewContainer = buildOverview();
        overviewContainer.setVisibility(View.GONE);
        root.addView(overviewContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

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

    private LinearLayout buildOverview() {
        LinearLayout container = new LinearLayout(this);
        container.setId(R.id.overview_container);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(Color.BLACK);

        for (int i = 0; i < NAMES.length; i++) {
            final int index = i;

            LinearLayout pane = new LinearLayout(this);
            pane.setOrientation(LinearLayout.VERTICAL);
            pane.setBackgroundColor(Color.WHITE);

            TextView header = new TextView(this);
            header.setText(NAMES[i]);
            header.setTextSize(12);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(dp(12), 0, dp(8), 0);
            header.setTextColor(Color.BLACK);
            header.setBackgroundColor(0xFFF1F1F1);
            header.setOnClickListener(v -> focusOverviewPane(index));
            pane.addView(header, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(28)
            ));
            overviewHeaders[i] = header;

            GeckoView view = new GeckoView(this);
            view.setId(index == 0 ? R.id.overview_chatgpt
                    : index == 1 ? R.id.overview_gemini
                    : R.id.overview_claude);
            view.setAutofillEnabled(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                view.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_YES);
            }
            view.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    focusOverviewPane(index);
                }
                return false;
            });
            pane.addView(view, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
            ));
            overviewViews[i] = view;

            LinearLayout.LayoutParams paneParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
            );
            if (i > 0) paneParams.topMargin = dp(1);
            container.addView(pane, paneParams);
        }

        return container;
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

        launcherOverview = new Button(this);
        launcherOverview.setId(R.id.launcher_overview);
        launcherOverview.setText("3つをまとめて表示");
        launcherOverview.setTextSize(16);
        launcherOverview.setAllCaps(false);
        launcherOverview.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        launcherOverview.setPadding(dp(18), 0, dp(14), 0);
        launcherOverview.setOnClickListener(v -> {
            showOverview();
            hideLauncher();
        });
        panel.addView(launcherOverview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(56)
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
                showSinglePage(index, true);
                hideLauncher();
            });

            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(56)
            );
            itemParams.topMargin = dp(8);
            panel.addView(item, itemParams);
            launcherItems[i] = item;
        }

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(dp(288),
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
        refreshLauncherSelection();
    }

    private void hideLauncher() {
        launcherOverlay.setVisibility(View.GONE);
        launcherButton.setVisibility(View.VISIBLE);
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
            sessions[i] = session;
            session.loadUri(testMode ? "about:blank" : URLS[i]);
        }
    }

    private void showOverview() {
        if (overviewMode) return;

        overviewMode = true;
        overviewFocusedTab = activeTab;

        detachPagerSessions();
        pager.setVisibility(View.GONE);
        overviewContainer.setVisibility(View.VISIBLE);

        for (int i = 0; i < sessions.length; i++) {
            GeckoView view = overviewViews[i];
            if (view.getSession() != null) view.releaseSession();
            view.setSession(sessions[i]);
            sessions[i].setActive(true);
            sessions[i].setFocused(i == overviewFocusedTab);
        }

        updateOverviewHeaders();
        refreshLauncherSelection();
    }

    private void showSinglePage(int index, boolean animate) {
        index = clampTab(index);

        if (overviewMode) {
            for (GeckoView view : overviewViews) {
                if (view != null && view.getSession() != null) view.releaseSession();
            }
            overviewContainer.setVisibility(View.GONE);
            pager.setVisibility(View.VISIBLE);
            overviewMode = false;
            reattachPagerSessions();
        }

        pager.setCurrentItem(index, animate);
        updateActivePage(index);
    }

    private void detachPagerSessions() {
        for (int i = 0; i < attachedViews.length; i++) {
            GeckoView view = attachedViews[i];
            if (view != null && view.getSession() != null) {
                view.releaseSession();
            }
        }
    }

    private void reattachPagerSessions() {
        for (int i = 0; i < attachedViews.length; i++) {
            GeckoView view = attachedViews[i];
            if (view != null && view.getSession() == null) {
                view.setSession(sessions[i]);
            }
        }
        if (pagerAdapter != null) pagerAdapter.notifyDataSetChanged();
    }

    private void focusOverviewPane(int index) {
        if (!overviewMode) return;
        overviewFocusedTab = clampTab(index);
        activeTab = overviewFocusedTab;

        for (int i = 0; i < sessions.length; i++) {
            sessions[i].setActive(true);
            sessions[i].setFocused(i == overviewFocusedTab);
        }

        persistActiveTab();
        updateOverviewHeaders();
    }

    private void updateOverviewHeaders() {
        for (int i = 0; i < overviewHeaders.length; i++) {
            TextView header = overviewHeaders[i];
            if (header == null) continue;
            boolean focused = overviewMode && i == overviewFocusedTab;
            header.setText((focused ? "●  " : "") + NAMES[i]);
            header.setTextColor(focused ? Color.WHITE : Color.BLACK);
            header.setBackgroundColor(focused ? 0xFF171717 : 0xFFF1F1F1);
        }
    }

    private void updateActivePage(int index) {
        activeTab = clampTab(index);

        if (!overviewMode) {
            for (int i = 0; i < sessions.length; i++) {
                boolean selected = i == activeTab;
                if (sessions[i] != null) {
                    sessions[i].setActive(selected);
                    sessions[i].setFocused(selected);
                }
            }
        }

        persistActiveTab();
        refreshLauncherSelection();
    }

    private void persistActiveTab() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putInt(PREF_ACTIVE_TAB, activeTab)
                .apply();
    }

    private void refreshLauncherSelection() {
        if (launcherOverview != null) {
            launcherOverview.setSelected(overviewMode);
            launcherOverview.setText((overviewMode ? "✓  " : "    ") + "3つをまとめて表示");
            launcherOverview.setTextColor(overviewMode ? Color.WHITE : Color.BLACK);
            launcherOverview.setBackground(roundRect(
                    overviewMode ? 0xFF171717 : 0xFFF0F0F0,
                    dp(16)
            ));
        }

        for (int i = 0; i < launcherItems.length; i++) {
            Button item = launcherItems[i];
            if (item == null) continue;
            boolean selected = !overviewMode && i == activeTab;
            item.setSelected(selected);
            item.setText((selected ? "✓  " : "    ") + NAMES[i]);
            item.setTextColor(selected ? Color.WHITE : Color.BLACK);
            item.setBackground(roundRect(
                    selected ? 0xFF171717 : 0xFFF0F0F0,
                    dp(16)
            ));
        }
    }

    private GeckoSession currentSession() {
        return sessions[activeTab];
    }

    @Override
    public void onBackPressed() {
        if (launcherOverlay != null && launcherOverlay.getVisibility() == View.VISIBLE) {
            hideLauncher();
        } else if (overviewMode) {
            showSinglePage(activeTab, false);
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
            for (GeckoView view : overviewViews) {
                if (view != null && view.getSession() != null) view.releaseSession();
            }
            for (GeckoView view : attachedViews) {
                if (view != null && view.getSession() != null) view.releaseSession();
            }
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

    private final class BrowserPagerAdapter extends RecyclerView.Adapter<BrowserViewHolder> {

        BrowserPagerAdapter() {
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public BrowserViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            GeckoView view = new GeckoView(parent.getContext());
            view.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            view.setAutofillEnabled(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                view.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_YES);
            }
            return new BrowserViewHolder(view);
        }

        @Override
        public void onBindViewHolder(BrowserViewHolder holder, int position) {
            GeckoView view = holder.geckoView;
            GeckoSession current = view.getSession();
            if (current != null && current != sessions[position]) {
                view.releaseSession();
            }
            if (!overviewMode && view.getSession() == null) {
                view.setSession(sessions[position]);
            }
            attachedViews[position] = view;
        }

        @Override
        public void onViewRecycled(BrowserViewHolder holder) {
            GeckoView view = holder.geckoView;
            GeckoSession session = view.getSession();
            if (session != null) {
                for (int i = 0; i < attachedViews.length; i++) {
                    if (attachedViews[i] == view) attachedViews[i] = null;
                }
                view.releaseSession();
            }
            super.onViewRecycled(holder);
        }

        @Override
        public int getItemCount() {
            return sessions.length;
        }
    }

    private static final class BrowserViewHolder extends RecyclerView.ViewHolder {
        final GeckoView geckoView;

        BrowserViewHolder(GeckoView view) {
            super(view);
            geckoView = view;
        }
    }
}
