package com.triai.browser;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isSelected;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

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
            onView(withId(R.id.launcher_button)).check(matches(isDisplayed())).perform(click());
            onView(withId(R.id.launcher_chatgpt)).check(matches(isDisplayed()));
            onView(withId(R.id.launcher_gemini)).check(matches(isDisplayed()));
            onView(withId(R.id.launcher_claude)).check(matches(isDisplayed()));

            onView(withId(R.id.launcher_gemini)).perform(click());
            onView(withId(R.id.launcher_button)).check(matches(isDisplayed())).perform(click());
            onView(withId(R.id.launcher_gemini)).check(matches(isSelected()));

            onView(withId(R.id.launcher_claude)).perform(click());
            onView(withId(R.id.launcher_button)).check(matches(isDisplayed())).perform(click());
            onView(withId(R.id.launcher_claude)).check(matches(isSelected()));

            onView(withId(R.id.launcher_chatgpt)).perform(click());
            onView(withId(R.id.launcher_button)).check(matches(isDisplayed())).perform(click());
            onView(withId(R.id.launcher_chatgpt)).check(matches(isSelected()));
        }
    }

    @Test
    public void browserUsesImmersiveFullscreen() {
        try (ActivityScenario<MainActivity> scenario = launchTestActivity()) {
            scenario.onActivity(activity -> {
                View decor = activity.getWindow().getDecorView();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowInsets insets = decor.getRootWindowInsets();
                    assertTrue("WindowInsets must be available", insets != null);
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
