package com.hampus.dslraicoach;

import java.util.ArrayList;
import java.util.List;

public final class MarkdownBlocks {
    public enum Type {
        HEADING,
        BULLET,
        PARAGRAPH
    }

    public static final class Block {
        public final Type type;
        public final String text;

        private Block(Type type, String text) {
            this.type = type;
            this.text = text;
        }
    }

    private MarkdownBlocks() {}

    public static List<Block> parse(String markdown) {
        List<Block> blocks = new ArrayList<>();
        if (markdown == null) {
            return blocks;
        }

        StringBuilder paragraph = new StringBuilder();
        String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                flushParagraph(blocks, paragraph);
                continue;
            }

            if (line.startsWith("#")) {
                flushParagraph(blocks, paragraph);
                blocks.add(new Block(Type.HEADING, stripInlineMarkers(line.replaceFirst("^#{1,6}\\s*", ""))));
            } else if (line.startsWith("- ") || line.startsWith("* ") || line.matches("^\\d+\\.\\s+.*")) {
                flushParagraph(blocks, paragraph);
                blocks.add(new Block(Type.BULLET, stripInlineMarkers(line.replaceFirst("^(?:[-*]|\\d+\\.)\\s+", ""))));
            } else {
                if (paragraph.length() > 0) {
                    paragraph.append(' ');
                }
                paragraph.append(stripInlineMarkers(line));
            }
        }
        flushParagraph(blocks, paragraph);
        return blocks;
    }

    private static void flushParagraph(List<Block> blocks, StringBuilder paragraph) {
        if (paragraph.length() == 0) {
            return;
        }
        blocks.add(new Block(Type.PARAGRAPH, paragraph.toString()));
        paragraph.setLength(0);
    }

    private static String stripInlineMarkers(String text) {
        return text.replace("**", "").replace("__", "").replace("*", "").replace("_", "").trim();
    }
}
