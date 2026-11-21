package com.example.zalo.client.util;

import javafx.application.Platform;

/** Safely schedules UI work from receive and connection threads. */
public final class UiThread {

    private UiThread() {
    }

    public static void run(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
