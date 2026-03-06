package org.sil.hearthis;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.os.Build;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumentation tests for SyncActivity.
 * Focuses on UI state, CameraX initialization, and SyncService integration.
 */
@RunWith(AndroidJUnit4.class)
public class SyncActivityTest {

    @Rule
    public GrantPermissionRule permissionRule = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ?
            GrantPermissionRule.grant(Manifest.permission.CAMERA, Manifest.permission.POST_NOTIFICATIONS) :
            GrantPermissionRule.grant(Manifest.permission.CAMERA);

    @Before
    public void setUp() {
        ServiceLocator.theOneInstance = new ServiceLocator();
    }

    @Test
    public void syncActivity_initialState_showsIpAddress() {
        try (ActivityScenario<SyncActivity> ignored = ActivityScenario.launch(SyncActivity.class)) {
            // Verify basic UI elements are present
            onView(withId(R.id.progress)).check(matches(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
            onView(withId(R.id.continue_button)).check(matches(isDisplayed()));
            onView(withId(R.id.continue_button)).check(matches(not(isEnabled())));

            // Our IP should be displayed
            onView(withId(R.id.our_ip_address)).check(matches(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
        }
    }

    @Test
    public void syncActivity_startsCamera_onScanClick() {
        try (ActivityScenario<SyncActivity> scenario = ActivityScenario.launch(SyncActivity.class)) {
            // Click the scan button in the layout
            onView(withId(R.id.scan_button)).perform(click());

            // PreviewView should become visible
            onView(withId(R.id.preview_view)).check(matches(isDisplayed()));
            
            // Verify internal state: scanning should be true
            scenario.onActivity(activity -> assertTrue("Activity should be in scanning state", activity.scanning));
        }
    }

    @Test
    public void syncActivity_serviceIntegration_updatesStatus() {
        try (ActivityScenario<SyncActivity> scenario = ActivityScenario.launch(SyncActivity.class)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            // Simulate a notification from the sync server
            scenario.onActivity(activity -> activity.onNotification("Connected to Desktop"));

            // Verify UI updates: success message shown and button enabled
            onView(withText(R.string.sync_success)).check(matches(isDisplayed()));
            onView(withId(R.id.continue_button)).check(matches(isEnabled()));
        }
    }

    @Test
    public void syncActivity_fileTransfer_updatesProgress() {
        try (ActivityScenario<SyncActivity> scenario = ActivityScenario.launch(SyncActivity.class)) {
            final String testPath = "Genesis/1/1.wav";
            
            // Simulate receiving a file
            scenario.onActivity(activity -> activity.receivingFile(testPath));

            // Verify progress view shows the path
            Context context = ApplicationProvider.getApplicationContext();
            String expectedText = context.getString(R.string.receiving_file, testPath);
            onView(withId(R.id.progress)).check(matches(withText(expectedText)));
        }
    }

    @Test
    public void syncActivity_clickContinue_finishesActivity() {
        try (ActivityScenario<SyncActivity> scenario = ActivityScenario.launch(SyncActivity.class)) {
            // Enable the button via a simulated success notification
            scenario.onActivity(activity -> activity.onNotification("Success"));
            
            // Click Continue
            onView(withId(R.id.continue_button)).perform(click());
            
            // Verify activity is finishing
            scenario.onActivity(activity -> assertTrue("Activity should be finishing after clicking Continue", activity.isFinishing()));
        }
    }
}
