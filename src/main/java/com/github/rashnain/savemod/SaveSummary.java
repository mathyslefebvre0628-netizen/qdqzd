package com.github.rashnain.savemod;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;

public class SaveSummary {
    private final String saveFileName;
    private final String worldDir;
    private final long size;
    private long lastPlayed = -1;

    public SaveSummary(String saveFileName, String worldDir, long size) {
        this.saveFileName = saveFileName;
        this.worldDir = worldDir;
        this.size = size;
    }

    public String getSaveFileName() { return saveFileName; }

    public String getSaveName() {
        if (saveFileName.length() <= 24) return saveFileName;
        return saveFileName.substring(20, saveFileName.length() - 4);
    }

    public String getWorldDir() { return worldDir; }

    public long getLastPlayed() {
        if (lastPlayed == -1) {
            Date date;
            try {
                date = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
                        .parse(saveFileName.substring(0, 19));
            } catch (ParseException e) {
                date = Date.from(Instant.EPOCH);
            }
            lastPlayed = date.getTime();
        }
        return lastPlayed;
    }

    public String getSizeInMB() {
        long mb = 1_000_000L;
        return size < mb ? "< 1" : String.valueOf(size / mb);
    }
}
