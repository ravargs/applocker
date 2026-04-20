package com.android.internal.util;

public interface ThrowingRunnable extends Runnable {
    void runOrThrow() throws Exception;

    @Override
    default void run() {
        try {
            runOrThrow();
        } catch (Exception ex) {
            if (ex.getClass().isInstance(Error.class) || ex.getClass().isInstance(RuntimeException.class)) {
                throw new RuntimeException((ex));
            }
        }
    }
}
