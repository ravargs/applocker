package android.app;

import static android.view.Display.INVALID_DISPLAY;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.List;

/**
 * This class gives information about, and interacts with activities and their containers like task,
 * stacks, and displays.
 *
 * @hide
 */
public class ActivityTaskManager {

    /**
     * Invalid stack ID.
     */
    public static final int INVALID_STACK_ID = -1;

    /**
     * Invalid task ID.
     *
     * @hide
     */
    public static final int INVALID_TASK_ID = -1;

    /**
     * Invalid windowing mode.
     *
     * @hide
     */
    public static final int INVALID_WINDOWING_MODE = -1;

    /**
     * Input parameter to { IActivityTaskManager#resizeTask} which indicates
     * that the resize doesn't need to preserve the window, and can be skipped if bounds
     * is unchanged. This mode is used by window manager in most cases.
     *
     * @hide
     */
    public static final int RESIZE_MODE_SYSTEM = 0;

    /**
     * Input parameter to {IActivityTaskManager#resizeTask} which indicates
     * that the resize should preserve the window if possible.
     *
     * @hide
     */
    public static final int RESIZE_MODE_PRESERVE_WINDOW = (0x1 << 0);

    /**
     * Input parameter to {IActivityTaskManager#resizeTask} used when the
     * resize is due to a drag action.
     *
     * @hide
     */
    public static final int RESIZE_MODE_USER = RESIZE_MODE_PRESERVE_WINDOW;

    /**
     * Input parameter to {IActivityTaskManager#resizeTask} which indicates
     * that the resize should be performed even if the bounds appears unchanged.
     *
     * @hide
     */
    public static final int RESIZE_MODE_FORCED = (0x1 << 1);

    /**
     * Input parameter to {IActivityTaskManager#resizeTask} which indicates
     * that the resize should preserve the window if possible, and should not be skipped
     * even if the bounds is unchanged. Usually used to force a resizing when a drag action
     * is ending.
     *
     * @hide
     */
    public static final int RESIZE_MODE_USER_FORCED =
            RESIZE_MODE_PRESERVE_WINDOW | RESIZE_MODE_FORCED;

    /**
     * Extra included on intents that contain an EXTRA_INTENT, with options that the contained
     * intent may want to be started with.  Type is Bundle.
     * TODO: remove once the ChooserActivity moves to systemui
     *
     * @hide
     */
    public static final String EXTRA_OPTIONS = "android.app.extra.OPTIONS";

    /**
     * Extra included on intents that contain an EXTRA_INTENT, use this boolean value for the
     * parameter of the same name when starting the contained intent.
     * TODO: remove once the ChooserActivity moves to systemui
     *
     * @hide
     */
    public static final String EXTRA_IGNORE_TARGET_SECURITY =
            "android.app.extra.EXTRA_IGNORE_TARGET_SECURITY";

    /**
     * The minimal size of a display's long-edge needed to support split-screen multi-window.
     */
    public static final int DEFAULT_MINIMAL_SPLIT_SCREEN_DISPLAY_SIZE_DP = 440;

    private ActivityTaskManager() {
    }

    /**
     * @hide
     */
    public static ActivityTaskManager getInstance() {
        throw new RuntimeException("STUB!");
    }

    /**
     * @hide
     */
    public static IActivityTaskManager getService() {
        throw new RuntimeException("STUB!");
    }

    /**
     * Return the maximum number of recents entries that we will maintain and show.
     *
     * @hide
     */
    public static int getMaxRecentTasksStatic() {
        throw new RuntimeException("STUB!");
    }

    /**
     * Return the default limit on the number of recents that an app can make.
     *
     * @hide
     */
    public static int getDefaultAppRecentsLimitStatic() {
        throw new RuntimeException("STUB!");
    }

    /**
     * Return the maximum limit on the number of recents that an app can make.
     *
     * @hide
     */
    public static int getMaxAppRecentsLimitStatic() {
        throw new RuntimeException("STUB!");
    }

    /**
     * Returns true if the system supports at least one form of multi-window.
     * E.g. freeform, split-screen, picture-in-picture.
     */
    public static boolean supportsMultiWindow(Context context) {
        throw new RuntimeException("STUB!");
    }

    /**
     * Returns {@code true} if the display the context is associated with supports split screen
     * multi-window.
     *
     * @throws UnsupportedOperationException if the supplied {Context} is not associated with
     *                                       a display.
     */
    public static boolean supportsSplitScreenMultiWindow(Context context) {
        throw new RuntimeException("STUB!");
    }

    /**
     * @return whether the UI mode of the given config supports error dialogs (ANR, crash, etc).
     * @hide
     */
    public static boolean currentUiModeSupportsErrorDialogs(@NonNull Configuration config) {
        throw new RuntimeException("STUB!");
    }

    /**
     * @return whether the current UI mode supports error dialogs (ANR, crash, etc).
     */
    public static boolean currentUiModeSupportsErrorDialogs(@NonNull Context context) {
        throw new RuntimeException("STUB!");
    }

    /**
     * @return max allowed number of actions in picture-in-picture mode.
     */
    public static int getMaxNumPictureInPictureActions(@NonNull Context context) {
        throw new RuntimeException("STUB!");
    }

    /**
     * Removes root tasks in the windowing modes from the system if they are of activity type
     * ACTIVITY_TYPE_STANDARD or ACTIVITY_TYPE_UNDEFINED
     */
    public void removeRootTasksInWindowingModes(@NonNull int[] windowingModes) {
        throw new RuntimeException("STUB!");
    }

    /**
     * Removes root tasks of the activity types from the Default TDA of all displays.
     */
    public void removeRootTasksWithActivityTypes(@NonNull int[] activityTypes) {
        throw new RuntimeException("STUB!");
    }

    /**
     * Removes all visible recent tasks from the system.
     *
     * @hide
     */
    public void removeAllVisibleRecentTasks() {
        throw new RuntimeException("STUB!");
    }

    /**
     * Notify the server that splash screen of the given task has been copied"
     *
     * @param taskId     Id of task to handle the material to reconstruct the splash screen view.
     * @param parcelable Used to reconstruct the view, null means the surface is un-copyable.
     * @hide
     */
    public void onSplashScreenViewCopyFinished(int taskId,
                                               @Nullable SplashScreenViewParcelable parcelable) {
        throw new RuntimeException("STUB!");
    }

    /**
     * Start to enter lock task mode for given task by system(UI).
     *
     * @param taskId Id of task to lock.
     */
    public void startSystemLockTaskMode(int taskId) {
        throw new RuntimeException("STUB!");
    }

    /**
     * Stop lock task mode by system(UI).
     */
    public void stopSystemLockTaskMode() {
        throw new RuntimeException("STUB!");
    }

    /**
     * Move task to root task with given id.
     *
     * @param taskId     Id of the task to move.
     * @param rootTaskId Id of the rootTask for task moving.
     * @param toTop      Whether the given task should shown to top of stack.
     */
    public void moveTaskToRootTask(int taskId, int rootTaskId, boolean toTop) {
        throw new RuntimeException("STUB!");
    }

    /**
     * Resize task to given bounds.
     *
     * @param taskId Id of task to resize.
     * @param bounds Bounds to resize task.
     */
    public void resizeTask(int taskId, Rect bounds) {
        throw new RuntimeException("STUB!");
    }

    /**
     * Clears launch params for the given package.
     *
     * @param packageNames the names of the packages of which the launch params are to be cleared
     */
    public void clearLaunchParamsForPackages(List<String> packageNames) {
        throw new RuntimeException("STUB!");
    }

    /**
     * @return List of running tasks.
     * @hide
     */
    public List<ActivityManager.RunningTaskInfo> getTasks(int maxNum) {
        throw new RuntimeException("STUB!");
    }

    /**
     * @return List of running tasks that can be filtered by visibility in recents.
     * @hide
     */
    public List<ActivityManager.RunningTaskInfo> getTasks(
            int maxNum, boolean filterOnlyVisibleRecents) {
        throw new RuntimeException("STUB!");
    }

    /**
     * @return List of running tasks that can be filtered by visibility in recents and keep intent
     * extra.
     * @hide
     */
    public List<ActivityManager.RunningTaskInfo> getTasks(
            int maxNum, boolean filterOnlyVisibleRecents, boolean keepIntentExtra) {
        return getTasks(maxNum, filterOnlyVisibleRecents, keepIntentExtra, INVALID_DISPLAY);
    }

    /**
     * @param displayId the target display id, or {INVALID_DISPLAY} not to filter by displayId
     * @return List of running tasks that can be filtered by visibility and displayId in recents
     * and keep intent extra.
     * @hide
     */
    public List<ActivityManager.RunningTaskInfo> getTasks(
            int maxNum, boolean filterOnlyVisibleRecents, boolean keepIntentExtra, int displayId) {
        throw new RuntimeException("STUB!");
    }

    /**
     * @return List of recent tasks.
     * @hide
     */
    public List<ActivityManager.RecentTaskInfo> getRecentTasks(
            int maxNum, int flags, int userId) {
        throw new RuntimeException("STUB!");
    }

    /**
     * @hide
     */
    public void registerTaskStackListener(TaskStackListener listener) {
        throw new RuntimeException("STUB!");
    }

    /**
     * @hide
     */
    public void unregisterTaskStackListener(TaskStackListener listener) {
        throw new RuntimeException("STUB!");
    }

    /**
     * @hide
     */
    public Rect getTaskBounds(int taskId) {
        throw new RuntimeException("STUB!");
    }

    /**
     * Registers remote animations for a display.
     *
     * @hide
     */
    public void registerRemoteAnimationsForDisplay(
            int displayId, RemoteAnimationDefinition definition) {
        throw new RuntimeException("STUB!");
    }

    /**
     * @hide
     */
    public boolean isInLockTaskMode() {
        throw new RuntimeException("STUB!");
    }

    /**
     * Removes task by a given taskId
     */
    public boolean removeTask(int taskId) {
        throw new RuntimeException("STUB!");
    }

    /**
     * Detaches the navigation bar from the app it was attached to during a transition.
     *
     * @hide
     */
    public void detachNavigationBarFromApp(@NonNull IBinder transition) {
        throw new RuntimeException("STUB!");
    }

    /**
     * Update the list of packages allowed in lock task mode.
     */
    public void updateLockTaskPackages(@NonNull Context context, @NonNull String[] packages) {
        throw new RuntimeException("STUB!");
    }

    /**
     * Information you can retrieve about a root task in the system.
     *
     * @hide
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    public static class RootTaskInfo extends TaskInfo {
        public Rect bounds = new Rect();
        public int[] childTaskIds;
        public String[] childTaskNames;
        public Rect[] childTaskBounds;
        public int[] childTaskUserIds;
        public boolean visible;
        // Index of the stack in the display's stack list, can be used for comparison of stack order
        public int position;

        public RootTaskInfo() {
        }
    }

}
