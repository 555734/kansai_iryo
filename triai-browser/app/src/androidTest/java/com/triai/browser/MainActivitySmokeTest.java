package com.triai.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.viewpager2.widget.ViewPager2;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.geckoview.GeckoView;

@RunWith(AndroidJUnit4.class)
public class MainActivitySmokeTest {

    private ActivityScenario<MainActivity> launchTestActivity() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_TEST_MODE, true);
        return ActivityScenario.launch(intent);
    }

    @Test
    public void pagerLauncherAndCombinedOverviewAllWork() {
        try (ActivityScenario<MainActivity> scenario = launchTestActivity()) {
            scenario.onActivity(activity -> {
                ViewPager2 pager = activity.findViewById(R.id.ai_pager);
                Button launcher = activity.findViewById(R.id.launcher_button);
                View overlay = activity.findViewById(R.id.launcher_overlay);
                Button overviewButton = activity.findViewById(R.id.launcher_overview);
                View overview = activity.findViewById(R.id.overview_container);
                GeckoView overviewChatgpt = activity.findViewById(R.id.overview_chatgpt);
                GeckoView overviewGemini = activity.findViewById(R.id.overview_gemini);
                GeckoView overviewClaude = activity.findViewById(R.id.overview_claude);
                Button chatgpt = activity.findViewById(R.id.launcher_chatgpt);
                Button gemini = activity.findViewById(R.id.launcher_gemini);
                Button claude = activity.findViewById(R.id.launcher_claude);

                assertNotNull(pager);
                assertNotNull(launcher);
                assertNotNull(overlay);
                assertNotNull(overviewButton);
                assertNotNull(overview);
                assertNotNull(overviewChatgpt);
                assertNotNull(overviewGemini);
                assertNotNull(overviewClaude);
                assertNotNull(chatgpt);
                assertNotNull(gemini);
                assertNotNull(claude);

                assertTrue("Horizontal page swiping must be enabled", pager.isUserInputEnabled());
                assertEquals(ViewPager2.ORIENTATION_HORIZONTAL, pager.getOrientation());
                assertEquals(2, pager.getOffscreenPageLimit());
                assertEquals(View.VISIBLE, pager.getVisibility());
                assertEquals(View.GONE, overview.getVisibility());

                launcher.performClick();
                assertEquals(View.VISIBLE, overlay.getVisibility());
                overviewButton.performClick();

                assertEquals(View.GONE, pager.getVisibility());
                assertEquals(View.VISIBLE, overview.getVisibility());
                assertNotNull(overviewChatgpt.getSession());
                assertNotNull(overviewGemini.getSession());
                assertNotNull(overviewClaude.getSession());

                launcher.performClick();
                assertTrue(overviewButton.isSelected());
                chatgpt.performClick();
                assertEquals(View.VISIBLE, pager.getVisibility());
                assertEquals(View.GONE, overview.getVisibility());
                assertEquals(0, pager.getCurrentItem());

                launcher.performClick();
                gemini.performClick();
                assertEquals(1, pager.getCurrentItem());
                launcher.performClick();
                assertTrue(gemini.isSelected());

                claude.performClick();
                assertEquals(2, pager.getCurrentItem());
                launcher.performClick();
                assertTrue(claude.isSelected());
            });
        }
    }

    @Test
    public void browserUsesImmersiveFullscreen() {
        try (ActivityScenario<MainActivity> scenario = launchTestActivity()) {
            scenario.onActivity(activity -> {
                View decor = activity.getWindow().getDecorView();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowInsets insets = decor.getRootWindowInsets();
                    assertNotNull(insets);
                    assertFalse(
                            "Status bar must be hidden in fullscreen mode",
                            insets.isVisible(WindowInsets.Type.statusBars())
                    );
                    assertFalse(
                            "Navigation bar must be hidden in fullscreen mode",
                            insets.isVisible(WindowInsets.Type.navigationBars())
                    );
                } else {
                    int flags = decor.getSystemUiVisibility();
                    assertTrue((flags & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0);
                    assertTrue((flags & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0);
                    assertTrue((flags & View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) != 0);
                }
            });
        }
    }
}
