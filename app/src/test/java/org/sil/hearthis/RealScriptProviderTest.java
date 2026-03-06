package org.sil.hearthis;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import script.FileSystem;
import script.RealScriptProvider;
import script.TestFileSystem;

/**
 * Created by Thomson on 3/27/2016.
 */
public class RealScriptProviderTest {
    TestFileSystem tfs;
    FileSystem fs;
    private void makeDfaultFs() {
        tfs = new TestFileSystem(); // has a default info.txt
        fs = new FileSystem(tfs);
        ServiceLocator.getServiceLocator().setFileSystem(fs);
        tfs.project = "test";
        tfs.simulateFile(tfs.project + "/info.txt", tfs.getDefaultInfoTxtContent());
    }

    @Test
    public void getBasicDataFromInfoTxt() {
        makeDfaultFs();
        RealScriptProvider sp = new RealScriptProvider(tfs.project);
        ServiceLocator.getServiceLocator().setScriptProvider(sp);
        
        assertThat(sp.GetScriptLineCount(0), is(0));
        assertThat(sp.GetScriptLineCount(39), is(38));
        assertThat(sp.GetScriptLineCount(39, 1), is(12));
    }

    @Test
    public void getBasicLineData() {
        makeDfaultFs();
        tfs.MakeChapterContent("Matthew", 1, new String[]{"first line", "second line", "third line"}, null);
        RealScriptProvider sp = new RealScriptProvider(tfs.project);
        ServiceLocator.getServiceLocator().setScriptProvider(sp);
        
        assertThat(sp.GetLine(39, 1, 0).Text, is("first line"));
        assertThat(sp.GetLine(39, 1, 1).Text, is("second line"));
        assertThat(sp.GetLine(39, 1, 2).Text, is("third line"));
    }

    @Test
    public void getRecordingExists() {
        makeDfaultFs();
        tfs.MakeChapterContent("Matthew", 1, new String[]{"first line", "second line", "third line"},
                new String[] {null, "second line", null});
        RealScriptProvider sp = new RealScriptProvider(tfs.project);
        ServiceLocator.getServiceLocator().setScriptProvider(sp);
        
        assertFalse(sp.hasRecording(39, 1, 0));
        assertTrue(sp.hasRecording(39, 1, 1));
        assertFalse(sp.hasRecording(39, 1, 2));
    }
}
