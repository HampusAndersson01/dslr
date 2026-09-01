package com.hampus.dslraicoach;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class MarkdownBlocksTest {
    @Test
    public void parsesHeadingsBulletsAndParagraphs() {
        List<MarkdownBlocks.Block> blocks = MarkdownBlocks.parse("## Score\n8/10\n\n- Move closer\n- Lower ISO");

        assertEquals(MarkdownBlocks.Type.HEADING, blocks.get(0).type);
        assertEquals("Score", blocks.get(0).text);
        assertEquals(MarkdownBlocks.Type.PARAGRAPH, blocks.get(1).type);
        assertEquals("8/10", blocks.get(1).text);
        assertEquals(MarkdownBlocks.Type.BULLET, blocks.get(2).type);
        assertEquals("Move closer", blocks.get(2).text);
        assertEquals(MarkdownBlocks.Type.BULLET, blocks.get(3).type);
        assertEquals("Lower ISO", blocks.get(3).text);
    }

    @Test
    public void stripsCommonMarkdownEmphasisMarkers() {
        List<MarkdownBlocks.Block> blocks = MarkdownBlocks.parse("### **Fix next shot**\n*Use +1 EV*");

        assertEquals("Fix next shot", blocks.get(0).text);
        assertEquals("Use +1 EV", blocks.get(1).text);
    }
}
