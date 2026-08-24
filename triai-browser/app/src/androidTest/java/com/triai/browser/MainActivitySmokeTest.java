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
import android.widget.LinearLayout;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.viewpager2.widget.ViewPager2;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MainActivitySmokeTest {

    private ActivityScenario<MainActivity> launchTestActivity() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_TEST_MODE, true);
        return ActivityScenario.launch(intent);
    }

    @Test
    public void pagerLauncherAndUnifiedRendererWork() {
        try (ActivityScenario<MainActivity> scenario = launchTestActivity()) {
            scenario.onActivity(activity -> {
                ViewPager2 pager = activity.findViewById(R.id.ai_pager);
                Button launcher = activity.findViewById(R.id.launcher_button);
                View overlay = activity.findViewById(R.id.launcher_overlay);
                Button unified = activity.findViewById(R.id.launcher_unified);
                Button chatgpt = activity.findViewById(R.id.launcher_chatgpt);
                Button gemini = activity.findViewById(R.id.launcher_gemini);
                Button claude = activity.findViewById(R.id.launcher_claude);

                assertNotNull(pager);
                assertNotNull(launcher);
                assertNotNull(overlay);
                assertNotNull(unified);
                assertNotNull(chatgpt);
                assertNotNull(gemini);
                assertNotNull(claude);

                assertTrue("Horizontal page swiping must be enabled", pager.isUserInputEnabled());
                assertEquals(ViewPager2.ORIENTATION_HORIZONTAL, pager.getOrientation());
                assertEquals(3, pager.getOffscreenPageLimit());
                assertNotNull(pager.getAdapter());
                assertEquals(4, pager.getAdapter().getItemCount());

                activity.injectTestSnapshot(0, "same prompt", "GPT response");
                activity.injectTestSnapshot(1, "same prompt", "Gemini response");
                activity.injectTestSnapshot(2, "same prompt", "Claude response");

                LinearLayout content = activity.getUnifiedContentForTest();
                assertNotNull(content);
                assertTrue("Unified renderer should contain extracted data", content.getChildCount() > 4);

                launcher.performClick();
                assertEquals(View.VISIBLE, overlay.getVisibility());
                unified.performClick();
                assertEquals(3, pager.getCurrentItem());

                launcher.performClick();
                chatgpt.performClick();
                assertEquals(0, pager.getCurrentItem());

                launcher.performClick();
                gemini.performClick();
                assertEquals(1, pager.getCurrentItem());

                launcher.performClick();
                claude.performClick();
                assertEquals(2, pager.getCurrentItem());
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
