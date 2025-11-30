package com.star.logscanner.parser;

public interface LogParser {

    boolean canParse(String fileName, String contentSample);

    default boolean canParse(String line) {
        return canParse(null, line);
    }

    ParseResult parseLine(String line, long lineNumber, ParseContext context);

    void reset();

    String getSupportedFormat();

    default String getParserType() {
        return getSupportedFormat();
    }

    default int getPriority() {
        return 0;
    }

    default boolean supportsMultiLine() {
        return false;
    }

    default String getDescription() {
        return "Log parser for " + getSupportedFormat() + " format";
    }
}
