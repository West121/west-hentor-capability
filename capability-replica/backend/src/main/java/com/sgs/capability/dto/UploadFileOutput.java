package com.sgs.capability.dto;

/** Uploaded binary descriptor copied from DemoUiComponentsController. */
public class UploadFileOutput {
    public String id;
    public String fileName;

    public UploadFileOutput() {
    }

    public UploadFileOutput(String id, String fileName) {
        this.id = id;
        this.fileName = fileName;
    }
}
