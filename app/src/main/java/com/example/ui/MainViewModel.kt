package com.example.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class Screen {
    Splash,
    Wizard,
    Dashboard
}

data class GroupScore(
    val group: GroupEntity,
    val totalPoints: Int,
    val goldCount: Int,
    val silverCount: Int,
    val bronzeCount: Int
)

data class StudentScore(
    val student: StudentEntity,
    val group: GroupEntity?,
    val totalPoints: Int,
    val firstCount: Int,
    val secondCount: Int,
    val thirdCount: Int
)

class MainViewModel(private val repository: AppRepository, private val context: Context) : ViewModel() {

    private val prefs: SharedPreferences = context.getSharedPreferences("scoreboard_prefs", Context.MODE_PRIVATE)

    // Admin state flows
    private val _isAdminMode = MutableStateFlow(prefs.getBoolean("is_admin_mode", false))
    val isAdminMode: StateFlow<Boolean> = _isAdminMode.asStateFlow()

    private val _adminPin = MutableStateFlow(prefs.getString("admin_pin", "1234") ?: "1234")
    val adminPin: StateFlow<String> = _adminPin.asStateFlow()

    private val _syncKey = MutableStateFlow(prefs.getString("sync_key", "") ?: "")
    val syncKey: StateFlow<String> = _syncKey.asStateFlow()

    private val _webcastUrl = MutableStateFlow(prefs.getString("webcast_url", "") ?: "")
    val webcastUrl: StateFlow<String> = _webcastUrl.asStateFlow()

    private val _isCloudAutoSync = MutableStateFlow(prefs.getBoolean("cloud_auto_sync", false))
    val isCloudAutoSync: StateFlow<Boolean> = _isCloudAutoSync.asStateFlow()

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _visitorCount = MutableStateFlow<Int?>(null)
    val visitorCount: StateFlow<Int?> = _visitorCount.asStateFlow()

    fun setAdminMode(enabled: Boolean) {
        _isAdminMode.value = enabled
        prefs.edit().putBoolean("is_admin_mode", enabled).apply()
    }

    fun updateAdminPin(pin: String) {
        _adminPin.value = pin
        prefs.edit().putString("admin_pin", pin).apply()
    }

    fun setSyncKey(key: String) {
        _syncKey.value = key
        prefs.edit().putString("sync_key", key).apply()
    }

    fun setWebcastUrl(url: String) {
        _webcastUrl.value = url.trim()
        prefs.edit().putString("webcast_url", url.trim()).apply()
    }

    fun setCloudAutoSync(enabled: Boolean) {
        _isCloudAutoSync.value = enabled
        prefs.edit().putBoolean("cloud_auto_sync", enabled).apply()
    }

    fun fetchVisitorCount() {
        val currentKey = syncKey.value
        if (currentKey.isEmpty()) {
            _visitorCount.value = null
            return
        }
        val client = okhttp3.OkHttpClient()
        val request = okhttp3.Request.Builder()
            .url("https://api.counterapi.dev/v1/toppest/$currentKey")
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                // Fail silently
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val bodyStr = response.body?.string()
                response.close()
                if (response.isSuccessful && !bodyStr.isNullOrEmpty()) {
                    try {
                        val json = org.json.JSONObject(bodyStr)
                        val count = json.optInt("value", 0)
                        _visitorCount.value = count
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        })
    }

    fun publishToCloud(onSuccess: (String) -> Unit = {}, onFailure: (String) -> Unit = {}) {
        val config = appConfig.value
        val madrasa = config?.madrasaName ?: "Darul Huda Islamic Complex"
        val event = config?.eventName ?: "മദ്രസ കലോത്സവം"
        val groups = allGroups.value
        val students = allStudents.value
        val competitions = allCompetitions.value

        _isSyncing.value = true
        _syncStatus.value = "Uploading scoreboard to live webcast..."

        val json = CloudSyncManager.serializeScoreboard(madrasa, event, groups, students, competitions)
        val currentKey = syncKey.value

        CloudSyncManager.uploadToCloud(
            jsonPayload = json,
            existingKey = if (currentKey.isNotEmpty()) currentKey else null,
            onSuccess = { newKey ->
                _isSyncing.value = false
                _syncStatus.value = "Webcast updated successfully!"
                setSyncKey(newKey)
                onSuccess(newKey)
            },
            onFailure = { error ->
                _isSyncing.value = false
                _syncStatus.value = "Failed to update webcast: $error"
                onFailure(error)
            }
        )
    }

    fun pullFromCloud(keyToUse: String, onSuccess: () -> Unit = {}, onFailure: (String) -> Unit = {}) {
        if (keyToUse.isEmpty()) {
            onFailure("Sync Key cannot be empty.")
            return
        }

        _isSyncing.value = true
        _syncStatus.value = "Fetching live webcast data..."

        CloudSyncManager.downloadFromCloud(
            syncKey = keyToUse,
            onSuccess = { syncedData ->
                viewModelScope.launch {
                    try {
                        // 1. Save config
                        repository.saveAppConfig(
                            AppConfigEntity(
                                id = 1,
                                madrasaName = syncedData.madrasaName,
                                eventName = syncedData.eventName,
                                isWizardCompleted = true
                            )
                        )

                        // 2. Clear old database values in order
                        // Delete all competitions
                        allCompetitions.value.forEach { repository.deleteCompetition(it) }
                        // Delete all groups (which will cascade delete students!)
                        allGroups.value.forEach { repository.deleteGroup(it) }

                        // 3. Insert synced data back maintaining exact original IDs!
                        syncedData.groups.forEach { repository.insertGroup(it) }
                        syncedData.students.forEach { repository.insertStudent(it) }
                        syncedData.competitions.forEach { repository.insertCompetition(it) }

                        setSyncKey(keyToUse)
                        _isSyncing.value = false
                        _syncStatus.value = "Live webcast downloaded successfully!"
                        onSuccess()
                    } catch (e: Exception) {
                        _isSyncing.value = false
                        _syncStatus.value = "Failed to import database: ${e.message}"
                        onFailure(e.message ?: "Import database exception")
                    }
                }
            },
            onFailure = { error ->
                _isSyncing.value = false
                _syncStatus.value = "Sync download error: $error"
                onFailure(error)
            }
        )
    }

    fun triggerAutoSyncIfNeeded() {
        if (isCloudAutoSync.value && syncKey.value.isNotEmpty()) {
            publishToCloud()
        }
    }

    // Screens & Navigation state
    private val _currentScreen = MutableStateFlow(Screen.Splash)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Wizard step state
    private val _wizardStep = MutableStateFlow(1)
    val wizardStep: StateFlow<Int> = _wizardStep.asStateFlow()

    // Temp Wizard data
    val wizardMadrasaName = MutableStateFlow("")
    val wizardEventName = MutableStateFlow("")
    val wizardGroups = MutableStateFlow<List<GroupEntity>>(emptyList())
    val wizardStudents = MutableStateFlow<List<StudentEntity>>(emptyList())
    val wizardCompetitions = MutableStateFlow<List<CompetitionEntity>>(emptyList())

    // Database Flows
    val appConfig: StateFlow<AppConfigEntity?> = repository.appConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allGroups: StateFlow<List<GroupEntity>> = repository.allGroups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allStudents: StateFlow<List<StudentEntity>> = repository.allStudents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCompetitions: StateFlow<List<CompetitionEntity>> = repository.allCompetitions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live Calculations
    val groupScores: StateFlow<List<GroupScore>> = combine(
        allGroups,
        allStudents,
        allCompetitions
    ) { groups, students, competitions ->
        val studentMap = students.associateBy { it.id }
        val groupMap = groups.associateBy { it.id }

        val scoreMap = groups.associateWith { 0 }.toMutableMap()
        val goldMap = groups.associateWith { 0 }.toMutableMap()
        val silverMap = groups.associateWith { 0 }.toMutableMap()
        val bronzeMap = groups.associateWith { 0 }.toMutableMap()

        for (comp in competitions) {
            comp.firstStudentId?.let { sId ->
                studentMap[sId]?.let { student ->
                    groupMap[student.groupId]?.let { group ->
                        val marks = comp.firstMarks ?: 5
                        scoreMap[group] = (scoreMap[group] ?: 0) + marks
                        goldMap[group] = (goldMap[group] ?: 0) + 1
                    }
                }
            }
            comp.secondStudentId?.let { sId ->
                studentMap[sId]?.let { student ->
                    groupMap[student.groupId]?.let { group ->
                        val marks = comp.secondMarks ?: 3
                        scoreMap[group] = (scoreMap[group] ?: 0) + marks
                        silverMap[group] = (silverMap[group] ?: 0) + 1
                    }
                }
            }
            comp.thirdStudentId?.let { sId ->
                studentMap[sId]?.let { student ->
                    groupMap[student.groupId]?.let { group ->
                        val marks = comp.thirdMarks ?: 1
                        scoreMap[group] = (scoreMap[group] ?: 0) + marks
                        bronzeMap[group] = (bronzeMap[group] ?: 0) + 1
                    }
                }
            }
        }

        groups.map { group ->
            GroupScore(
                group = group,
                totalPoints = scoreMap[group] ?: 0,
                goldCount = goldMap[group] ?: 0,
                silverCount = silverMap[group] ?: 0,
                bronzeCount = bronzeMap[group] ?: 0
            )
        }.sortedByDescending { it.totalPoints }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val studentScores: StateFlow<List<StudentScore>> = combine(
        allGroups,
        allStudents,
        allCompetitions
    ) { groups, students, competitions ->
        val studentMap = students.associateBy { it.id }
        val groupMap = groups.associateBy { it.id }

        val scoreMap = students.associateWith { 0 }.toMutableMap()
        val firstMap = students.associateWith { 0 }.toMutableMap()
        val secondMap = students.associateWith { 0 }.toMutableMap()
        val thirdMap = students.associateWith { 0 }.toMutableMap()

        for (comp in competitions) {
            comp.firstStudentId?.let { sId ->
                studentMap[sId]?.let { student ->
                    val marks = comp.firstMarks ?: 5
                    scoreMap[student] = (scoreMap[student] ?: 0) + marks
                    firstMap[student] = (firstMap[student] ?: 0) + 1
                }
            }
            comp.secondStudentId?.let { sId ->
                studentMap[sId]?.let { student ->
                    val marks = comp.secondMarks ?: 3
                    scoreMap[student] = (scoreMap[student] ?: 0) + marks
                    secondMap[student] = (secondMap[student] ?: 0) + 1
                }
            }
            comp.thirdStudentId?.let { sId ->
                studentMap[sId]?.let { student ->
                    val marks = comp.thirdMarks ?: 1
                    scoreMap[student] = (scoreMap[student] ?: 0) + marks
                    thirdMap[student] = (thirdMap[student] ?: 0) + 1
                }
            }
        }

        students.map { student ->
            StudentScore(
                student = student,
                group = groupMap[student.groupId],
                totalPoints = scoreMap[student] ?: 0,
                firstCount = firstMap[student] ?: 0,
                secondCount = secondMap[student] ?: 0,
                thirdCount = thirdMap[student] ?: 0
            )
        }.filter { it.totalPoints > 0 }.sortedByDescending { it.totalPoints }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Run checks to decide start screen
        viewModelScope.launch {
            repository.appConfig.collect { config ->
                if (config != null && config.isWizardCompleted) {
                    _currentScreen.value = Screen.Dashboard
                } else {
                    _currentScreen.value = Screen.Wizard
                }
            }
        }

        viewModelScope.launch {
            _syncKey.collect { key ->
                if (key.isNotEmpty()) {
                    fetchVisitorCount()
                } else {
                    _visitorCount.value = null
                }
            }
        }

        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(15000)
                if (_syncKey.value.isNotEmpty()) {
                    fetchVisitorCount()
                }
            }
        }
    }

    // Navigation and Wizard controllers
    fun setScreen(screen: Screen) {
        _currentScreen.value = screen
    }

    fun nextWizardStep() {
        if (_wizardStep.value < 4) {
            _wizardStep.value += 1
        }
    }

    fun prevWizardStep() {
        if (_wizardStep.value > 1) {
            _wizardStep.value -= 1
        }
    }

    // Config Actions
    fun updateConfig(madrasaName: String, eventName: String) {
        viewModelScope.launch {
            repository.saveAppConfig(AppConfigEntity(madrasaName = madrasaName, eventName = eventName, isWizardCompleted = true))
            _currentScreen.value = Screen.Dashboard
        }
    }

    fun completeWizard(madrasaName: String, eventName: String, groups: List<GroupEntity>, students: List<StudentEntity>, competitions: List<CompetitionEntity>) {
        viewModelScope.launch {
            // Save Madrasa Configuration
            repository.saveAppConfig(AppConfigEntity(madrasaName = madrasaName, eventName = eventName, isWizardCompleted = true))
            
            // Save Groups
            for (g in groups) {
                repository.insertGroup(g)
            }
            
            // Flow will update groups in background, but since we need IDs for foreign keys, let's observe
            // Wait, to be safe during setup, we insert groups first and get their generated IDs to insert students.
            // Let's do it sequentially!
        }
    }

    // We can execute direct setup routines to write database sequentially in one transaction block
    fun finalizeWizardSetup() {
        viewModelScope.launch {
            val finalConfig = AppConfigEntity(
                madrasaName = wizardMadrasaName.value,
                eventName = wizardEventName.value,
                isWizardCompleted = true
            )
            repository.saveAppConfig(finalConfig)

            // Dynamic group mapping
            for (g in wizardGroups.value) {
                repository.insertGroup(g)
            }

            // Since we inserted groups, they will be reactively flow-loaded. 
            // In a simple wizard, we can save config and go to dashboard, and users can manage groups/students live,
            // or we save the whole batch. Let's make groups, students, competitions added directly to DB during Wizard steps!
            // That is 100% cleaner and less error-prone!
        }
    }

    // Live Actions (Groups)
    fun addGroup(name: String, colorHex: String) {
        viewModelScope.launch {
            repository.insertGroup(GroupEntity(name = name, colorHex = colorHex))
            triggerAutoSyncIfNeeded()
        }
    }

    fun updateGroup(group: GroupEntity) {
        viewModelScope.launch {
            repository.updateGroup(group)
            triggerAutoSyncIfNeeded()
        }
    }

    fun deleteGroup(group: GroupEntity) {
        viewModelScope.launch {
            repository.deleteGroup(group)
            triggerAutoSyncIfNeeded()
        }
    }

    // Live Actions (Students)
    fun addStudent(name: String, groupId: Int) {
        viewModelScope.launch {
            repository.insertStudent(StudentEntity(name = name, groupId = groupId))
            triggerAutoSyncIfNeeded()
        }
    }

    fun deleteStudent(student: StudentEntity) {
        viewModelScope.launch {
            repository.deleteStudent(student)
            triggerAutoSyncIfNeeded()
        }
    }

    // Bulk Importer
    fun importStudentsFromCsvText(csvText: String, defaultGroupId: Int? = null) {
        viewModelScope.launch {
            val lines = csvText.lines()
            val importedStudents = mutableListOf<StudentEntity>()
            val currentGroups = allGroups.value
            val groupMap = currentGroups.associateBy { it.name.lowercase().trim() }

            for (line in lines) {
                val trimmedLine = line.trim()
                if (trimmedLine.isEmpty()) continue

                val parts = trimmedLine.split(Regex("[,;\\t]"))
                if (parts.isNotEmpty()) {
                    val name = parts[0].trim()
                    if (name.isEmpty()) continue

                    var groupId: Int? = null
                    if (parts.size > 1) {
                        val groupName = parts[1].trim().lowercase()
                        groupId = groupMap[groupName]?.id
                    }

                    val finalGroupId = groupId ?: defaultGroupId
                    if (finalGroupId != null) {
                        importedStudents.add(StudentEntity(name = name, groupId = finalGroupId))
                    }
                }
            }
            if (importedStudents.isNotEmpty()) {
                repository.insertStudents(importedStudents)
                triggerAutoSyncIfNeeded()
            }
        }
    }

    // Live Actions (Competitions)
    fun addCompetition(name: String) {
        viewModelScope.launch {
            repository.insertCompetition(CompetitionEntity(name = name))
            triggerAutoSyncIfNeeded()
        }
    }

    fun updateCompetition(competition: CompetitionEntity) {
        viewModelScope.launch {
            repository.updateCompetition(competition)
            triggerAutoSyncIfNeeded()
        }
    }

    fun deleteCompetition(competition: CompetitionEntity) {
        viewModelScope.launch {
            repository.deleteCompetition(competition)
            triggerAutoSyncIfNeeded()
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            // Re-initialize configuration
            repository.saveAppConfig(AppConfigEntity(isWizardCompleted = false))
            
            // Delete all groups (cascades to students due to foreign key)
            for (g in allGroups.value) {
                repository.deleteGroup(g)
            }
            // Delete all competitions
            for (c in allCompetitions.value) {
                repository.deleteCompetition(c)
            }
            
            // Clear wizard values
            wizardMadrasaName.value = ""
            wizardEventName.value = ""
            wizardGroups.value = emptyList()
            wizardStudents.value = emptyList()
            wizardCompetitions.value = emptyList()
            
            _wizardStep.value = 1
            _currentScreen.value = Screen.Wizard
        }
    }
}

class MainViewModelFactory(private val repository: AppRepository, private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
