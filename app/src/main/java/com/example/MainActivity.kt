package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.AppDatabase
import com.example.data.StoryRepository
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val db = remember { AppDatabase.getDatabase(context) }
            val repository = remember { StoryRepository(db.storyDao()) }
            val characterRepository = remember { com.example.data.CharacterRepository(db.characterDao()) }
            val viewModel: StoryViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return StoryViewModel(repository, characterRepository) as T
                    }
                }
            )
            val uiState by viewModel.uiState.collectAsState()

            MyApplicationTheme(darkTheme = uiState.isDarkMode, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StoryWeaverApp(viewModel, uiState)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryWeaverApp(viewModel: StoryViewModel, uiState: StoryUiState) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onImageSelected(context, it) }
    }

    Scaffold(
        topBar = {
            if (!uiState.isFocusMode) {
                TopAppBar(
                    title = { 
                        Text("StoryWeaver", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) 
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleFocusMode() }) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Focus Mode"
                            )
                        }
                        IconButton(onClick = { viewModel.toggleCharactersPanel() }) {
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = "Characters"
                        )
                    }
                    IconButton(onClick = { viewModel.toggleHistory() }) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "History"
                        )
                    }
                    IconButton(onClick = { viewModel.toggleDarkMode() }) {
                        Icon(
                            imageVector = if (uiState.isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Dark Mode"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
            }
        },
        bottomBar = {
            if (!uiState.isFocusMode && uiState.storyText != null) {
                ChatInputBar(
                    isLoading = uiState.isChatLoading,
                    onSend = { text ->
                        viewModel.sendMessage(text)
                        coroutineScope.launch {
                            listState.animateScrollToItem(uiState.chatMessages.size + 1)
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (uiState.isFocusMode) {
                FloatingActionButton(
                    onClick = { viewModel.toggleFocusMode() },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = "Exit Focus Mode"
                    )
                }
            }
        }
    ) { paddingValues ->
        if (uiState.showCharactersPanel) {
            CharactersScreen(viewModel = viewModel, modifier = Modifier.padding(paddingValues))
        } else if (uiState.showHistory) {
            HistoryScreen(viewModel = viewModel, modifier = Modifier.padding(paddingValues))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }
            
            // Tone Selection
            if (!uiState.isFocusMode && uiState.imageUri == null) {
                item {
                    val tones = listOf("Suspenseful", "Whimsical", "Dark", "Romantic")
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Select Tone",
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(tones) { tone ->
                                FilterChip(
                                    selected = uiState.selectedTone == tone,
                                    onClick = { viewModel.selectTone(tone) },
                                    label = { Text(tone) }
                                )
                            }
                        }
                    }
                }
            }

            // Image Section
            if (!uiState.isFocusMode) {
            item {
                if (uiState.imageUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(uiState.imageUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Inspiration Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    OutlinedButton(
                        onClick = { imagePicker.launch("image/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant)
                        )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Upload Inspiration Image", fontFamily = FontFamily.Serif)
                        }
                    }
                }
            }
            }

            // Writing Prompts Section
            if (uiState.imageUri == null && uiState.storyText == null && !uiState.isLoading) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Or jumpstart your imagination...",
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { viewModel.refreshPrompts() }) {
                                Text("Refresh")
                            }
                        }
                        
                        uiState.randomPrompts.forEach { prompt ->
                            Card(
                                onClick = { viewModel.onPromptSelected(prompt) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = prompt,
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            // Initial Story Loading
            if (uiState.isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.padding(32.dp))
                    }
                }
            }
            
            // Error handling
            if (uiState.error != null) {
                item {
                    Text(
                        text = "Error: ${uiState.error}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // Story Text
            if (uiState.storyText != null) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        TextField(
                            value = uiState.storyText,
                            onValueChange = { viewModel.updateStoryText(it) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Serif,
                                fontSize = 20.sp,
                                lineHeight = 32.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                        
                        if (!uiState.isFocusMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val wordCount = uiState.storyText.trim().split("\\s+".toRegex()).count { it.isNotBlank() }
                                Text(
                                    text = "$wordCount words" + if (uiState.isSaving) " • Saving..." else " • Saved",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                IconButton(
                                    onClick = { viewModel.undo() }, 
                                    enabled = uiState.canUndo,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", modifier = Modifier.size(20.dp))
                                }
                                IconButton(
                                    onClick = { viewModel.redo() }, 
                                    enabled = uiState.canRedo,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", modifier = Modifier.size(20.dp))
                                }
                            }
                            
                            // Read Aloud Button
                            FilledTonalButton(
                                onClick = { 
                                    if (uiState.isPlayingAudio) {
                                        viewModel.stopAudio()
                                    } else {
                                        viewModel.readAloud(context, uiState.storyText) 
                                    }
                                }
                            ) {
                                if (uiState.isAudioLoading && !uiState.isPlayingAudio) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Preparing Audio...")
                                } else {
                                    Icon(
                                        imageVector = if (uiState.isPlayingAudio) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = if (uiState.isPlayingAudio) "Stop" else "Read Aloud"
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (uiState.isPlayingAudio) "Stop Reading" else "Read Aloud")
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        // Theme Music & Custom Audio
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = "Story Sounds",
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = uiState.audioPrompt,
                                    onValueChange = { viewModel.updateAudioPrompt(it) },
                                    placeholder = { Text("e.g., footsteps, thunder, romantic theme...") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                FilledTonalButton(
                                    onClick = {
                                        if (uiState.isPlayingMusic) {
                                            viewModel.stopMusic()
                                        } else {
                                            val prompt = if (uiState.audioPrompt.isNotBlank()) uiState.audioPrompt else "Create a ${uiState.selectedTone} theme song for this story."
                                            viewModel.generateMusic(context, prompt)
                                        }
                                    }
                                ) {
                                    if (uiState.isMusicLoading && !uiState.isPlayingMusic) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(
                                            imageVector = if (uiState.isPlayingMusic) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = if (uiState.isPlayingMusic) "Stop" else "Generate Audio"
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 32.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        )
                        } else {
                            val wordCount = uiState.storyText.trim().split("\\s+".toRegex()).count { it.isNotBlank() }
                            Text(
                                text = "$wordCount words" + if (uiState.isSaving) " • Saving..." else " • Saved",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }

            // Chat Messages (skip the first message since it's the opening paragraph we already showed)
            val subsequentMessages = uiState.chatMessages.drop(1)
            if (subsequentMessages.isNotEmpty()) {
                items(subsequentMessages) { message ->
                    MessageBubble(message)
                }
            }
            
            if (uiState.isChatLoading) {
                item {
                    MessageBubble(ChatMessage("...", isUser = false))
                }
            }
            
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}
}

@Composable
fun HistoryScreen(viewModel: StoryViewModel, modifier: Modifier = Modifier) {
    val savedStories by viewModel.savedStories.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    val filteredStories = savedStories.filter { story ->
        val matchesSearch = if (uiState.searchQuery.isNotBlank()) {
            story.title.contains(uiState.searchQuery, ignoreCase = true) ||
            story.text.contains(uiState.searchQuery, ignoreCase = true)
        } else true
        val matchesTone = if (uiState.filterTone != "All") {
            story.tone.equals(uiState.filterTone, ignoreCase = true)
        } else true
        matchesSearch && matchesTone
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Saved Stories",
                fontFamily = FontFamily.Serif,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search stories...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") }
            )
        }

        item {
            val tones = listOf("All", "Suspenseful", "Whimsical", "Dark", "Romantic")
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(tones) { tone ->
                    FilterChip(
                        selected = uiState.filterTone == tone,
                        onClick = { viewModel.updateFilterTone(tone) },
                        label = { Text(tone) }
                    )
                }
            }
        }

        if (filteredStories.isEmpty()) {
            item {
                Text(
                    text = if (savedStories.isEmpty()) "No saved stories yet." else "No stories match your search.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }

        items(filteredStories) { story ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.loadStory(story) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (story.title.isNotBlank()) story.title else "Untitled",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val previewText = if (story.text.length > 100) story.text.substring(0, 100) + "..." else story.text
                    Text(
                        text = previewText,
                        fontFamily = FontFamily.Serif,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val date = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(story.timestamp))
                            Text(
                                text = date,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            if (story.tone.isNotBlank()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = story.tone,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = { viewModel.deleteStory(story.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
        
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bgColor = if (message.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (message.isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = if (message.isUser) {
        RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(bgColor)
                .padding(16.dp)
        ) {
            Text(
                text = message.text,
                fontFamily = FontFamily.Serif,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = textColor
            )
        }
    }
}

@Composable
fun ChatInputBar(
    isLoading: Boolean,
    onSend: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Continue the story...") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp),
                enabled = !isLoading
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            IconButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onSend(text)
                        text = ""
                    }
                },
                enabled = !isLoading && text.isNotBlank(),
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(24.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharactersScreen(viewModel: StoryViewModel, modifier: Modifier = Modifier) {
    val characters by viewModel.characters.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var traits by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Character") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = traits,
                        onValueChange = { traits = it },
                        label = { Text("Traits (e.g., brave, cunning)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Physical Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            viewModel.saveCharacter(name, traits, description)
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                Text(
                    text = "Character Profiles",
                    fontFamily = FontFamily.Serif,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Select characters to include them in the context for story generation.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (characters.isEmpty()) {
                item {
                    Text(
                        text = "No characters added yet.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(characters) { character ->
                val isSelected = uiState.selectedCharacters.any { it.id == character.id }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    onClick = { viewModel.toggleCharacterSelection(character) }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = character.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            if (character.traits.isNotBlank()) {
                                Text(
                                    text = "Traits: ${character.traits}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            if (character.physicalDescription.isNotBlank()) {
                                Text(
                                    text = "Description: ${character.physicalDescription}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.deleteCharacter(character.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Character")
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Character")
        }
    }
}
