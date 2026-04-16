package com.chenweikeng.mcparks.cursor;

public class CursorState {
    private static final CursorState INSTANCE = new CursorState();

    private volatile boolean automaticallyReleasedCursor = false;
    private volatile int windowRestoreGrace = 0;

    public static CursorState getInstance() {
        return INSTANCE;
    }

    public boolean isAutomaticallyReleasedCursor() {
        return automaticallyReleasedCursor;
    }

    public void setAutomaticallyReleasedCursor(boolean value) {
        this.automaticallyReleasedCursor = value;
    }

    public boolean isWithinWindowRestoreGrace() {
        return windowRestoreGrace > 0;
    }

    public void setWindowRestoreGrace(int ticks) {
        this.windowRestoreGrace = ticks;
    }

    public void tickGrace() {
        if (windowRestoreGrace > 0) {
            windowRestoreGrace--;
        }
    }

    public void reset() {
        automaticallyReleasedCursor = false;
        windowRestoreGrace = 0;
    }
}
