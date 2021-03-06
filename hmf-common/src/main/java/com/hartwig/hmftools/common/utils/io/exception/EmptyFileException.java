package com.hartwig.hmftools.common.utils.io.exception;

import java.io.IOException;

import org.jetbrains.annotations.NotNull;

public class EmptyFileException extends IOException {

    private static final String EMPTY_FILES_ERROR = "File %s was found empty in path -> %s";

    public EmptyFileException(@NotNull final String fileName, @NotNull final String filePath) {
        super(String.format(EMPTY_FILES_ERROR, fileName, filePath));
    }

    public EmptyFileException(@NotNull final String fileName) {
        super(String.format("File %s was found empty", fileName));
    }
}
