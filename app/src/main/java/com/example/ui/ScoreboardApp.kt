package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreboardApp(viewModel: MainViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
            },
            label = "ScreenTransition"
        ) { screen ->
            when (screen) {
                Screen.Splash -> SplashScreen {
                    // Decide where to go
                    val config = viewModel.appConfig.value
                    if (config != null && config.isWizardCompleted) {
                        viewModel.setScreen(Screen.Dashboard)
                    } else {
                        viewModel.setScreen(Screen.Wizard)
                    }
                }
                Screen.Wizard -> WizardScreen(viewModel)
                Screen.Dashboard -> DashboardScreen(viewModel)
            }
        }
    }
}

// 1. SPLASH SCREEN
@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    val scope = rememberCoroutineScope()
    var animateStart by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        animateStart = true
        delay(2200) // Beautiful splash duration
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(SlateDarkBackground, Color(0xFF070B14))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // Islamic Geometric Dome Canvas Drawing
            Canvas(
                modifier = Modifier
                    .size(140.dp)
                    .animateContentSize()
            ) {
                val path = Path().apply {
                    val w = size.width
                    val h = size.height
                    moveTo(w / 2, 10f)
                    cubicTo(w / 4, h / 3, 5f, h * 2 / 3, w / 2, h - 10f)
                    cubicTo(w - 5f, h * 2 / 3, w * 3 / 4, h / 3, w / 2, 10f)
                }
                drawPath(path, Brush.radialGradient(listOf(PremiumGold, DarkGold)))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "مدرسة മത്സര സ്കോർബോർഡ്",
                fontSize = 20.sp,
                color = PremiumGold,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "MALAYALAM COMPETITION\nSCOREBOARD",
                fontSize = 24.sp,
                color = TextPrimary,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                lineHeight = 30.sp
            )

            Spacer(modifier = Modifier.height(30.dp))

            CircularProgressIndicator(
                color = PremiumGold,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

// 2. WIZARD SCREEN (4-Step Setup Flow)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardScreen(viewModel: MainViewModel) {
    val step by viewModel.wizardStep.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Local state variables for Wizard setup
    var madrasaName by remember { mutableStateOf("") }
    var eventName by remember { mutableStateOf("") }
    
    val groups by viewModel.allGroups.collectAsStateWithLifecycle()
    val students by viewModel.allStudents.collectAsStateWithLifecycle()
    val competitions by viewModel.allCompetitions.collectAsStateWithLifecycle()

    var showGroupDialog by remember { mutableStateOf(false) }
    var showStudentDialog by remember { mutableStateOf(false) }
    var showCompDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Setup Wizard", color = PremiumGold, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ImmersiveHeaderBlue,
                    titleContentColor = TextPrimary
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                color = ImmersiveNavBlue,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (step > 1) {
                        OutlinedButton(
                            onClick = { viewModel.prevWizardStep() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PremiumGold),
                            border = BorderStroke(1.dp, PremiumGold),
                            modifier = Modifier.testTag("wizard_prev_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Back")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Button(
                        onClick = {
                            if (step == 1) {
                                if (madrasaName.trim().isEmpty() || eventName.trim().isEmpty()) {
                                    Toast.makeText(context, "Please enter all fields", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.nextWizardStep()
                                }
                            } else if (step == 2) {
                                if (groups.isEmpty()) {
                                    Toast.makeText(context, "Please add at least one group", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.nextWizardStep()
                                }
                            } else if (step == 3) {
                                viewModel.nextWizardStep()
                            } else if (step == 4) {
                                if (competitions.isEmpty()) {
                                    Toast.makeText(context, "Please add at least one competition", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.updateConfig(madrasaName, eventName)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = Color(0xFF0F172A)),
                        modifier = Modifier.testTag("wizard_next_button")
                    ) {
                        Text(if (step == 4) "Finish Setup" else "Next")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(ImmersiveBackground)
                .padding(16.dp)
        ) {
            // Wizard step indicator
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = ImmersiveHeaderBlue),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.1f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Step $step of 4",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PremiumGold
                        )
                        val stepTitle = when (step) {
                            1 -> "General Info"
                            2 -> "Groups Setup"
                            3 -> "Student Setup"
                            else -> "Competition Setup"
                        }
                        Text(
                            text = stepTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PremiumGold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (step / 4f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = PremiumGold,
                        trackColor = TextSecondary.copy(alpha = 0.15f)
                    )
                }
            }

            // Step Content
            Box(modifier = Modifier.weight(1f)) {
                when (step) {
                    1 -> Column {
                        Text(
                            "Enter Event Details",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = PremiumGold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Setup your Madrasa name and the event name to show on screens and final posters.",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        OutlinedTextField(
                            value = madrasaName,
                            onValueChange = { madrasaName = it },
                            label = { Text("Madrasa / Organization Name") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = PremiumGold,
                                unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                                cursorColor = PremiumGold,
                                focusedContainerColor = ImmersiveHeaderBlue,
                                unfocusedContainerColor = ImmersiveHeaderBlue
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("madrasa_name_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = eventName,
                            onValueChange = { eventName = it },
                            label = { Text("Event Name (e.g. Arts Fest 2026)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = PremiumGold,
                                unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                                cursorColor = PremiumGold,
                                focusedContainerColor = ImmersiveHeaderBlue,
                                unfocusedContainerColor = ImmersiveHeaderBlue
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("event_name_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = ImmersiveHeaderBlue),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, PremiumGold.copy(alpha = 0.15f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Created by Mohammed Basith Saqafi",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = PremiumGold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "Malayalam Competition Scoreboard v1.0",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                    2 -> Column {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Add Groups",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = PremiumGold
                            )
                            Button(
                                onClick = { showGroupDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = Color(0xFF0F172A))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Group", fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        if (groups.isEmpty()) {
                            EmptyStatePlaceholder(
                                icon = Icons.Default.Groups,
                                message = "No groups added yet.\nCreate teams like 'Safa', 'Marwa', etc."
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(groups) { group ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = ImmersiveHeaderBlue),
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(2.dp, Color(android.graphics.Color.parseColor(group.colorHex)))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(12.dp)
                                                .fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                group.name,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(android.graphics.Color.parseColor(group.colorHex)),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = { viewModel.deleteGroup(group) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    3 -> Column {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Add Students",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = PremiumGold
                            )
                            Button(
                                onClick = { showStudentDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = Color(0xFF0F172A))
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = "Add")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Student", fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        if (students.isEmpty()) {
                            EmptyStatePlaceholder(
                                icon = Icons.Default.People,
                                message = "No students added yet.\nImport easily via Excel/CSV Copy-Paste!"
                            )
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(students) { student ->
                                    val group = groups.find { it.id == student.groupId }
                                    val gColor = group?.colorHex ?: "#94A3B8"
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = ImmersiveHeaderBlue),
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.1f))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(12.dp)
                                                .fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(student.name, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                                Text(
                                                    group?.name ?: "No Group",
                                                    color = Color(android.graphics.Color.parseColor(gColor)),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            IconButton(onClick = { viewModel.deleteStudent(student) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    4 -> Column {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Competitions Setup",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = PremiumGold
                            )
                            Button(
                                onClick = { showCompDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = Color(0xFF0F172A))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Comp", fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        if (competitions.isEmpty()) {
                            EmptyStatePlaceholder(
                                icon = Icons.Default.EmojiEvents,
                                message = "No competitions added yet.\nAdd events like Qur'an Recitation, Quiz, Speech, etc."
                            )
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(competitions) { comp ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = ImmersiveHeaderBlue),
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.1f))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(12.dp)
                                                .fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(comp.name, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                            IconButton(onClick = { viewModel.deleteCompetition(comp) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Setup Dialogs
        if (showGroupDialog) {
            AddGroupDialog(
                onDismiss = { showGroupDialog = false },
                onAdd = { name, color ->
                    viewModel.addGroup(name, color)
                    showGroupDialog = false
                }
            )
        }

        if (showStudentDialog) {
            AddStudentDialog(
                groups = groups,
                onDismiss = { showStudentDialog = false },
                onAdd = { name, gId ->
                    viewModel.addStudent(name, gId)
                    showStudentDialog = false
                },
                onBulkImport = { csvText, gId ->
                    viewModel.importStudentsFromCsvText(csvText, gId)
                    showStudentDialog = false
                }
            )
        }

        if (showCompDialog) {
            AddCompDialog(
                onDismiss = { showCompDialog = false },
                onAdd = { name ->
                    viewModel.addCompetition(name)
                    showCompDialog = false
                }
            )
        }
    }
}

// 3. DASHBOARD (MAIN APPLICATION VIEW WITH 5 TABS)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: MainViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val config by viewModel.appConfig.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showAdminPanelDialog by remember { mutableStateOf(false) }
    val isAdminMode by viewModel.isAdminMode.collectAsStateWithLifecycle()
    val syncKey by viewModel.syncKey.collectAsStateWithLifecycle()
    val webcastUrl by viewModel.webcastUrl.collectAsStateWithLifecycle()

    LaunchedEffect(isAdminMode) {
        if (!isAdminMode && selectedTab != 0 && selectedTab != 3) {
            selectedTab = 0
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            (config?.madrasaName ?: "Darul Huda Islamic Complex").uppercase(),
                            color = PremiumGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            config?.eventName ?: "മദ്രസ കലോത്സവം",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (syncKey.isNotEmpty()) {
                                CloudSyncManager.shareLiveDashboardLink(
                                    context = context,
                                    syncKey = syncKey,
                                    madrasaName = config?.madrasaName ?: "Madrasa",
                                    eventName = config?.eventName ?: "Arts Fest",
                                    customUrl = webcastUrl
                                )
                            } else {
                                showAdminPanelDialog = true
                                Toast.makeText(context, "തത്സമയ ലിങ്ക് ലഭിക്കാൻ Sync Key ക്രിയേറ്റ് ചെയ്യുക", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(PremiumGold.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share Web Link for Students",
                            tint = PremiumGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { showAboutDialog = true },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(TextSecondary.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "About App",
                            tint = PremiumGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { showAdminPanelDialog = true },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(TextSecondary.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            Icons.Default.Cloud,
                            contentDescription = "Admin & Webcast Sync",
                            tint = PremiumGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (isAdminMode) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { showResetDialog = true },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(TextSecondary.copy(alpha = 0.08f))
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Reset",
                                tint = PremiumGold,
                                modifier = Modifier.size(20.dp)
                             )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ImmersiveHeaderBlue,
                    titleContentColor = TextPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = ImmersiveNavBlue,
                contentColor = TextSecondary,
                modifier = Modifier.navigationBarsPadding(),
                tonalElevation = 8.dp
            ) {
                val items = if (isAdminMode) {
                    listOf(
                        Triple("Leaderboard", Icons.Default.Leaderboard, 0),
                        Triple("Groups", Icons.Default.Groups, 1),
                        Triple("Students", Icons.Default.People, 2),
                        Triple("Prizes", Icons.Default.EmojiEvents, 3)
                    )
                } else {
                    listOf(
                        Triple("Leaderboard", Icons.Default.Leaderboard, 0),
                        Triple("Prizes", Icons.Default.EmojiEvents, 3)
                    )
                }
                items.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 10.sp, maxLines = 1, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF0F172A),
                            selectedTextColor = PremiumGold,
                            indicatorColor = PremiumGold,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(ImmersiveBackground)
        ) {
            when (selectedTab) {
                0 -> LeaderboardTab(viewModel)
                1 -> GroupsTab(viewModel)
                2 -> StudentsTab(viewModel)
                3 -> CompetitionsTab(viewModel)
            }
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Reset Application Data?") },
                text = { Text("This will permanently delete all configured groups, students, competition results, and settings, returning you to the Setup Wizard. This cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.resetAllData()
                            showResetDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Reset All", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                title = { Text("About Scoreboard App", color = PremiumGold, fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Malayalam Competition Scoreboard",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 16.sp
                        )
                        Text(
                            "An elegant scoreboard and result card platform designed for Madrasas and arts competitions to record and showcase live scores beautifully.",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(TextSecondary.copy(alpha = 0.15f))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Created by:",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                        Text(
                            "Mohammed Basith Saqafi",
                            fontWeight = FontWeight.ExtraBold,
                            color = PremiumGold,
                            fontSize = 16.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showAboutDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = Color.White)
                    ) {
                        Text("Okay")
                    }
                }
            )
        }

        if (showExportDialog) {
            ExportHubDialog(viewModel = viewModel, onDismiss = { showExportDialog = false })
        }

        if (showAdminPanelDialog) {
            AdminSyncPanelDialog(viewModel = viewModel, onDismiss = { showAdminPanelDialog = false })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSyncPanelDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isAdminMode by viewModel.isAdminMode.collectAsStateWithLifecycle()
    val adminPin by viewModel.adminPin.collectAsStateWithLifecycle()
    val syncKey by viewModel.syncKey.collectAsStateWithLifecycle()
    val isCloudAutoSync by viewModel.isCloudAutoSync.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val config by viewModel.appConfig.collectAsStateWithLifecycle()
    val visitorCount by viewModel.visitorCount.collectAsStateWithLifecycle()
    val webcastUrl by viewModel.webcastUrl.collectAsStateWithLifecycle()

    LaunchedEffect(syncKey) {
        if (syncKey.isNotEmpty()) {
            viewModel.fetchVisitorCount()
        }
    }

    val groups by viewModel.allGroups.collectAsStateWithLifecycle()
    val students by viewModel.allStudents.collectAsStateWithLifecycle()
    val competitions by viewModel.allCompetitions.collectAsStateWithLifecycle()

    var pinInput by remember { mutableStateOf("") }
    var pinChangeInput by remember { mutableStateOf("") }
    var importKeyInput by remember { mutableStateOf("") }
    var webcastUrlInput by remember(webcastUrl) { mutableStateOf(webcastUrl) }
    var showLinkGuide by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Cloud, contentDescription = null, tint = PremiumGold)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Admin & Webcast Control", color = PremiumGold, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // PART 1: ADMIN SECURITY LOCK
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = ImmersiveNavBlue),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Security Access", fontWeight = FontWeight.Bold, color = PremiumGold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        if (isAdminMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF22C55E))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Status: Unlocked (Admin)", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = { viewModel.setAdminMode(false) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.15f), contentColor = Color.Red),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Lock", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Change PIN (Current: $adminPin):", fontSize = 11.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = pinChangeInput,
                                    onValueChange = { if (it.length <= 8) pinChangeInput = it },
                                    placeholder = { Text("New PIN", fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = TextPrimary),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedBorderColor = PremiumGold,
                                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.2f),
                                        focusedContainerColor = ImmersiveHeaderBlue,
                                        unfocusedContainerColor = ImmersiveHeaderBlue
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true
                                )
                                Button(
                                    onClick = {
                                        if (pinChangeInput.trim().isNotEmpty()) {
                                            viewModel.updateAdminPin(pinChangeInput.trim())
                                            Toast.makeText(context, "PIN updated to ${pinChangeInput.trim()}", Toast.LENGTH_SHORT).show()
                                            pinChangeInput = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = Color.Black),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("Update", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color.Red)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Status: Locked (Read-Only Mode)", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("To unlock editing features, enter PIN:", fontSize = 11.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = pinInput,
                                    onValueChange = { pinInput = it },
                                    placeholder = { Text("PIN (Default: 1234)", fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = TextPrimary),
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedBorderColor = PremiumGold,
                                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.2f),
                                        focusedContainerColor = ImmersiveHeaderBlue,
                                        unfocusedContainerColor = ImmersiveHeaderBlue
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true
                                )
                                Button(
                                    onClick = {
                                        if (pinInput == adminPin) {
                                            viewModel.setAdminMode(true)
                                            Toast.makeText(context, "Admin access enabled!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Incorrect security PIN!", Toast.LENGTH_SHORT).show()
                                        }
                                        pinInput = ""
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = Color.Black),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("Unlock", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // PART 2: LIVE WEBCAST / SYNC CONTROL
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = ImmersiveNavBlue),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("📡 Scoreboard Live Webcast", fontWeight = FontWeight.Bold, color = PremiumGold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Sync and broadcast your standings onto a live web dashboard automatically. Share with parents and viewers!",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        if (syncKey.isEmpty()) {
                            Text("No active webcast setup:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Spacer(modifier = Modifier.height(8.dp))

                            if (isAdminMode) {
                                Button(
                                    onClick = {
                                        viewModel.publishToCloud(
                                            onSuccess = { key ->
                                                Toast.makeText(context, "Webcast live on key: $key", Toast.LENGTH_LONG).show()
                                            },
                                            onFailure = { err ->
                                                Toast.makeText(context, "Setup failed: $err", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = Color.Black),
                                    enabled = !isSyncing
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Go Live! Create Webcast Key", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            } else {
                                Text("Only an Admin can initialize a live webcast key.", fontSize = 11.sp, color = Color.Red, fontWeight = FontWeight.SemiBold)
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(TextSecondary.copy(alpha = 0.15f))
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Text("Or connect to an existing Sync Key:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = importKeyInput,
                                    onValueChange = { importKeyInput = it },
                                    placeholder = { Text("Paste Sync Key here...", fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = TextPrimary),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedBorderColor = PremiumGold,
                                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.2f),
                                        focusedContainerColor = ImmersiveHeaderBlue,
                                        unfocusedContainerColor = ImmersiveHeaderBlue
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true
                                )
                                Button(
                                    onClick = {
                                        if (importKeyInput.trim().isNotEmpty()) {
                                            viewModel.pullFromCloud(
                                                keyToUse = importKeyInput.trim(),
                                                onSuccess = {
                                                    Toast.makeText(context, "Standings synced successfully!", Toast.LENGTH_LONG).show()
                                                    importKeyInput = ""
                                                },
                                                onFailure = { err ->
                                                    Toast.makeText(context, "Sync failed: $err", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = Color.Black),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(48.dp),
                                    enabled = !isSyncing
                                ) {
                                    Text("Sync", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            // Active Webcast Key
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = ImmersiveHeaderBlue),
                                border = BorderStroke(1.dp, PremiumGold.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("ACTIVE WEBCAST KEY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = PremiumGold, letterSpacing = 1.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(syncKey, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                val clip = android.content.ClipData.newPlainText("Sync Key", syncKey)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "Sync Key Copied!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Key", tint = PremiumGold, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    if (visitorCount != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(PremiumGold.copy(alpha = 0.12f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                                .padding(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.RemoveRedEye,
                                                contentDescription = null,
                                                tint = PremiumGold,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Website Views: $visitorCount",
                                                color = PremiumGold,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (isAdminMode) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Auto-Sync edits", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimary)
                                        Text("Uploads instantly on every score change", fontSize = 10.sp, color = TextSecondary)
                                    }
                                    Switch(
                                        checked = isCloudAutoSync,
                                        onCheckedChange = { viewModel.setCloudAutoSync(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.Black,
                                            checkedTrackColor = PremiumGold,
                                            uncheckedThumbColor = Color.Gray,
                                            uncheckedTrackColor = ImmersiveHeaderBlue
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        viewModel.publishToCloud(
                                            onSuccess = { Toast.makeText(context, "Live webcast synchronized!", Toast.LENGTH_SHORT).show() },
                                            onFailure = { err -> Toast.makeText(context, "Upload failed: $err", Toast.LENGTH_SHORT).show() }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = Color.Black),
                                    enabled = !isSyncing
                                ) {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Upload & Update Live Scores Now", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        viewModel.pullFromCloud(
                                            keyToUse = syncKey,
                                            onSuccess = { Toast.makeText(context, "Pulled live updates successfully!", Toast.LENGTH_SHORT).show() },
                                            onFailure = { err -> Toast.makeText(context, "Download failed: $err", Toast.LENGTH_SHORT).show() }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = Color.Black),
                                    enabled = !isSyncing
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pull Live Updates from Admin Now", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Live Sync Confirmation Badge
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F291E)),
                                border = BorderStroke(1.dp, Color(0xFF22C55E).copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF22C55E),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "തത്സമയ ലൈവ് സിങ്ക് സിസ്റ്റം പ്രവർത്തിക്കുന്നു. നിങ്ങൾ ആപ്പിൽ നൽകുന്ന ഓരോ മാപ്പും ലൈവ് സ്കോർ ബോർഡിൽ സ്വയം അപ്ഡേറ്റാകും.",
                                        color = Color(0xFF86EFAC),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        lineHeight = 15.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(TextSecondary.copy(alpha = 0.15f))
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            TextButton(
                                onClick = {
                                    viewModel.setSyncKey("")
                                    Toast.makeText(context, "Webcast disconnected.", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text("Disconnect current Sync Key", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                        }

                        // Sync Status bar
                        if (syncStatus != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(PremiumGold.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                    .border(BorderStroke(1.dp, PremiumGold.copy(alpha = 0.2f)), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = PremiumGold, strokeWidth = 1.5.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(syncStatus ?: "", color = TextPrimary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = Color.Black)
            ) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    )
}

// 4. TAB 0: LEADERBOARD (RANKINGS AND LIVE PODIUM)
@Composable
fun LeaderboardTab(viewModel: MainViewModel) {
    val groupScores by viewModel.groupScores.collectAsStateWithLifecycle()
    val studentScores by viewModel.studentScores.collectAsStateWithLifecycle()
    val syncKey by viewModel.syncKey.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Real-Time Score Status Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ImmersiveHeaderBlue),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PremiumGold.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "തത്സമയ സ്കോർ ബോർഡ്",
                                color = PremiumGold,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "LIVE REAL-TIME SCORES",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    if (syncKey.isNotEmpty()) {
                        Button(
                            onClick = {
                                viewModel.pullFromCloud(
                                    keyToUse = syncKey,
                                    onSuccess = { Toast.makeText(context, "തത്സമയ സ്കോറുകൾ പുതുക്കി!", Toast.LENGTH_SHORT).show() },
                                    onFailure = { err -> Toast.makeText(context, "Error: $err", Toast.LENGTH_SHORT).show() }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = Color(0xFF0F172A)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isSyncing
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("റിഫ്രഷ്", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (groupScores.isEmpty()) {
            item {
                EmptyStatePlaceholder(
                    icon = Icons.Default.Leaderboard,
                    message = "Add teams and results to see the live leaderboard rankings!"
                )
            }
        } else {
            // Highlighting leading group badge at top
            item {
                val winner = groupScores.first()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(12.dp, RoundedCornerShape(24.dp), ambientColor = PremiumGold.copy(alpha = 0.2f), spotColor = PremiumGold.copy(alpha = 0.4f)),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, PremiumGold.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(ImmersiveCardGradientStart, ImmersiveCardGradientEnd)
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(100.dp))
                                            .background(PremiumGold.copy(alpha = 0.2f))
                                            .border(BorderStroke(1.dp, PremiumGold.copy(alpha = 0.4f)), RoundedCornerShape(100.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            "LEADING NOW",
                                            color = PremiumGold,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = winner.group.name,
                                        color = TextPrimary,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Group ID: DH-${winner.group.id} | Active Contender",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${winner.totalPoints}",
                                        color = PremiumGold,
                                        fontSize = 38.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = "TOTAL POINTS",
                                        color = TextSecondary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Beautiful Gold Progress Bar matching the Tailwind HTML "w-82%"
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(TextSecondary.copy(alpha = 0.15f))
                            ) {
                                val percentage = if (groupScores.first().totalPoints > 0) {
                                    0.82f
                                } else {
                                    0.1f
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fraction = percentage)
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(PremiumGold, LightGold)
                                            )
                                        )
                                )
                            }
                        }
                    }
                }
            }

            // High Quality 3D-like visual podium
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(containerColor = ImmersiveHeaderBlue),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Podium Standings",
                            fontWeight = FontWeight.Bold,
                            color = PremiumGold,
                            fontSize = 15.sp,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // 2ND PLACE
                            if (groupScores.size > 1) {
                                val second = groupScores[1]
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(second.group.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = TextPrimary)
                                    Text("${second.totalPoints} pts", fontSize = 11.sp, color = TextSecondary)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(70.dp)
                                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color(0xFFCBD5E1), Color(0xFF94A3B8))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🥈 2nd", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // 1ST PLACE
                            if (groupScores.isNotEmpty()) {
                                val first = groupScores[0]
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1.2f)
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = PremiumGold, modifier = Modifier.size(24.dp))
                                    Text(first.group.name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = LightGold)
                                    Text("${first.totalPoints} pts", fontSize = 12.sp, color = PremiumGold, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(100.dp)
                                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(PremiumGold, DarkGold)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🏆 1st", color = Color.Black, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }

                            // 3RD PLACE
                            if (groupScores.size > 2) {
                                val third = groupScores[2]
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(third.group.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = TextPrimary)
                                    Text("${third.totalPoints} pts", fontSize = 11.sp, color = TextSecondary)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color(0xFFD97706), Color(0xFFB45309))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🥉 3rd", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Scoreboard Detailed Ranks Title
            item {
                Text(
                    "Scoreboard Standings",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = PremiumGold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Detailed scroll list of teams
            items(groupScores) { score ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = ImmersiveHeaderBlue),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.1f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(score.group.colorHex)))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(score.group.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${score.goldCount}🥇 / ${score.silverCount}🥈 / ${score.bronzeCount}🥉",
                                color = TextSecondary,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Text(
                                "${score.totalPoints} pts",
                                fontWeight = FontWeight.Bold,
                                color = PremiumGold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            // Individual Student Leaderboard section
            if (studentScores.isNotEmpty()) {
                item {
                    Text(
                        "Outstanding Students (Top Placements)",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = PremiumGold,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                items(studentScores.take(5)) { sScore ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = ImmersiveHeaderBlue),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(sScore.student.name, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(Color(android.graphics.Color.parseColor(sScore.group?.colorHex ?: "#94A3B8")))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        sScore.group?.name ?: "No Group",
                                        color = Color(android.graphics.Color.parseColor(sScore.group?.colorHex ?: "#94A3B8")),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${sScore.firstCount}🥇 / ${sScore.secondCount}🥈 / ${sScore.thirdCount}🥉",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    "${sScore.totalPoints} pts",
                                    fontWeight = FontWeight.Bold,
                                    color = PremiumGold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Beautiful developer credit footer
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Created by Mohammed Basith Saqafi",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PremiumGold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Malayalam Competition Scoreboard v1.0",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

// 5. TAB 1: GROUPS MANAGEMENT
@Composable
fun GroupsTab(viewModel: MainViewModel) {
    val groups by viewModel.allGroups.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdminMode.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var groupToDelete by remember { mutableStateOf<GroupEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Groups List", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = PremiumGold)
            if (isAdmin) {
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PremiumGold,
                        contentColor = ImmersiveNavBlue
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Team", fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (groups.isEmpty()) {
            EmptyStatePlaceholder(
                icon = Icons.Default.Groups,
                message = if (isAdmin) "No groups configured. Click 'Add Team' to start configuring!" else "No groups configured."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(groups) { group ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = ImmersiveHeaderBlue),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(2.dp, Color(android.graphics.Color.parseColor(group.colorHex)))
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(group.colorHex)))
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(group.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                            }

                            if (isAdmin) {
                                IconButton(onClick = { groupToDelete = group }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AddGroupDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { name, color ->
                    viewModel.addGroup(name, color)
                    showAddDialog = false
                }
            )
        }

        if (groupToDelete != null) {
            AlertDialog(
                onDismissRequest = { groupToDelete = null },
                title = { Text("Delete Group?", color = PremiumGold, fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to delete '${groupToDelete?.name}'? This will also delete all students belonging to this group.") },
                confirmButton = {
                    Button(
                        onClick = {
                            groupToDelete?.let { viewModel.deleteGroup(it) }
                            groupToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { groupToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

// 6. TAB 2: STUDENTS MANAGEMENT
@Composable
fun StudentsTab(viewModel: MainViewModel) {
    val students by viewModel.allStudents.collectAsStateWithLifecycle()
    val groups by viewModel.allGroups.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdminMode.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedGroupFilter by remember { mutableStateOf<GroupEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var studentToDelete by remember { mutableStateOf<StudentEntity?>(null) }

    val filteredStudents = students.filter { student ->
        (selectedGroupFilter == null || student.groupId == selectedGroupFilter?.id) &&
        (searchQuery.isEmpty() || student.name.contains(searchQuery, ignoreCase = true))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Students List", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = PremiumGold)
            if (isAdmin) {
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PremiumGold,
                        contentColor = ImmersiveNavBlue
                    )
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Add")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Student", fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar styled with Immersive Blue background and Gold borders
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search Students...", color = TextSecondary.copy(alpha = 0.6f)) },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = PremiumGold) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = PremiumGold,
                unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                cursorColor = PremiumGold,
                focusedContainerColor = ImmersiveHeaderBlue,
                unfocusedContainerColor = ImmersiveHeaderBlue
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Group Filter chips styled elegantly
        LazyVerticalGrid(
            columns = GridCells.Adaptive(100.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 80.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedGroupFilter == null,
                    onClick = { selectedGroupFilter = null },
                    label = { Text("All Groups", color = if (selectedGroupFilter == null) Color.White else TextPrimary) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PremiumGold,
                        selectedLabelColor = Color.White,
                        containerColor = ImmersiveNavBlue,
                        labelColor = TextPrimary
                    )
                )
            }
            items(groups) { group ->
                val isSelected = selectedGroupFilter?.id == group.id
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedGroupFilter = group },
                    label = { Text(group.name, color = if (isSelected) Color.White else TextPrimary) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PremiumGold,
                        selectedLabelColor = Color.White,
                        containerColor = ImmersiveNavBlue,
                        labelColor = TextPrimary
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (filteredStudents.isEmpty()) {
            EmptyStatePlaceholder(
                icon = Icons.Default.People,
                message = "No students match your criteria."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filteredStudents) { student ->
                    val group = groups.find { it.id == student.groupId }
                    val gColor = group?.colorHex ?: "#94A3B8"
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = ImmersiveHeaderBlue),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(student.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(android.graphics.Color.parseColor(gColor)))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        group?.name ?: "No Group",
                                        color = Color(android.graphics.Color.parseColor(gColor)),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (isAdmin) {
                                IconButton(onClick = { studentToDelete = student }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AddStudentDialog(
                groups = groups,
                onDismiss = { showAddDialog = false },
                onAdd = { name, gId ->
                    viewModel.addStudent(name, gId)
                    showAddDialog = false
                },
                onBulkImport = { csvText, gId ->
                    viewModel.importStudentsFromCsvText(csvText, gId)
                    showAddDialog = false
                }
            )
        }

        if (studentToDelete != null) {
            AlertDialog(
                onDismissRequest = { studentToDelete = null },
                title = { Text("Delete Student?", color = PremiumGold, fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to delete student '${studentToDelete?.name}'?") },
                confirmButton = {
                    Button(
                        onClick = {
                            studentToDelete?.let { viewModel.deleteStudent(it) }
                            studentToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { studentToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

// 7. TAB 3: COMPETITION PRIZE ALLOCATIONS (EXPANDABLE PLACEMENT CARDS)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompetitionsTab(viewModel: MainViewModel) {
    val competitions by viewModel.allCompetitions.collectAsStateWithLifecycle()
    val students by viewModel.allStudents.collectAsStateWithLifecycle()
    val groups by viewModel.allGroups.collectAsStateWithLifecycle()
    val config by viewModel.appConfig.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdminMode.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }
    var expandedCompId by remember { mutableStateOf<Int?>(null) }
    var competitionToDelete by remember { mutableStateOf<CompetitionEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Competitions & Prizes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = PremiumGold)
            if (isAdmin) {
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PremiumGold,
                        contentColor = Color(0xFF0F172A)
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Competition", fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (competitions.isEmpty()) {
            EmptyStatePlaceholder(
                icon = Icons.Default.EmojiEvents,
                message = "No competitions added. Set up events and allocate prizes!"
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(competitions) { comp ->
                    val isExpanded = expandedCompId == comp.id
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = ImmersiveHeaderBlue),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.1f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column {
                            // Competition Title Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedCompId = if (isExpanded) null else comp.id }
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = PremiumGold)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(comp.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isAdmin) {
                                        IconButton(
                                            onClick = { competitionToDelete = comp },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "Expand",
                                        tint = Color.LightGray
                                    )
                                }
                            }

                            // Expandable Allocation UI
                            if (isExpanded) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(TextSecondary.copy(alpha = 0.1f))
                                )
                                if (isAdmin) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("Winners & Placements", fontWeight = FontWeight.Bold, color = PremiumGold)
                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Position First
                                        WinnerSelectorRow(
                                            positionLabel = "1st Place (Winner)",
                                            students = students,
                                            groups = groups,
                                            selectedStudentId = comp.firstStudentId,
                                            selectedGrade = comp.firstGrade,
                                            selectedMarks = comp.firstMarks ?: 5,
                                            onSelected = { studentId, grade, marks ->
                                                viewModel.updateCompetition(
                                                    comp.copy(firstStudentId = studentId, firstGrade = grade, firstMarks = marks)
                                                )
                                            },
                                            enabled = true
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        // Position Second
                                        WinnerSelectorRow(
                                            positionLabel = "2nd Place (Runner)",
                                            students = students,
                                            groups = groups,
                                            selectedStudentId = comp.secondStudentId,
                                            selectedGrade = comp.secondGrade,
                                            selectedMarks = comp.secondMarks ?: 3,
                                            onSelected = { studentId, grade, marks ->
                                                viewModel.updateCompetition(
                                                    comp.copy(secondStudentId = studentId, secondGrade = grade, secondMarks = marks)
                                                )
                                            },
                                            enabled = true
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        // Position Third
                                        WinnerSelectorRow(
                                            positionLabel = "3rd Place",
                                            students = students,
                                            groups = groups,
                                            selectedStudentId = comp.thirdStudentId,
                                            selectedGrade = comp.thirdGrade,
                                            selectedMarks = comp.thirdMarks ?: 1,
                                            onSelected = { studentId, grade, marks ->
                                                viewModel.updateCompetition(
                                                    comp.copy(thirdStudentId = studentId, thirdGrade = grade, thirdMarks = marks)
                                                )
                                            },
                                            enabled = true
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                    }
                                } else {
                                    // Beautiful read-only view of published results for students/children
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("Published Results", fontWeight = FontWeight.Bold, color = PremiumGold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(12.dp))

                                        val firstStudent = students.find { it.id == comp.firstStudentId }
                                        val secondStudent = students.find { it.id == comp.secondStudentId }
                                        val thirdStudent = students.find { it.id == comp.thirdStudentId }

                                        if (firstStudent == null && secondStudent == null && thirdStudent == null) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(ImmersiveNavBlue, RoundedCornerShape(12.dp))
                                                    .padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(Icons.Default.HourglassEmpty, contentDescription = "Pending", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Results of this competition will be published soon!", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                            }
                                        } else {
                                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                if (firstStudent != null) {
                                                    val g = groups.find { it.id == firstStudent.groupId }
                                                    ResultRow(
                                                        position = "1st Place 🥇",
                                                        studentName = firstStudent.name,
                                                        groupName = g?.name ?: "No Group",
                                                        groupColorHex = g?.colorHex ?: "#FFFFFF",
                                                        grade = comp.firstGrade,
                                                        marks = comp.firstMarks ?: 5
                                                    )
                                                }
                                                if (secondStudent != null) {
                                                    val g = groups.find { it.id == secondStudent.groupId }
                                                    ResultRow(
                                                        position = "2nd Place 🥈",
                                                        studentName = secondStudent.name,
                                                        groupName = g?.name ?: "No Group",
                                                        groupColorHex = g?.colorHex ?: "#FFFFFF",
                                                        grade = comp.secondGrade,
                                                        marks = comp.secondMarks ?: 3
                                                    )
                                                }
                                                if (thirdStudent != null) {
                                                    val g = groups.find { it.id == thirdStudent.groupId }
                                                    ResultRow(
                                                        position = "3rd Place 🥉",
                                                        studentName = thirdStudent.name,
                                                        groupName = g?.name ?: "No Group",
                                                        groupColorHex = g?.colorHex ?: "#FFFFFF",
                                                        grade = comp.thirdGrade,
                                                        marks = comp.thirdMarks ?: 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AddCompDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { name ->
                    viewModel.addCompetition(name)
                    showAddDialog = false
                }
            )
        }

        if (competitionToDelete != null) {
            AlertDialog(
                onDismissRequest = { competitionToDelete = null },
                title = { Text("Delete Competition?", color = PremiumGold, fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to delete '${competitionToDelete?.name}'? This will permanently delete its results and grades.") },
                confirmButton = {
                    Button(
                        onClick = {
                            competitionToDelete?.let { viewModel.deleteCompetition(it) }
                            competitionToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { competitionToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

// 8. WINNER ALLOCATION ROW COMPONENT (WITH GRADE & TOUCH MARKS CONTROLLER)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WinnerSelectorRow(
    positionLabel: String,
    students: List<StudentEntity>,
    groups: List<GroupEntity>,
    selectedStudentId: Int?,
    selectedGrade: String?,
    selectedMarks: Int,
    onSelected: (Int?, String?, Int) -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val currentStudent = students.find { it.id == selectedStudentId }
    val currentGroup = groups.find { it.id == currentStudent?.groupId }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TextSecondary.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(positionLabel, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PremiumGold)
        Spacer(modifier = Modifier.height(8.dp))

        // Student Dropdown Selection
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedCard(
                onClick = { if (enabled) expanded = true },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = ImmersiveNavBlue,
                    contentColor = TextPrimary
                ),
                border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(currentStudent?.name ?: "Select Student...", fontWeight = FontWeight.SemiBold, color = if (enabled) TextPrimary else TextSecondary)
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "Dropdown",
                        tint = if (enabled) PremiumGold else TextSecondary.copy(alpha = 0.4f)
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                DropdownMenuItem(
                    text = { Text("None (Clear Winner)") },
                    onClick = {
                        onSelected(null, null, selectedMarks)
                        expanded = false
                    }
                )
                students.forEach { s ->
                    val grp = groups.find { it.id == s.groupId }
                    DropdownMenuItem(
                        text = { Text("${s.name} (${grp?.name ?: "No Group"})") },
                        onClick = {
                            onSelected(s.id, selectedGrade, selectedMarks)
                            expanded = false
                        }
                    )
                }
            }
        }

        // Auto group detection pill
        if (currentGroup != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color(android.graphics.Color.parseColor(currentGroup.colorHex)).copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(android.graphics.Color.parseColor(currentGroup.colorHex)))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Auto Group Resolved: ${currentGroup.name}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(android.graphics.Color.parseColor(currentGroup.colorHex))
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Grade Selection and Marks Count Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Grade Select
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Grade: ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(modifier = Modifier.width(4.dp))
                listOf("A", "B", "C").forEach { g ->
                    val isSelected = selectedGrade == g
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) {
                                    if (enabled) PremiumGold else PremiumGold.copy(alpha = 0.4f)
                                } else {
                                    ImmersiveHeaderBlue
                                }
                            )
                            .clickable(enabled = enabled) {
                                onSelected(
                                    selectedStudentId,
                                    if (isSelected) null else g,
                                    selectedMarks
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            g,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) {
                                if (enabled) Color.Black else Color.Black.copy(alpha = 0.5f)
                            } else {
                                if (enabled) TextPrimary else TextSecondary
                            }
                        )
                    }
                }
            }

            // Plus / Minus Marks Counter
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Marks: ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(modifier = Modifier.width(4.dp))
                FilledIconButton(
                    onClick = { if (selectedMarks > 0) onSelected(selectedStudentId, selectedGrade, selectedMarks - 1) },
                    modifier = Modifier.size(28.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (enabled) ImmersiveHeaderBlue else ImmersiveHeaderBlue.copy(alpha = 0.5f)
                    ),
                    enabled = enabled
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "Decrease",
                        tint = if (enabled) TextPrimary else TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    selectedMarks.toString(),
                    color = if (enabled) TextPrimary else TextSecondary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                FilledIconButton(
                    onClick = { onSelected(selectedStudentId, selectedGrade, selectedMarks + 1) },
                    modifier = Modifier.size(28.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (enabled) PremiumGold else PremiumGold.copy(alpha = 0.3f)
                    ),
                    enabled = enabled
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Increase",
                        tint = if (enabled) Color.Black else Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// 9. TAB 4: POSTER GENERATOR PREVIEW & SHARING
@Composable
fun PosterTab(viewModel: MainViewModel) {
    val context = LocalContext.current
    val config by viewModel.appConfig.collectAsStateWithLifecycle()
    val groupScores by viewModel.groupScores.collectAsStateWithLifecycle()
    val studentScores by viewModel.studentScores.collectAsStateWithLifecycle()
    val competitions by viewModel.allCompetitions.collectAsStateWithLifecycle()
    val students by viewModel.allStudents.collectAsStateWithLifecycle()
    val groups by viewModel.allGroups.collectAsStateWithLifecycle()

    var posterBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val scope = rememberCoroutineScope()

    // Generate poster on demand
    LaunchedEffect(groupScores, studentScores, competitions) {
        if (config != null) {
            posterBitmap = PosterGenerator.generatePoster(
                context = context,
                madrasaName = config!!.madrasaName,
                eventName = config!!.eventName,
                groupScores = groupScores,
                studentScores = studentScores,
                competitions = competitions,
                students = students,
                groups = groups
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Arts Fest Poster Generator",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = PremiumGold
        )
        Text(
            "Share premium Islamic styled result cards for Social Media and Print. 1080px HD Export.",
            fontSize = 12.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        // Poster Preview Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(1.dp, TextSecondary.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(ImmersiveHeaderBlue),
            contentAlignment = Alignment.Center
        ) {
            if (posterBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = posterBitmap!!.asImageBitmap(),
                    contentDescription = "Poster Preview",
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(12.dp)
                )
            } else {
                CircularProgressIndicator(color = PremiumGold)
            }
        }

        // Export Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    posterBitmap?.let {
                        val success = PosterGenerator.savePosterToGallery(context, it, config?.eventName ?: "Results")
                        if (success) {
                            Toast.makeText(context, "Saved Poster to Pictures/Scoreboard!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Error saving image", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PremiumGold),
                border = BorderStroke(1.5.dp, PremiumGold)
            ) {
                Icon(Icons.Default.Download, contentDescription = "Download")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Download PNG", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    posterBitmap?.let {
                        PosterGenerator.sharePoster(context, it, config?.eventName ?: "Results")
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = Color(0xFF0F172A))
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share Poster", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// 10. HELPER/SUB-COMPONENTS (DIALOGS & EMPTY STATE)
@Composable
fun EmptyStatePlaceholder(icon: ImageVector, message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .background(ImmersiveHeaderBlue, RoundedCornerShape(16.dp))
            .border(1.dp, TextSecondary.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(54.dp), tint = PremiumGold)
            Spacer(modifier = Modifier.height(12.dp))
            Text(message, textAlign = TextAlign.Center, color = TextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
fun AddGroupDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var groupName by remember { mutableStateOf("") }
    var selectedColorHex by remember { mutableStateOf(GroupColorsList.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Team Group") },
        text = {
            Column {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Select Team Theme Color:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.height(80.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(GroupColorsList) { color ->
                        val isSelected = selectedColorHex == color
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(color)))
                                .border(
                                    2.dp,
                                    if (isSelected) Color.Black else Color.Transparent,
                                    CircleShape
                                )
                                .clickable { selectedColorHex = color }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (groupName.isNotBlank()) onAdd(groupName, selectedColorHex) },
                colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = Color.Black)
            ) {
                Text("Add Team")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddStudentDialog(
    groups: List<GroupEntity>,
    onDismiss: () -> Unit,
    onAdd: (String, Int) -> Unit,
    onBulkImport: (String, Int?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedGroupId by remember { mutableStateOf<Int?>(groups.firstOrNull()?.id) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Manual, 1: Bulk Paste
    var bulkText by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Student / Import") },
        text = {
            Column {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                        Padding(8.dp) { Text("Single Student") }
                    }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                        Padding(8.dp) { Text("Bulk Copy-Paste") }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (selectedTab == 0) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Student Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (groups.isNotEmpty()) {
                        Text("Select Group Team:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))

                        Box(modifier = Modifier.fillMaxWidth()) {
                            val activeGroup = groups.find { it.id == selectedGroupId }
                            OutlinedCard(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(activeGroup?.name ?: "Select Group...")
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                groups.forEach { g ->
                                    DropdownMenuItem(
                                        text = { Text(g.name) },
                                        onClick = {
                                            selectedGroupId = g.id
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = bulkText,
                        onValueChange = { bulkText = it },
                        label = { Text("Paste Names (one per line)") },
                        placeholder = { Text("Optionally paste CSV:\nAbdullah, Safa Group\nKamil, Safa Group") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (groups.isNotEmpty()) {
                        Text("Default Group (if not specified in text):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))

                        Box(modifier = Modifier.fillMaxWidth()) {
                            val activeGroup = groups.find { it.id == selectedGroupId }
                            OutlinedCard(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(activeGroup?.name ?: "Select Default Group...")
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                groups.forEach { g ->
                                    DropdownMenuItem(
                                        text = { Text(g.name) },
                                        onClick = {
                                            selectedGroupId = g.id
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedTab == 0) {
                        selectedGroupId?.let { onAdd(name, it) }
                    } else {
                        onBulkImport(bulkText, selectedGroupId)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = Color.Black)
            ) {
                Text(if (selectedTab == 0) "Add Student" else "Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddCompDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var compName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Competition Event") },
        text = {
            OutlinedTextField(
                value = compName,
                onValueChange = { compName = it },
                label = { Text("Competition Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
        },
        confirmButton = {
            Button(
                onClick = { if (compName.isNotBlank()) onAdd(compName) },
                colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = Color.Black)
            ) {
                Text("Add Comp")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun Padding(padding: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Box(modifier = Modifier.padding(padding)) {
        content()
    }
}

@Composable
fun ExportHubDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val config by viewModel.appConfig.collectAsStateWithLifecycle()
    val groupScores by viewModel.groupScores.collectAsStateWithLifecycle()
    val students by viewModel.allStudents.collectAsStateWithLifecycle()
    val groups by viewModel.allGroups.collectAsStateWithLifecycle()
    val competitions by viewModel.allCompetitions.collectAsStateWithLifecycle()

    val madrasaName = config?.madrasaName ?: "Darul Huda Islamic Complex"
    val eventName = config?.eventName ?: "Scoreboard Event"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Download, contentDescription = null, tint = PremiumGold, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export Data Hub", color = PremiumGold, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Download or share scoreboard reports in Excel/CSV and PDF formats. Choose from separate components or complete comprehensive files.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                // 1. Comprehensive Complete Report (All Data)
                ExportOptionCard(
                    title = "Comprehensive Report (All-in-One)",
                    description = "Includes Group Standings, full Student Roster, and Competition Winners & Grades in a single multi-page report.",
                    onCsvSave = {
                        val csv = ExportManager.generateAllDataCsv(groupScores, students, groups, competitions)
                        val success = ExportManager.saveCsvToDownloads(context, csv, "Scoreboard_Full_Report")
                        if (success) Toast.makeText(context, "Saved Excel to Downloads/Scoreboard!", Toast.LENGTH_LONG).show()
                    },
                    onCsvShare = {
                        val csv = ExportManager.generateAllDataCsv(groupScores, students, groups, competitions)
                        ExportManager.shareCsv(context, csv, "Scoreboard_Full_Report")
                    },
                    onPdfSave = {
                        ExportManager.exportAllDataPdf(context, madrasaName, eventName, groupScores, students, groups, competitions, isShare = false)
                    },
                    onPdfShare = {
                        ExportManager.exportAllDataPdf(context, madrasaName, eventName, groupScores, students, groups, competitions, isShare = true)
                    }
                )

                // 2. Student List
                ExportOptionCard(
                    title = "Student Roster List",
                    description = "Roster of all registered students with their designated team group assignments.",
                    onCsvSave = {
                        val csv = ExportManager.generateStudentsCsv(students, groups)
                        val success = ExportManager.saveCsvToDownloads(context, csv, "Student_Roster")
                        if (success) Toast.makeText(context, "Saved Excel to Downloads/Scoreboard!", Toast.LENGTH_LONG).show()
                    },
                    onCsvShare = {
                        val csv = ExportManager.generateStudentsCsv(students, groups)
                        ExportManager.shareCsv(context, csv, "Student_Roster")
                    },
                    onPdfSave = {
                        ExportManager.exportStudentsPdf(context, madrasaName, eventName, students, groups, isShare = false)
                    },
                    onPdfShare = {
                        ExportManager.exportStudentsPdf(context, madrasaName, eventName, students, groups, isShare = true)
                    }
                )

                // 3. Competitions List
                ExportOptionCard(
                    title = "Competitions Category List",
                    description = "Listing of all registered arts competition event categories and completion status.",
                    onCsvSave = {
                        val csv = ExportManager.generateCompetitionsCsv(competitions)
                        val success = ExportManager.saveCsvToDownloads(context, csv, "Competitions_List")
                        if (success) Toast.makeText(context, "Saved Excel to Downloads/Scoreboard!", Toast.LENGTH_LONG).show()
                    },
                    onCsvShare = {
                        val csv = ExportManager.generateCompetitionsCsv(competitions)
                        ExportManager.shareCsv(context, csv, "Competitions_List")
                    },
                    onPdfSave = {
                        ExportManager.exportCompetitionsPdf(context, madrasaName, eventName, competitions, isShare = false)
                    },
                    onPdfShare = {
                        ExportManager.exportCompetitionsPdf(context, madrasaName, eventName, competitions, isShare = true)
                    }
                )

                // 4. Winners list
                ExportOptionCard(
                    title = "Prizes & Winners Standing List",
                    description = "Detailed list of all competition results, indicating first, second, and third prize winners, their grades and marks.",
                    onCsvSave = {
                        val csv = ExportManager.generateResultsCsv(competitions, students, groups)
                        val success = ExportManager.saveCsvToDownloads(context, csv, "Prizes_And_Winners")
                        if (success) Toast.makeText(context, "Saved Excel to Downloads/Scoreboard!", Toast.LENGTH_LONG).show()
                    },
                    onCsvShare = {
                        val csv = ExportManager.generateResultsCsv(competitions, students, groups)
                        ExportManager.shareCsv(context, csv, "Prizes_And_Winners")
                    },
                    onPdfSave = {
                        ExportManager.exportResultsPdf(context, madrasaName, eventName, competitions, students, groups, isShare = false)
                    },
                    onPdfShare = {
                        ExportManager.exportResultsPdf(context, madrasaName, eventName, competitions, students, groups, isShare = true)
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = Color.Black)
            ) {
                Text("Close Hub")
            }
        }
    )
}

@Composable
fun ExportOptionCard(
    title: String,
    description: String,
    onCsvSave: () -> Unit,
    onCsvShare: () -> Unit,
    onPdfSave: () -> Unit,
    onPdfShare: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ImmersiveHeaderBlue),
        border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = PremiumGold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, fontSize = 11.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(12.dp))

            // Excel CSV Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCsvSave,
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = PremiumGold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Excel", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onCsvShare,
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp), tint = PremiumGold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // PDF Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPdfSave,
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onPdfShare,
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ResultRow(
    position: String,
    studentName: String,
    groupName: String,
    groupColorHex: String,
    grade: String?,
    marks: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ImmersiveNavBlue, RoundedCornerShape(12.dp))
            .border(1.dp, TextSecondary.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Position Label
            Text(
                text = position,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                color = PremiumGold,
                modifier = Modifier.width(90.dp)
            )

            Column {
                Text(
                    text = studentName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val parsedColor = try {
                        Color(android.graphics.Color.parseColor(groupColorHex))
                    } catch (e: Exception) {
                        Color.LightGray
                    }
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(parsedColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = groupName,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Grade and Marks bubble
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!grade.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .background(PremiumGold.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Grade $grade",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = PremiumGold
                    )
                }
            }
            Box(
                modifier = Modifier
                    .background(ImmersiveHeaderBlue, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "$marks Pts",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }
    }
}

