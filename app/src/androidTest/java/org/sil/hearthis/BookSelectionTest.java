package org.sil.hearthis;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import script.BookInfo;
import script.FileSystem;
import script.RealScriptProvider;
import script.TestFileSystem;

/**
 * Tests the navigation flow from ChooseBookActivity to ChooseChapterActivity.
 */
@RunWith(AndroidJUnit4.class)
public class BookSelectionTest {

    private TestFileSystem fakeFileSystem;

    @Before
    public void setUp() {
        Intents.init();
        ServiceLocator.theOneInstance = new ServiceLocator();
        
        fakeFileSystem = new TestFileSystem();
        fakeFileSystem.externalFilesDirectory = "root";
        fakeFileSystem.project = "testProject";
        
        // Default setup for most tests (partially complete books)
        setupTestFileSystem("Matthew;10:0\nMark;8:0\n");
    }

    // Helper to allow different file setups
    private void setupTestFileSystem(String infoTxtContent) {
        StringBuilder infoTxtBuilder = new StringBuilder();
        for (int i = 0; i < 39; i++) infoTxtBuilder.append("Book").append(i).append(";\n");
        infoTxtBuilder.append(infoTxtContent);

        fakeFileSystem.simulateFile(fakeFileSystem.getInfoTxtPath(), infoTxtBuilder.toString());
        fakeFileSystem.SimulateDirectory(fakeFileSystem.getProjectDirectory());
        
        ServiceLocator.getServiceLocator().externalFilesDirectory = fakeFileSystem.externalFilesDirectory;
        ServiceLocator.getServiceLocator().setFileSystem(new FileSystem(fakeFileSystem));
        ServiceLocator.getServiceLocator().setScriptProvider(new RealScriptProvider(fakeFileSystem.getProjectDirectory()));
    }

    @After
    public void tearDown() {
        Intents.release();
    }

    @Test
    public void chooseBookActivity_displaysBooks() {
        try (ActivityScenario<ChooseBookActivity> ignored = ActivityScenario.launch(ChooseBookActivity.class)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            
            onView(withBookName("Matthew")).check(matches(isDisplayed()));
            onView(withBookName("Mark")).check(matches(isDisplayed()));
        }
    }

    @Test
    public void selectingBook_navigatesToChapters() {
        try (ActivityScenario<ChooseBookActivity> ignored = ActivityScenario.launch(ChooseBookActivity.class)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            onView(withBookName("Matthew")).perform(click());

            intended(allOf(
                hasComponent(ChooseChapterActivity.class.getName()),
                hasExtra(is("bookInfo"), instanceOf(BookInfo.class))
            ));

            onView(withId(R.id.bookNameText)).check(matches(withText("Matthew")));
        }
    }

    @Test
    public void selectingChapter_navigatesToRecordActivity() {
        try (ActivityScenario<ChooseBookActivity> ignored = ActivityScenario.launch(ChooseBookActivity.class)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            onView(withBookName("Matthew")).perform(click());
            
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            onView(withChapterNumber(1)).perform(click());

            intended(allOf(
                hasComponent(RecordActivity.class.getName()),
                hasExtra("chapter", 1)
            ));
        }
    }

    @Test
    public void chooseBookActivity_showsRecordedStatus() {
        // New setup for this specific test where Matthew is fully recorded
        setupTestFileSystem("Matthew;10:10\nMark;8:0\n");

        try (ActivityScenario<ChooseBookActivity> ignored = ActivityScenario.launch(ChooseBookActivity.class)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            // Verify Matthew is marked as recorded, and Mark is not.
            onView(withBookName("Matthew")).check(matches(isFullyRecorded()));
            onView(withBookName("Mark")).check(matches(not(isFullyRecorded())));
        }
    }

    /**
     * Custom matcher to find a BookButton based on its Model's name.
     */
    public static Matcher<View> withBookName(final String bookName) {
        return new TypeSafeMatcher<>() {
            @Override
            public boolean matchesSafely(View view) {
                if (view instanceof BookButton button) {
                    return button.Model != null && bookName.equals(button.Model.Name);
                }
                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("with book name: " + bookName);
            }
        };
    }

    /**
     * Custom matcher to find a ChapterButton based on its chapter number.
     */
    public static Matcher<View> withChapterNumber(final int chapterNumber) {
        return new TypeSafeMatcher<>() {
            @Override
            public boolean matchesSafely(View view) {
                if (view instanceof ChapterButton button) {
                    return button.chapterNumber == chapterNumber;
                }
                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("with chapter number: " + chapterNumber);
            }
        };
    }

    /**
     * Custom matcher to check if a ProgressButton is fully recorded.
     */
    public static Matcher<View> isFullyRecorded() {
        return new TypeSafeMatcher<>() {
            @Override
            public boolean matchesSafely(View view) {
                if (view instanceof ProgressButton button) {
                    return button.isAllRecorded();
                }
                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("is fully recorded");
            }
        };
    }
}
