package org.sil.hearthis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import Script.BookInfo;
import Script.IScriptProvider;

@RunWith(MockitoJUnitRunner.class)
public class BookButtonTest {

    @Mock
    Context mockContext;
    @Mock
    IScriptProvider mockScriptProvider;

    private BookInfo createBookInfo(int bookNumber, String abbr) {
        BookInfo info = new BookInfo("test", bookNumber, "Genesis", 50, new int[50], mockScriptProvider);
        info.Abbr = abbr;
        return info;
    }

    @Test
    public void testGetForeColor_NothingTranslated_ReturnsGrey() {
        when(mockScriptProvider.GetTranslatedLineCount(0)).thenReturn(0);

        BookButton button = new BookButton(mockContext, null);
        button.Model = createBookInfo(0, "gen");

        assertEquals(R.color.navButtonUntranslatedColor, button.getForeColor());
    }

    @Test
    public void testGetForeColor_SomethingTranslated_Joshua_ReturnsHistoryColor() {
        when(mockScriptProvider.GetTranslatedLineCount(6)).thenReturn(3);

        BookButton button = new BookButton(mockContext, null);
        button.Model = createBookInfo(6, "josh");

        assertEquals(R.color.navButtonHistoryColor, button.getForeColor());
    }

    @Test
    public void testGetForeColor_SomethingTranslated_Genesis_ReturnsLawColor() {
        when(mockScriptProvider.GetTranslatedLineCount(0)).thenReturn(3);

        BookButton button = new BookButton(mockContext, null);
        button.Model = createBookInfo(0, "gen");

        assertEquals(R.color.navButtonLawColor, button.getForeColor());
    }

    @Test
    public void testGetLabel_NumericAbbr_FormatsCorrectly() {
        BookButton button = new BookButton(mockContext, null);
        button.Model = createBookInfo(18, "1sam");

        assertEquals("1Sam", button.getLabel());
    }

    @Test
    public void testGetLabel_AlphaAbbr_FormatsCorrectly() {
        BookButton button = new BookButton(mockContext, null);
        button.Model = createBookInfo(0, "gen");

        assertEquals("Gen", button.getLabel());
    }

    @Test
    public void testIsAllRecorded_Partial_ReturnsFalse() {
        when(mockScriptProvider.GetTranslatedLineCount(0)).thenReturn(5);
        when(mockScriptProvider.GetScriptLineCount(0)).thenReturn(10);

        BookButton button = new BookButton(mockContext, null);
        button.Model = createBookInfo(0, "gen");

        assertFalse(button.isAllRecorded());
    }

    @Test
    public void testIsAllRecorded_Complete_ReturnsTrue() {
        when(mockScriptProvider.GetTranslatedLineCount(0)).thenReturn(10);
        when(mockScriptProvider.GetScriptLineCount(0)).thenReturn(10);

        BookButton button = new BookButton(mockContext, null);
        button.Model = createBookInfo(0, "gen");

        assertTrue(button.isAllRecorded());
    }
}