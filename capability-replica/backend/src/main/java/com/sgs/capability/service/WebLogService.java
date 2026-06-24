package com.sgs.capability.service;

import com.sgs.capability.dto.FileDto;
import com.sgs.capability.model.WebLogOutput;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Reads local web logs and packages them like WebLogAppService. */
@Service
public class WebLogService {
    private final Path logsPath;
    private final TempFileService tempFiles;

    public WebLogService(@Value("${replica.logs.path:logs}") String logsPath, TempFileService tempFiles) {
        this.logsPath = Path.of(logsPath);
        this.tempFiles = tempFiles;
    }

    public WebLogOutput latestWebLogs() {
        WebLogOutput output = new WebLogOutput();
        latestLogFile().ifPresent(path -> {
            try {
                List<String> reversed = new ArrayList<>(Files.readAllLines(path, StandardCharsets.UTF_8));
                Collections.reverse(reversed);
                int levelCount = 0;
                int lineCount = 0;
                for (String line : reversed.stream().limit(1000).toList()) {
                    lineCount++;
                    if (startsWithLogLevel(line)) {
                        levelCount++;
                    }
                    if (levelCount == 100) {
                        break;
                    }
                }
                List<String> latestLines = new ArrayList<>(reversed.stream().limit(lineCount).toList());
                Collections.reverse(latestLines);
                output.latestWebLogLines.addAll(latestLines);
            } catch (IOException ex) {
                output.latestWebLogLines.add("ERROR Unable to read local web log: " + ex.getMessage());
            }
        });
        return output;
    }

    public FileDto downloadWebLogs() {
        ensureSeedLog();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (Path path : allLogFiles()) {
                zip.putNextEntry(new ZipEntry(path.getFileName().toString()));
                Files.copy(path, zip);
                zip.closeEntry();
            }
            zip.finish();
            return tempFiles.put("WebSiteLogs.zip", "application/zip", bytes.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to package web logs", ex);
        }
    }

    private java.util.Optional<Path> latestLogFile() {
        try (Stream<Path> stream = Files.walk(logsPath)) {
            return stream
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".txt"))
                    .max(Comparator.comparing(path -> path.toFile().lastModified()));
        } catch (IOException ex) {
            return java.util.Optional.empty();
        }
    }

    private List<Path> allLogFiles() throws IOException {
        try (Stream<Path> stream = Files.list(logsPath)) {
            return stream.filter(Files::isRegularFile).sorted().toList();
        }
    }

    private void ensureSeedLog() {
        try {
            Files.createDirectories(logsPath);
            Path file = logsPath.resolve("capability-web.txt");
            if (Files.exists(file)) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            List<String> lines = List.of(
                    "INFO  " + now.minusMinutes(12) + " Capability replica backend started",
                    "DEBUG " + now.minusMinutes(10) + " Loaded SQL Server database state",
                    "INFO  " + now.minusMinutes(8) + " Registered ABP-style application service routes",
                    "WARN  " + now.minusMinutes(4) + " Production external providers are disabled in local replica",
                    "INFO  " + now.minusMinutes(1) + " WebLogAppService latest log request ready"
            );
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to initialize local web logs", ex);
        }
    }

    private boolean startsWithLogLevel(String line) {
        return line.startsWith("DEBUG") || line.startsWith("INFO") || line.startsWith("WARN")
                || line.startsWith("ERROR") || line.startsWith("FATAL");
    }
}
