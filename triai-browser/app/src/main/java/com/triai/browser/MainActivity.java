package com.triai.browser;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    static final String EXTRA_TEST_MODE = "com.triai.browser.TEST_MODE";

    private static final String TAG = "TriAI";
    private static final String[] NAMES = {"ChatGPT", "Gemini", "Claude"};
    private static final String[] URLS = {
            "https://chatgpt.com/",
            "https://gemini.google.com/app",
            "https://claude.ai/new"
    };
    private static final int UNIFIED_PAGE = 3;
    private static final int PAGE_COUNT = 4;

    private static final String PREFS = "triai_prefs";
    private static final String PREF_ACTIVE_PAGE = "active_page";
    private static final String EXTENSION_LOCATION = "resource://android/assets/unified/";
    private static final String EXTENSION_ID = "triai-unified@local";
    private static final String NATIVE_APP = "triai";

    private static GeckoRuntime runtime;

    private final GeckoSession[] sessions = new GeckoSession[3];
    private final GeckoView[] browserViews = new GeckoView[3];
    private final boolean[] canGoBack = new boolean[3];
    private final ProviderSnapshot[] snapshots = new ProviderSnapshot[3];
    private final Button[] launcherItems = new Button[3];

    private boolean testMode;
    private int activePage;

    private ViewPager2 pager;
    private BrowserPagerAdapter pagerAdapter;
    private FrameLayout launcherOverlay;
    private Button launcherButton;
    private Button launcherUnified;
    private ScrollView unifiedPage;
    private LinearLayout unifiedContent;

    private final WebExtension.MessageDelegate extractorMessageDelegate =
            new WebExtension.MessageDelegate() {
                @Override
                public GeckoResult<Object> onMessage(
                        String nativeApp,
                        Object message,
                        WebExtension.MessageSender sender
                ) {
                    if (!NATIVE_APP.equals(nativeApp) || !(message instanceof JSONObject)) {
                        return null;
                    }
                    handleSnapshot((JSONObject) message);
                    return null;
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        testMode = getIntent() != null && getIntent().getBooleanExtra(EXTRA_TEST_MODE, false);
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        activePage = clampPage(prefs.getInt(PREF_ACTIVE_PAGE, 0));

        for (int i = 0; i < snapshots.length; i++) {
            snapshots[i] = new ProviderSnapshot(NAMES[i]);
        }

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
        pager.setCurrentItem(activePage, false);
        updateActivePage(activePage);

        if (!testMode) {
            installExtractorExtension();
        }
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

    private void installExtractorExtension() {
        runtime.getWebExtensionController()
                .ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID)
                .accept(extension -> {
                    for (GeckoSession session : sessions) {
                        session.getWebExtensionController()
                                .setMessageDelegate(extension, extractorMessageDelegate, NATIVE_APP);
                    }
                    Log.i(TAG, "Unified extractor extension ready");
                }, error -> Log.e(TAG, "Failed to install unified extractor", error));
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        unifiedPage = buildUnifiedPage();

        pager = new ViewPager2(this);
        pager.setId(R.id.ai_pager);
        pager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        pager.setUserInputEnabled(true);
        pager.setOffscreenPageLimit(3);
        pagerAdapter = new BrowserPagerAdapter();
        pager.setAdapter(pagerAdapter);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateActivePage(position);
            }
        });
        root.addView(pager, new FrameLayout.LayoutParams(
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

    private ScrollView buildUnifiedPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setId(R.id.unified_page);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFFF5F5F5);

        unifiedContent = new LinearLayout(this);
        unifiedContent.setId(R.id.unified_content);
        unifiedContent.setOrientation(LinearLayout.VERTICAL);
        unifiedContent.setPadding(dp(14), dp(18), dp(14), dp(40));
        scroll.addView(unifiedContent, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        renderUnified();
        return scroll;
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
        panel.setOnClickListener(v -> {});

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

        launcherUnified = new Button(this);
        launcherUnified.setId(R.id.launcher_unified);
        launcherUnified.setText("Unified");
        launcherUnified.setTextSize(16);
        launcherUnified.setAllCaps(false);
        launcherUnified.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        launcherUnified.setPadding(dp(18), 0, dp(14), 0);
        launcherUnified.setOnClickListener(v -> {
            showPage(UNIFIED_PAGE, true);
            hideLauncher();
        });
        panel.addView(launcherUnified, new LinearLayout.LayoutParams(
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
                showPage(index, true);
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

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                dp(288), ViewGroup.LayoutParams.WRAP_CONTENT
        );
        panelParams.gravity = Gravity.CENTER;
        overlay.addView(panel, panelParams);
        return overlay;
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

    private void showPage(int page, boolean animate) {
        pager.setCurrentItem(clampPage(page), animate);
    }

    private void updateActivePage(int page) {
        activePage = clampPage(page);

        // Keep all three sites alive so the unified extractor can continue receiving updates.
        for (int i = 0; i < sessions.length; i++) {
            GeckoSession session = sessions[i];
            if (session != null) {
                session.setActive(true);
                session.setFocused(activePage == i);
            }
        }

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putInt(PREF_ACTIVE_PAGE, activePage)
                .apply();
        refreshLauncherSelection();
    }

    private void refreshLauncherSelection() {
        if (launcherUnified != null) {
            boolean selected = activePage == UNIFIED_PAGE;
            launcherUnified.setSelected(selected);
            launcherUnified.setText((selected ? "✓  " : "    ") + "Unified");
            launcherUnified.setTextColor(selected ? Color.WHITE : Color.BLACK);
            launcherUnified.setBackground(roundRect(
                    selected ? 0xFF171717 : 0xFFF0F0F0,
                    dp(16)
            ));
        }

        for (int i = 0; i < launcherItems.length; i++) {
            Button item = launcherItems[i];
            if (item == null) continue;
            boolean selected = activePage == i;
            item.setSelected(selected);
            item.setText((selected ? "✓  " : "    ") + NAMES[i]);
            item.setTextColor(selected ? Color.WHITE : Color.BLACK);
            item.setBackground(roundRect(
                    selected ? 0xFF171717 : 0xFFF0F0F0,
                    dp(16)
            ));
        }
    }

    private void handleSnapshot(JSONObject json) {
        String provider = json.optString("provider", "");
        int index = providerIndex(provider);
        if (index < 0) return;

        ProviderSnapshot snapshot = new ProviderSnapshot(NAMES[index]);
        snapshot.title = json.optString("title", NAMES[index]);
        snapshot.url = json.optString("url", "");
        snapshot.updatedAt = System.currentTimeMillis();

        JSONArray messages = json.optJSONArray("messages");
        if (messages != null) {
            for (int i = 0; i < messages.length(); i++) {
                JSONObject item = messages.optJSONObject(i);
                if (item == null) continue;
                String role = item.optString("role", "");
                String text = item.optString("text", "").trim();
                if (!("user".equals(role) || "assistant".equals(role)) || text.isEmpty()) {
                    continue;
                }
                snapshot.messages.add(new MessageItem(role, text));
            }
        }

        snapshots[index] = snapshot;
        runOnUiThread(this::renderUnified);
    }

    private void renderUnified() {
        if (unifiedContent == null) return;
        unifiedContent.removeAllViews();

        TextView title = new TextView(this);
        title.setText("Unified");
        title.setTextColor(Color.BLACK);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        unifiedContent.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("3つのAIサイトから現在の会話を読み取り、共通の会話UIに再構成しています。");
        subtitle.setTextColor(0xFF666666);
        subtitle.setTextSize(13);
        subtitle.setPadding(0, dp(4), 0, dp(14));
        unifiedContent.addView(subtitle);

        TextView status = new TextView(this);
        status.setText(buildStatusLine());
        status.setTextColor(0xFF555555);
        status.setTextSize(12);
        status.setPadding(dp(12), dp(9), dp(12), dp(9));
        status.setBackground(roundRect(0xFFE9E9E9, dp(12)));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        statusParams.bottomMargin = dp(14);
        unifiedContent.addView(status, statusParams);

        List<PromptGroup> groups = buildPromptGroups();
        if (groups.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("まだ会話データがありません。\nChatGPT・Gemini・Claudeで会話を開くと、ここに同じ形式で表示されます。");
            empty.setTextColor(0xFF555555);
            empty.setTextSize(15);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(60), dp(20), dp(60));
            unifiedContent.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            return;
        }

        for (PromptGroup group : groups) {
            addPromptGroupView(group);
        }
    }

    private String buildStatusLine() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < snapshots.length; i++) {
            if (i > 0) builder.append("   •   ");
            ProviderSnapshot snapshot = snapshots[i];
            builder.append(NAMES[i]).append(" ")
                    .append(snapshot == null ? 0 : snapshot.messages.size());
        }
        builder.append(" messages");
        return builder.toString();
    }

    private List<PromptGroup> buildPromptGroups() {
        LinkedHashMap<String, PromptGroup> groups = new LinkedHashMap<>();

        for (int provider = 0; provider < snapshots.length; provider++) {
            ProviderSnapshot snapshot = snapshots[provider];
            if (snapshot == null || snapshot.messages.isEmpty()) continue;

            Map<String, Integer> occurrences = new LinkedHashMap<>();
            List<Exchange> exchanges = toExchanges(snapshot.messages);
            for (Exchange exchange : exchanges) {
                if (exchange.user.isEmpty()) continue;

                String normalized = normalizePrompt(exchange.user);
                int occurrence = occurrences.getOrDefault(normalized, 0) + 1;
                occurrences.put(normalized, occurrence);
                String key = normalized + "::" + occurrence;

                PromptGroup group = groups.get(key);
                if (group == null) {
                    group = new PromptGroup(exchange.user);
                    groups.put(key, group);
                }
                group.responses[provider] = exchange.assistant;
            }
        }

        return new ArrayList<>(groups.values());
    }

    private List<Exchange> toExchanges(List<MessageItem> messages) {
        List<Exchange> exchanges = new ArrayList<>();
        String currentUser = null;
        StringBuilder assistant = new StringBuilder();

        for (MessageItem item : messages) {
            if ("user".equals(item.role)) {
                if (currentUser != null) {
                    exchanges.add(new Exchange(currentUser, assistant.toString().trim()));
                }
                currentUser = item.text;
                assistant.setLength(0);
            } else if ("assistant".equals(item.role) && currentUser != null) {
                if (assistant.length() > 0) assistant.append("\n\n");
                assistant.append(item.text);
            }
        }

        if (currentUser != null) {
            exchanges.add(new Exchange(currentUser, assistant.toString().trim()));
        }
        return exchanges;
    }

    private void addPromptGroupView(PromptGroup group) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, 0, 0, dp(18));

        TextView user = new TextView(this);
        user.setText(group.prompt);
        user.setTextColor(Color.WHITE);
        user.setTextSize(15);
        user.setTextIsSelectable(true);
        user.setPadding(dp(14), dp(11), dp(14), dp(11));
        user.setBackground(roundRect(0xFF202020, dp(16)));
        LinearLayout.LayoutParams userParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        userParams.gravity = Gravity.END;
        userParams.leftMargin = dp(28);
        block.addView(user, userParams);

        for (int provider = 0; provider < NAMES.length; provider++) {
            String response = group.responses[provider];
            if (response == null || response.isEmpty()) continue;

            LinearLayout responseCard = new LinearLayout(this);
            responseCard.setOrientation(LinearLayout.VERTICAL);
            responseCard.setPadding(dp(13), dp(10), dp(13), dp(12));
            responseCard.setBackground(roundRect(Color.WHITE, dp(16)));

            TextView label = new TextView(this);
            label.setText(NAMES[provider]);
            label.setTextColor(Color.BLACK);
            label.setTextSize(12);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setPadding(0, 0, 0, dp(5));
            responseCard.addView(label);

            TextView body = new TextView(this);
            body.setText(response);
            body.setTextColor(0xFF202020);
            body.setTextSize(15);
            body.setTextIsSelectable(true);
            body.setLineSpacing(0f, 1.08f);
            responseCard.addView(body);

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            cardParams.topMargin = dp(8);
            cardParams.rightMargin = dp(20);
            block.addView(responseCard, cardParams);
        }

        unifiedContent.addView(block, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private int providerIndex(String provider) {
        String normalized = provider.toLowerCase(Locale.ROOT);
        if (normalized.contains("chatgpt") || normalized.contains("openai")) return 0;
        if (normalized.contains("gemini") || normalized.contains("google")) return 1;
        if (normalized.contains("claude") || normalized.contains("anthropic")) return 2;
        return -1;
    }

    private String normalizePrompt(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    void injectTestSnapshot(int provider, String user, String assistant) {
        int index = Math.max(0, Math.min(provider, 2));
        ProviderSnapshot snapshot = new ProviderSnapshot(NAMES[index]);
        snapshot.messages.add(new MessageItem("user", user));
        snapshot.messages.add(new MessageItem("assistant", assistant));
        snapshots[index] = snapshot;
        renderUnified();
    }

    LinearLayout getUnifiedContentForTest() {
        return unifiedContent;
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
    public void onBackPressed() {
        if (launcherOverlay != null && launcherOverlay.getVisibility() == View.VISIBLE) {
            hideLauncher();
            return;
        }

        if (activePage < sessions.length && canGoBack[activePage]) {
            sessions[activePage].goBack();
            return;
        }

        if (activePage == UNIFIED_PAGE) {
            showPage(0, true);
            return;
        }

        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            for (GeckoSession session : sessions) {
                if (session != null && session.isOpen()) {
                    session.close();
                }
            }
        }
    }

    private int clampPage(int page) {
        return Math.max(0, Math.min(page, PAGE_COUNT - 1));
    }

    private GradientDrawable roundRect(int color, int radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class BrowserPagerAdapter extends RecyclerView.Adapter<PageHolder> {
        BrowserPagerAdapter() {
            setHasStableIds(true);
        }

        @Override
        public PageHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            FrameLayout container = new FrameLayout(MainActivity.this);
            container.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            return new PageHolder(container);
        }

        @Override
        public void onBindViewHolder(PageHolder holder, int position) {
            holder.container.removeAllViews();

            if (position < 3) {
                GeckoView view = browserViews[position];
                if (view == null) {
                    view = new GeckoView(MainActivity.this);
                    view.setAutofillEnabled(true);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        view.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_YES);
                    }
                    browserViews[position] = view;
                }
                detachFromParent(view);
                holder.container.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));
                if (view.getSession() == null) {
                    view.setSession(sessions[position]);
                }
            } else {
                detachFromParent(unifiedPage);
                holder.container.addView(unifiedPage, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));
            }
        }

        @Override
        public int getItemCount() {
            return PAGE_COUNT;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }
    }

    private void detachFromParent(View view) {
        ViewParent parent = view.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(view);
        }
    }

    private static final class PageHolder extends RecyclerView.ViewHolder {
        final FrameLayout container;

        PageHolder(FrameLayout container) {
            super(container);
            this.container = container;
        }
    }

    private static final class MessageItem {
        final String role;
        final String text;

        MessageItem(String role, String text) {
            this.role = role;
            this.text = text;
        }
    }

    private static final class ProviderSnapshot {
        final String provider;
        String title = "";
        String url = "";
        long updatedAt = 0L;
        final List<MessageItem> messages = new ArrayList<>();

        ProviderSnapshot(String provider) {
            this.provider = provider;
        }
    }

    private static final class Exchange {
        final String user;
        final String assistant;

        Exchange(String user, String assistant) {
            this.user = user == null ? "" : user;
            this.assistant = assistant == null ? "" : assistant;
        }
    }

    private static final class PromptGroup {
        final String prompt;
        final String[] responses = new String[3];

        PromptGroup(String prompt) {
            this.prompt = prompt;
        }
    }
}
