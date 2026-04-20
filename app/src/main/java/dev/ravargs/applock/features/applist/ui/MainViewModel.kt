package dev.ravargs.applock.features.applist.ui

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ravargs.applock.data.repository.AppLockRepository
import dev.ravargs.applock.features.applist.domain.AppSearchManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.ravargs.applock.services.AppLockManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

@OptIn(FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appSearchManager = AppSearchManager(application)
    private val appLockRepository = AppLockRepository(application)

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _allApps = MutableStateFlow<Set<ApplicationInfo>>(emptySet())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _lockedApps = MutableStateFlow<Set<String>>(emptySet())

    private val _debouncedQuery = MutableStateFlow("")

    private val _appTimeLimits = MutableStateFlow<Map<String, Int>>(emptyMap())
    val appTimeLimits: StateFlow<Map<String, Int>> = _appTimeLimits.asStateFlow()

    val lockedAppsFlow: StateFlow<List<ApplicationInfo>> =
        combine(_allApps, _lockedApps, _debouncedQuery) { apps, locked, query ->
            apps.filter { it.packageName in locked }
                .filter { it.matchesQuery(query) }
                .sortedBy { it.loadLabel(getApplication<Application>().packageManager).toString() }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val unlockedAppsFlow: StateFlow<List<ApplicationInfo>> =
        combine(_allApps, _lockedApps, _debouncedQuery) { apps, locked, query ->
            apps.filterNot { it.packageName in locked }
                .filter { it.matchesQuery(query) }
                .sortedBy { it.loadLabel(getApplication<Application>().packageManager).toString() }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private fun ApplicationInfo.matchesQuery(query: String): Boolean {
        if (query.isBlank()) return true
        return loadLabel(getApplication<Application>().packageManager).toString()
            .contains(query, ignoreCase = true)
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.action?.let { action ->
                if (action == Intent.ACTION_PACKAGE_ADDED || 
                    action == Intent.ACTION_PACKAGE_REMOVED || 
                    action == Intent.ACTION_PACKAGE_REPLACED) {
                    loadAllApplications()
                }
            }
        }
    }

    init {
        loadAllApplications()
        loadLockedApps()
        loadAppTimeLimits()

        viewModelScope.launch {
            _searchQuery
                .debounce(100L)
                .collect { query ->
                    _debouncedQuery.value = query
                }
        }

        viewModelScope.launch {
            appLockRepository.lockedAppsFlow.collect { locked ->
                _lockedApps.value = locked
                loadAppTimeLimits()
            }
        }

        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        application.registerReceiver(packageReceiver, intentFilter)
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unregisterReceiver(packageReceiver)
    }

    private fun loadAllApplications() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val apps = withContext(Dispatchers.IO) {
                    appSearchManager.loadApps(true)
                }
                _allApps.value = apps
            } catch (e: Exception) {
                _allApps.value = emptySet()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadLockedApps() {
        _lockedApps.value = appLockRepository.getLockedApps()
    }

    private fun loadAppTimeLimits() {
        val limits = mutableMapOf<String, Int>()
        _lockedApps.value.forEach { pkg ->
            val limit = appLockRepository.getTimeLimit(pkg)
            if (limit > 0) {
                limits[pkg] = limit
            }
        }
        _appTimeLimits.value = limits
    }

    fun setTimeLimit(packageName: String, minutes: Int) {
        appLockRepository.setTimeLimit(packageName, minutes)
        AppLockManager.forgetAppUnlock(packageName)
    }

    fun getTimeLimit(packageName: String): Int {
        return appLockRepository.getTimeLimit(packageName)
    }

    fun lockApps(packageNames: List<String>) {
        appLockRepository.addMultipleLockedApps(packageNames.toSet())
        packageNames.forEach { AppLockManager.forgetAppUnlock(it) }
    }

    fun unlockApp(packageName: String) {
        appLockRepository.removeLockedApp(packageName)
        AppLockManager.forgetAppUnlock(packageName)
    }
}
