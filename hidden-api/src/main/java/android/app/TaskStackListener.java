package android.app;

import android.app.ActivityManager.RunningTaskInfo;
import android.content.ComponentName;
import android.os.IBinder;
import android.os.RemoteException;
import android.window.TaskSnapshot;

import dev.rikka.tools.refine.RefineAs;

/**
 * Classes interested in observing only a subset of changes using ITaskStackListener can extend
 * this class to avoid having to implement all the methods.
 *
 * @hide
 */
@RefineAs(TaskStackListener.class)
public abstract class TaskStackListener extends ITaskStackListener.Stub {

    public TaskStackListener() {
    }

    public static IBinder asBinder() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Indicates that this listener lives in system server.
     */
    public void setIsLocal() {

    }

    @Override
    public void onTaskStackChanged() {
    }

    @Override
    public void onActivityPinned(String packageName, int userId, int taskId, int rootTaskId) {
    }

    @Override
    public void onActivityUnpinned() {
    }

    @Override
    public void onActivityRestartAttempt(RunningTaskInfo task, boolean homeTaskVisible,
                                         boolean clearedTask, boolean wasVisible) {
    }

    @Override
    public void onActivityForcedResizable(String packageName, int taskId, int reason) {
    }

    @Override
    public void onActivityDismissingDockedTask() {
    }

    @Override
    public void onActivityLaunchOnSecondaryDisplayFailed(RunningTaskInfo taskInfo,
                                                         int requestedDisplayId) {

    }

    /**
     * @deprecated see {@link
     * #onActivityLaunchOnSecondaryDisplayFailed(RunningTaskInfo, int)}
     */
    @Deprecated
    public void onActivityLaunchOnSecondaryDisplayFailed() throws RemoteException {
    }

    @Override
    public void onActivityLaunchOnSecondaryDisplayRerouted(RunningTaskInfo taskInfo,
                                                           int requestedDisplayId) {
    }

    @Override
    public void onTaskCreated(int taskId, ComponentName componentName) {
    }

    @Override
    public void onTaskRemoved(int taskId) {
    }

    @Override
    public void onTaskMovedToFront(RunningTaskInfo taskInfo) {

    }

    /**
     * @deprecated see {@link #onTaskMovedToFront(RunningTaskInfo)}
     */
    @Deprecated
    public void onTaskMovedToFront(int taskId) throws RemoteException {
    }

    @Override
    public void onTaskRemovalStarted(RunningTaskInfo taskInfo) {

    }

    /**
     * @deprecated see {@link #onTaskRemovalStarted(RunningTaskInfo)}
     */
    @Deprecated
    public void onTaskRemovalStarted(int taskId) throws RemoteException {
    }

    @Override
    public void onTaskDescriptionChanged(RunningTaskInfo taskInfo) {
        //onTaskDescriptionChanged(taskInfo.taskId, taskInfo.taskDescription);
    }

    /**
     * @deprecated see {@link #onTaskDescriptionChanged(RunningTaskInfo)}
     */
    @Deprecated
    public void onTaskDescriptionChanged(int taskId, ActivityManager.TaskDescription td)
            throws RemoteException {
    }

    @Override
    public void onActivityRequestedOrientationChanged(int taskId, int requestedOrientation) {
    }

    @Override
    public void onTaskProfileLocked(RunningTaskInfo taskInfo, int userId) {

    }

    /**
     * @deprecated see {@link #onTaskProfileLocked(RunningTaskInfo, int)}
     */
    @Deprecated
    public void onTaskProfileLocked(RunningTaskInfo taskInfo)
            throws RemoteException {
    }

    @Override
    public void onTaskSnapshotChanged(int taskId, TaskSnapshot snapshot) {

    }

    @Override
    public void onTaskSnapshotInvalidated(int taskId) {
    }

    @Override
    public void onBackPressedOnTaskRoot(RunningTaskInfo taskInfo) {
    }

    @Override
    public void onTaskDisplayChanged(int taskId, int newDisplayId) {
    }

    @Override
    public void onRecentTaskListUpdated() {
    }

    @Override
    public void onRecentTaskListFrozenChanged(boolean frozen) {
    }

    @Override
    public void onRecentTaskRemovedForAddTask(int taskId) {
    }

    @Override
    public void onTaskFocusChanged(int taskId, boolean focused) {
    }

    @Override
    public void onTaskRequestedOrientationChanged(int taskId, int requestedOrientation) {
    }

    @Override
    public void onActivityRotation(int displayId) {
    }

    @Override
    public void onTaskMovedToBack(RunningTaskInfo taskInfo) {
    }

    @Override
    public void onLockTaskModeChanged(int mode) {
    }
}
