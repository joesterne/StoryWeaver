package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gemini.Content
import com.example.gemini.GenerateContentRequest
import com.example.gemini.GenerationConfig
import com.example.gemini.InlineData
import com.example.gemini.Part
import com.example.gemini.PrebuiltVoiceConfig
import com.example.gemini.RetrofitClient
import com.example.gemini.SpeechConfig
import com.example.gemini.VoiceConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import com.example.data.StoryEntity
import com.example.data.StoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

data class ChatMessage(val text: String, val isUser: Boolean)

class StoryViewModel(
    private val repository: StoryRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(StoryUiState())
    val uiState: StateFlow<StoryUiState> = _uiState.asStateFlow()

    val savedStories: StateFlow<List<StoryEntity>> = repository.allStories
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var currentStoryId: Int? = null

    private var mediaPlayer: MediaPlayer? = null
    private var musicPlayer: MediaPlayer? = null
    private val apiKey = BuildConfig.GEMINI_API_KEY

    // Chat history for Gemini
    private val chatHistory = mutableListOf<Content>()

    private val textHistory = mutableListOf<String>()
    private var historyIndex = -1
    private var lastSaveTime = 0L

    private fun addToHistory(text: String) {
        if (historyIndex >= 0 && textHistory[historyIndex] == text) return
        
        if (historyIndex < textHistory.size - 1) {
            textHistory.subList(historyIndex + 1, textHistory.size).clear()
        }
        
        textHistory.add(text)
        historyIndex = textHistory.size - 1
        updateUndoRedoState()
    }

    private fun updateUndoRedoState() {
        _uiState.value = _uiState.value.copy(
            canUndo = historyIndex > 0,
            canRedo = historyIndex < textHistory.size - 1
        )
    }

    fun undo() {
        if (historyIndex > 0) {
            historyIndex--
            val previousText = textHistory[historyIndex]
            applyHistoryText(previousText)
        }
    }

    fun redo() {
        if (historyIndex < textHistory.size - 1) {
            historyIndex++
            val nextText = textHistory[historyIndex]
            applyHistoryText(nextText)
        }
    }

    private fun applyHistoryText(newText: String) {
        val currentMessages = _uiState.value.chatMessages.toMutableList()
        if (currentMessages.isNotEmpty()) {
            currentMessages[0] = currentMessages[0].copy(text = newText)
        }
        _uiState.value = _uiState.value.copy(
            storyText = newText,
            chatMessages = currentMessages
        )
        updateUndoRedoState()
        
        viewModelScope.launch {
            val allText = newText + if (currentMessages.size > 1) "\n\n" + currentMessages.drop(1).joinToString("\n\n") { it.text } else ""
            currentStoryId = repository.insert(StoryEntity(id = currentStoryId ?: 0, text = allText)).toInt()
        }
    }

    fun toggleDarkMode() {
        _uiState.value = _uiState.value.copy(isDarkMode = !_uiState.value.isDarkMode)
    }

    fun selectTone(tone: String) {
        _uiState.value = _uiState.value.copy(selectedTone = tone)
    }

    fun updateAudioPrompt(prompt: String) {
        _uiState.value = _uiState.value.copy(audioPrompt = prompt)
    }

    fun onImageSelected(context: Context, uri: Uri) {
        currentStoryId = null
        textHistory.clear()
        historyIndex = -1
        _uiState.value = _uiState.value.copy(imageUri = uri, storyText = null, chatMessages = emptyList(), canUndo = false, canRedo = false)
        chatHistory.clear()
        generateStory(context, uri)
    }

    private fun generateStory(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val base64Image = uriToBase64(context, uri)
                if (base64Image == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Failed to process image")
                    return@launch
                }

                val prompt = "Analyze the mood, scene, and atmosphere in this image. Write a captivating opening paragraph to a story set in this world with a ${_uiState.value.selectedTone.lowercase()} tone."
                
                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(
                            role = "user",
                            parts = listOf(
                                Part(text = prompt),
                                Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                            )
                        )
                    )
                )

                val response = RetrofitClient.service.generateContent("gemini-3.1-pro-preview", apiKey, request)
                val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                
                if (responseText != null) {
                    textHistory.clear()
                    historyIndex = -1
                    addToHistory(responseText)
                    
                    _uiState.value = _uiState.value.copy(
                        storyText = responseText,
                        isLoading = false,
                        chatMessages = listOf(ChatMessage(responseText, isUser = false))
                    )
                    
                    val storyEntity = StoryEntity(text = responseText)
                    currentStoryId = repository.insert(storyEntity).toInt()

                    chatHistory.add(
                        Content(
                            role = "user",
                            parts = listOf(
                                Part(text = prompt),
                                Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                            )
                        )
                    )
                    chatHistory.add(
                        Content(
                            role = "model",
                            parts = listOf(Part(text = responseText))
                        )
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Failed to generate story")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.localizedMessage)
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isChatLoading = true,
                chatMessages = _uiState.value.chatMessages + ChatMessage(text, isUser = true)
            )

            try {
                val userContent = Content(role = "user", parts = listOf(Part(text = text)))
                chatHistory.add(userContent)

                val request = GenerateContentRequest(
                    contents = chatHistory,
                    systemInstruction = Content(
                        parts = listOf(Part(text = "You are a creative writer co-authoring a story with the user based on an initial scene. Keep responses concise, engaging, and in character as a storyteller."))
                    )
                )

                val response = RetrofitClient.service.generateContent("gemini-3.1-pro-preview", apiKey, request)
                val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                
                if (responseText != null) {
                    _uiState.value = _uiState.value.copy(
                        isChatLoading = false,
                        chatMessages = _uiState.value.chatMessages + ChatMessage(responseText, isUser = false)
                    )
                    chatHistory.add(Content(role = "model", parts = listOf(Part(text = responseText))))
                    
                    val allText = _uiState.value.storyText + "\n\n" + _uiState.value.chatMessages.drop(1).joinToString("\n\n") { it.text }
                    currentStoryId = repository.insert(StoryEntity(id = currentStoryId ?: 0, text = allText)).toInt()
                } else {
                    _uiState.value = _uiState.value.copy(isChatLoading = false, error = "Failed to generate response")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isChatLoading = false, error = e.localizedMessage)
            }
        }
    }

    fun readAloud(context: Context, text: String) {
        if (_uiState.value.isPlayingAudio) {
            stopAudio()
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAudioLoading = true)
            try {
                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(
                            role = "user",
                            parts = listOf(Part(text = "Say the following text: $text"))
                        )
                    ),
                    generationConfig = GenerationConfig(
                        responseModalities = listOf("AUDIO"),
                        speechConfig = SpeechConfig(
                            voiceConfig = VoiceConfig(
                                prebuiltVoiceConfig = PrebuiltVoiceConfig(voiceName = "Aoede")
                            )
                        )
                    )
                )
                
                val response = RetrofitClient.service.generateContent("gemini-3.1-flash-tts-preview", apiKey, request)
                
                val audioBase64 = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull { it.inlineData != null }?.inlineData?.data
                
                if (audioBase64 != null) {
                    playAudio(context, audioBase64)
                } else {
                    _uiState.value = _uiState.value.copy(isAudioLoading = false, error = "No audio received")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isAudioLoading = false, error = e.localizedMessage)
            }
        }
    }

    private fun playAudio(context: Context, base64Audio: String) {
        try {
            val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
            val tempFile = File(context.cacheDir, "story_audio_${UUID.randomUUID()}.mp3")
            FileOutputStream(tempFile).use { it.write(audioBytes) }

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    _uiState.value = _uiState.value.copy(isPlayingAudio = false)
                    it.release()
                    mediaPlayer = null
                    tempFile.delete()
                }
            }
            _uiState.value = _uiState.value.copy(isAudioLoading = false, isPlayingAudio = true)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isAudioLoading = false, error = "Failed to play audio")
        }
    }

    fun stopAudio() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
        _uiState.value = _uiState.value.copy(isPlayingAudio = false)
    }

    fun stopMusic() {
        musicPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        musicPlayer = null
        _uiState.value = _uiState.value.copy(isPlayingMusic = false)
    }

    override fun onCleared() {
        super.onCleared()
        stopAudio()
        stopMusic()
    }

    fun generateMusic(context: Context, prompt: String) {
        if (_uiState.value.isPlayingMusic) {
            stopMusic()
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMusicLoading = true, error = null)
            try {
                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(
                            role = "user",
                            parts = listOf(Part(text = "Generate a 30-second music track: $prompt"))
                        )
                    ),
                    generationConfig = GenerationConfig(
                        responseModalities = listOf("AUDIO")
                    )
                )
                
                val response = RetrofitClient.service.generateContent("lyria-3-clip-preview", apiKey, request)
                
                val audioBase64 = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull { it.inlineData != null }?.inlineData?.data
                
                if (audioBase64 != null) {
                    playMusic(context, audioBase64)
                } else {
                    _uiState.value = _uiState.value.copy(isMusicLoading = false, error = "No music received")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isMusicLoading = false, error = e.localizedMessage)
            }
        }
    }

    private fun playMusic(context: Context, base64Audio: String) {
        try {
            val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
            val tempFile = File(context.cacheDir, "story_music_${UUID.randomUUID()}.mp3")
            FileOutputStream(tempFile).use { it.write(audioBytes) }

            musicPlayer?.release()
            musicPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    _uiState.value = _uiState.value.copy(isPlayingMusic = false)
                    it.release()
                    musicPlayer = null
                    tempFile.delete()
                }
            }
            _uiState.value = _uiState.value.copy(isMusicLoading = false, isPlayingMusic = true)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isMusicLoading = false, error = "Failed to play music")
        }
    }

    fun toggleHistory() {
        _uiState.value = _uiState.value.copy(showHistory = !_uiState.value.showHistory)
    }

    fun loadStory(story: StoryEntity) {
        currentStoryId = story.id
        textHistory.clear()
        historyIndex = -1
        addToHistory(story.text)
        
        _uiState.value = _uiState.value.copy(
            showHistory = false,
            storyText = story.text,
            chatMessages = listOf(ChatMessage(story.text, isUser = false)),
            imageUri = null
        )
    }

    fun deleteStory(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    fun updateStoryText(newText: String) {
        val currentMessages = _uiState.value.chatMessages.toMutableList()
        if (currentMessages.isNotEmpty()) {
            currentMessages[0] = currentMessages[0].copy(text = newText)
        }
        _uiState.value = _uiState.value.copy(
            storyText = newText,
            chatMessages = currentMessages
        )
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSaveTime > 1000 || Math.abs(newText.length - (textHistory.getOrNull(historyIndex)?.length ?: 0)) > 5) {
            addToHistory(newText)
            lastSaveTime = currentTime
        } else {
            if (historyIndex >= 0) {
                textHistory[historyIndex] = newText
            }
        }
        
        viewModelScope.launch {
            val allText = newText + if (currentMessages.size > 1) "\n\n" + currentMessages.drop(1).joinToString("\n\n") { it.text } else ""
            currentStoryId = repository.insert(StoryEntity(id = currentStoryId ?: 0, text = allText)).toInt()
        }
    }

    private fun uriToBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            val maxDim = 1024
            val scaledBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
            } else {
                bitmap
            }

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }
}

data class StoryUiState(
    val isDarkMode: Boolean = true,
    val imageUri: Uri? = null,
    val storyText: String? = null,
    val chatMessages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isChatLoading: Boolean = false,
    val isAudioLoading: Boolean = false,
    val isPlayingAudio: Boolean = false,
    val isMusicLoading: Boolean = false,
    val isPlayingMusic: Boolean = false,
    val showHistory: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val selectedTone: String = "Whimsical",
    val audioPrompt: String = "",
    val error: String? = null
)
