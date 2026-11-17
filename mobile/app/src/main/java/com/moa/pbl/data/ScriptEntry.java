package com.moa.pbl.data;

public final class ScriptEntry {
    private final String transcript;
    private final String createdAt;
    private final String occurredOn;

    public ScriptEntry(String transcript, String createdAt, String occurredOn) {
        this.transcript = transcript;
        this.createdAt = createdAt;
        this.occurredOn = occurredOn;
    }

    public String transcript() { return transcript; }
    public String createdAt() { return createdAt; }
    public String occurredOn() { return occurredOn; }
}
