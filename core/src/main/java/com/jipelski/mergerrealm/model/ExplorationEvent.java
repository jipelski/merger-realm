package com.jipelski.mergerrealm.model;

/**
 * A single event that occurred during exploration.
 * Stored in the exploration log for display to the player.
 */
public class ExplorationEvent {

    private long timestamp;      // milliseconds since exploration started
    private String text;         // display text e.g. "Encountered a wolf! Took 5 damage"
    private String type;         // "combat", "loot", "heal", "nothing", "trap", "curse", "death"

    public ExplorationEvent() {}

    public ExplorationEvent(long timestamp, String text, String type) {
        this.timestamp = timestamp;
        this.text = text;
        this.type = type;
    }

    public long getTimestamp() { return timestamp; }
    public String getText() { return text; }
    public String getType() { return type; }

    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public void setText(String text) { this.text = text; }
    public void setType(String type) { this.type = type; }

    /**
     * Returns timestamp formatted as [MM:SS] for the log display.
     */
    public String getFormattedTime() {
        long totalSeconds = timestamp / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("[%02d:%02d]", minutes, seconds);
    }
}
