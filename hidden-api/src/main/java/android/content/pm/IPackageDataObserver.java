package android.content.pm;

/**
 * API for package data change related callbacks from the Package Manager.
 * Some usage scenarios include deletion of cache directory, generate
 * statistics related to code, data, cache usage(TODO)
 * {@hide}
 */
public interface IPackageDataObserver {
    void onRemoveCompleted(String packageName, boolean succeeded);
}
