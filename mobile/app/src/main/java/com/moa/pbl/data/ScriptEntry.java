package com.moa.pbl.data;

public final class ScriptEntry {
    private final String transcript;
    private final String createdAt;

    public ScriptEntry(String transcript, String createdAt) {
        this.transcript = transcript;
        this.createdAt = createdAt;
    }

    public String transcript() { return transcript; }
    public String createdAt() { return createdAt; }
}
