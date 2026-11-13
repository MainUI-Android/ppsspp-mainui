package io.github.mainui.stateapi;

import org.ppsspp.ppsspp.NativeApp;

import java.lang.ref.WeakReference;

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

    /** Games booted when the wait for the new one began ({@link #waitForStop}). */
    private static volatile int bootsAtLaunch;

    /** A game is coming ([newGame]: the one running doesn't count until it has booted), or not: attach. */
    private static void launched(boolean newGame) {
        bootsAtLaunch = newGame ? NativeApp.mainuiBoots() : 0;
        waitForStop = newGame && NativeApp.mainuiRunning();
        MainUIStateApi.attach(INSTANCE);
    }

    /** The activity followed: the newest one created. */
    private static WeakReference<Object> owner = new WeakReference<>(null);

    /**
     * [activity] was created. [shortcutParam]: the game it hands to a native side already up
     * (null: none, a first start, or a recreate). Without another game the one running, if any,
     * is still the game: an activity backed out of leaves it running for the next one.
     */
    public static void created(Object activity, String shortcutParam) {
        owner = new WeakReference<>(activity);
        launched(shortcutParam != null && !shortcutParam.isEmpty() && !NativeApp.mainuiRunsGame(shortcutParam));
    }

    /**
     * [activity] is gone for good: no game until the next one, unless that one came already (a
     * finish() and a quick relaunch, where the old activity's last calls come after the new one's).
     */
    public static void finished(Object activity) {
        if (owner.get() == activity) MainUIStateApi.detach(INSTANCE);
    }

    /**
     * A game handed to the running activity (not a file picker's answer or a plain launch): the
     * one running before it doesn't count until the new one has booted. The game running already
     * is ignored by PPSSPP, so it changes nothing here either.
     */
    public static void newGame(String shortcutParam) {
        if (NativeApp.mainuiRunsGame(shortcutParam)) return;
        launched(true);
    }

    public static void resumed(Object activity, boolean r) { if (owner.get() == activity) resumed = r; }

    private static int index(int slot) { return slot == MainUIStateApi.RESUME_SLOT ? RESUME : slot; }

    @Override
    public boolean isRunning() {
        boolean running = NativeApp.mainuiRunning();
        if (waitForStop) {
            // Over once the old game stopped or the new one booted.
            if (running && NativeApp.mainuiBoots() == bootsAtLaunch) return false;
            waitForStop = false;
        }
        return running;
    }

    @Override public boolean isReady() { return resumed && isRunning() && NativeApp.mainuiReady(); }

    @Override public int slotCount() { return Math.max(10, NativeApp.mainuiSlotCount()); }

    @Override public boolean pause() { return NativeApp.mainuiPause(true); }

    @Override public void resume() { NativeApp.mainuiPause(false); }

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
