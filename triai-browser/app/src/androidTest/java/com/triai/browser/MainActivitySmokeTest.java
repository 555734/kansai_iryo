package com.triai.browser;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isSelected;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertTrue;

import android.content.res.Resources;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MainActivitySmokeTest {

    @Test
    public void allThreeAiTabsExistAndCanBeSwitched() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withText("ChatGPT")).check(matches(isDisplayed()));
            onView(withText("Gemini")).check(matches(isDisplayed()));
            onView(withText("Claude")).check(matches(isDisplayed()));

            onView(withText("Gemini")).perform(click()).check(matches(isSelected()));
            onView(withText("Claude")).perform(click()).check(matches(isSelected()));
            onView(withText("ChatGPT")).perform(click()).check(matches(isSelected()));
        }
    }

    @Test
    public void topTabsDoNotOverlapStatusBar() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withText("ChatGPT")).check((view, noViewFoundException) -> {
                if (noViewFoundException != null) {
                    throw noViewFoundException;
                }

                int[] location = new int[2];
                view.getLocationOnScreen(location);

                Resources resources = view.getResources();
                int resourceId = resources.getIdentifier("status_bar_height", "dimen", "android");
                int statusBarHeight = resourceId > 0
                        ? resources.getDimensionPixelSize(resourceId)
                        : 0;

                assertTrue(
                        "Top AI tabs must start below the Android status bar",
                        location[1] >= statusBarHeight
                );
            });
        }
    }
}
