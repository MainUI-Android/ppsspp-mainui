package io.github.mainui.stateapi;

import org.ppsspp.ppsspp.NativeApp;

/**
 * PPSSPP's side of the MainUI State API ({@link MainUIStateApi}). MainUI's slots 0-9 are
 * PPSSPP's own (its menu shows the first "Savestate slot count" of them); MainUI's resume state
 * is slot 99. Saves and loads go through PPSSPP's queue like the pause menu's and are answered
 * once PPSSPP's callback says they are done.
 */
public final class PpssppStates implements MainUIStateApi.Emulator {
    public static final PpssppStates INSTANCE = new PpssppStates();

    private static final int RESUME = 99;

    /** The activity is in front (resumed). */
    static volatile boolean resumed;
    /** A game was just asked for: the one running before it doesn't count until it has stopped. */
    private static volatile boolean waitForStop;

    private PpssppStates() {}

    /** The activity is up (created, or a new game handed to it): attach for the game coming. */
    public static void launched(boolean nativeReady) {
        waitForStop = nativeReady && NativeApp.mainuiRunning();
        MainUIStateApi.attach(INSTANCE);
    }

    public static void resumed(boolean r) { resumed = r; }

    private static int index(int slot) { return slot == MainUIStateApi.RESUME_SLOT ? RESUME : slot; }

    @Override
    public boolean isRunning() {
        boolean running = NativeApp.mainuiRunning();
        if (waitForStop) {
            if (running) return false;
            waitForStop = false;
        }
        return running;
    }

    @Override public boolean isReady() { return resumed && isRunning() && NativeApp.mainuiReady(); }

    @Override public int slotCount() { return Math.max(10, NativeApp.mainuiSlotCount()); }

    @Override
    public String save(int slot) throws Exception {
        String error = NativeApp.mainuiState(false, index(slot));
        if (!error.isEmpty()) throw new IllegalStateException(error);
        return "PPSSPP slot " + index(slot);
    }

    @Override
    public void load(int slot) throws Exception {
        if (slotTime(slot) == 0) throw new IllegalStateException("slot " + slot + " is empty");
        String error = NativeApp.mainuiState(true, index(slot));
        if (!error.isEmpty()) throw new IllegalStateException(error);
    }

    @Override public long slotTime(int slot) { return NativeApp.mainuiSlotTime(index(slot)); }
}
