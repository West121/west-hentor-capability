package com.sgs.capability.dto;

import java.util.UUID;

/** Download descriptor copied from the original ABP file API shape. */
public class FileDto {
    public String fileName;
    public String fileType;
    public String fileToken;

    public FileDto() {
    }

    public FileDto(String fileName, String fileType) {
        this(fileName, fileType, UUID.randomUUID().toString().replace("-", ""));
    }

    public FileDto(String fileName, String fileType, String fileToken) {
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileToken = fileToken;
    }
}
