package android.app;

import android.os.IBinder;

public interface IForegroundServiceObserver {

    /**
     * Notify the client of all changes to services' foreground state.
     *
     * @param serviceToken unique identifier for a service instance
     * @param packageName  identifies the app hosting the service
     * @param userId       identifies the started user in which the app is running
     * @param isForeground whether the service is in the "foreground" mode now, i.e.
     *                     whether it is an FGS
     * @hide
     */
    void onForegroundStateChanged(IBinder serviceToken, String packageName, int userId, boolean isForeground);
}
