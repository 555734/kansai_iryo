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
    public void launcherCanSwitchAllThreeAiSessions() {
        try (ActivityScenario<MainActivity> scenario = launchTestActivity()) {
            scenario.onActivity(activity -> {
                Button launcher = activity.findViewById(R.id.launcher_button);
                View overlay = activity.findViewById(R.id.launcher_overlay);
                Button chatgpt = activity.findViewById(R.id.launcher_chatgpt);
                Button gemini = activity.findViewById(R.id.launcher_gemini);
                Button claude = activity.findViewById(R.id.launcher_claude);

                assertNotNull(launcher);
                assertNotNull(overlay);
                assertNotNull(chatgpt);
                assertNotNull(gemini);
                assertNotNull(claude);

                assertEquals(View.VISIBLE, launcher.getVisibility());
                assertEquals(View.GONE, overlay.getVisibility());

                launcher.performClick();
                assertEquals(View.VISIBLE, overlay.getVisibility());
                assertEquals(View.VISIBLE, chatgpt.getVisibility());
                assertEquals(View.VISIBLE, gemini.getVisibility());
                assertEquals(View.VISIBLE, claude.getVisibility());

                gemini.performClick();
                assertEquals(View.GONE, overlay.getVisibility());
                assertEquals(View.VISIBLE, launcher.getVisibility());
                launcher.performClick();
                assertTrue(gemini.isSelected());

                claude.performClick();
                launcher.performClick();
                assertTrue(claude.isSelected());

                chatgpt.performClick();
                launcher.performClick();
                assertTrue(chatgpt.isSelected());
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
