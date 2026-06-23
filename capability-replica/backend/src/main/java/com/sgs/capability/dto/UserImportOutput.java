package com.sgs.capability.dto;

import java.util.ArrayList;
import java.util.List;

/** Synchronous local result for the original background user import job. */
public class UserImportOutput {
    public List<ImportUserDto> items = new ArrayList<>();
    public FileDto file;
    public FileDto errorFile;
    public int totalCount;
    public int importedCount;
    public int errorCount;
    public boolean invalidFile;
}
