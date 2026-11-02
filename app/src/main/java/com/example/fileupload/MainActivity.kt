package com.example.fileupload

import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.fileupload.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.InputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tokenStore = TokenStore(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    App(tokenStore = tokenStore)
                }
            }
        }
    }
}

@Composable
fun App(tokenStore: TokenStore) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var token by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        token = tokenStore.tokenFlow.first()
        isLoading = false
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (token.isNullOrBlank()) {
        TokenScreen(
            onSave = { newToken ->
                scope.launch {
                    tokenStore.saveToken(newToken.trim())
                    token = newToken.trim()
                }
            }
        )
    } else {
        MainScreen(
            token = token!!,
            onLogout = {
                scope.launch {
                    tokenStore.clearToken()
                    token = null
                }
            }
        )
    }
}

@Composable
fun TokenScreen(onSave: (String) -> Unit) {
    var input by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Codeberg FileUpload", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Введите Personal Access Token\n(Settings → Applications на codeberg.org)",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Access Token") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { if (input.isNotBlank()) onSave(input) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Сохранить и войти")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(token: String, onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val api = remember(token) { ApiClient.create(token) }

    var repos by remember { mutableStateOf<List<Repository>>(emptyList()) }
    var isLoadingRepos by remember { mutableStateOf(true) }
    var selectedRepo by remember { mutableStateOf<Repository?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            repos = api.getUserRepos()
            isLoadingRepos = false
        } catch (e: Exception) {
            error = e.message ?: "Ошибка загрузки репозиториев"
            isLoadingRepos = false
        }
    }

    if (selectedRepo == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Мои репозитории") },
                    actions = {
                        IconButton(onClick = onLogout) {
                            Icon(Icons.Default.Logout, contentDescription = "Выйти")
                        }
                    }
                )
            }
        ) { padding ->
            if (isLoadingRepos) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (error != null) {
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {
                        isLoadingRepos = true
                        error = null
                        scope.launch {
                            try {
                                repos = api.getUserRepos()
                            } catch (e: Exception) {
                                error = e.message
                            } finally {
                                isLoadingRepos = false
                            }
                        }
                    }) { Text("Повторить") }
                }
            } else {
                LazyColumn(Modifier.padding(padding)) {
                    items(repos) { repo ->
                        ListItem(
                            headlineContent = { Text(repo.fullName) },
                            supportingContent = {
                                Text(if (repo.private) "Private" else "Public")
                            },
                            modifier = Modifier.clickable { selectedRepo = repo }
                        )
                        Divider()
                    }
                }
            }
        }
    } else {
        UploadScreen(
            repo = selectedRepo!!,
            api = api,
            onBack = { selectedRepo = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(repo: Repository, api: CodebergApi, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var path by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Upload via Android app") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedUri = uri
        selectedFileName = uri?.lastPathSegment ?: "file"
        if (path.isBlank() && selectedFileName != null) {
            path = selectedFileName!!
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(repo.fullName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                label = { Text("Путь в репозитории (например docs/readme.md)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Commit message") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { filePicker.launch("*/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(selectedFileName ?: "Выбрать файл")
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    if (path.isBlank() || selectedUri == null) {
                        Toast.makeText(context, "Укажите путь и выберите файл", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isUploading = true
                    status = null
                    scope.launch {
                        try {
                            val bytes = context.contentResolver.openInputStream(selectedUri!!)!!.use { it.readBytes() }
                            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

                            val owner = repo.owner.login
                            val repoName = repo.name

                            // Пробуем сначала получить файл, чтобы узнать SHA (для update)
                            var existingSha: String? = null
                            try {
                                val existing = api.getContents(owner, repoName, path)
                                if (existing.type == "file") {
                                    existingSha = existing.sha
                                }
                            } catch (_: Exception) {
                                // файла нет — будем create
                            }

                            val response = if (existingSha != null) {
                                api.updateFile(
                                    owner, repoName, path,
                                    UpdateFileOptions(
                                        content = base64,
                                        sha = existingSha,
                                        message = message.ifBlank { null }
                                    )
                                )
                            } else {
                                api.createFile(
                                    owner, repoName, path,
                                    CreateFileOptions(
                                        content = base64,
                                        message = message.ifBlank { null }
                                    )
                                )
                            }

                            if (response.isSuccessful) {
                                status = "Успешно загружено!\n${response.body()?.content?.htmlUrl ?: ""}"
                                Toast.makeText(context, "Файл загружен", Toast.LENGTH_SHORT).show()
                            } else {
                                val errBody = response.errorBody()?.string()
                                status = "Ошибка ${response.code()}: $errBody"
                            }
                        } catch (e: Exception) {
                            status = "Ошибка: ${e.message}"
                        } finally {
                            isUploading = false
                        }
                    }
                },
                enabled = !isUploading && selectedUri != null && path.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isUploading) "Загрузка..." else "Загрузить в репозиторий")
            }

            status?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
