package com.example.fileupload

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.example.fileupload.data.ApiClient
import com.example.fileupload.data.ChangeFileOperation
import com.example.fileupload.data.ChangeFilesOptions
import com.example.fileupload.data.CodebergApi
import com.example.fileupload.data.CreateFileOptions
import com.example.fileupload.data.Repository
import com.example.fileupload.data.UpdateFileOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    var token by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        token = tokenStore.tokenFlow.first()
        isLoading = false
    }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
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
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Введите Personal Access Token\n(Settings → Applications на codeberg.org)",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Access Token") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
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
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (error != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
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
                    }) {
                        Text("Повторить")
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.padding(padding)) {
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

fun collectFilesRecursive(context: Context, treeUri: Uri): List<Pair<String, Uri>> {
    val result = mutableListOf<Pair<String, Uri>>()
    val root = DocumentFile.fromTreeUri(context, treeUri) ?: return result

    fun walk(dir: DocumentFile, relative: String) {
        for (child in dir.listFiles()) {
            val name = child.name ?: continue
            val path = if (relative.isEmpty()) name else "$relative/$name"
            when {
                child.isDirectory -> walk(child, path)
                child.isFile -> result.add(path to child.uri)
            }
        }
    }
    walk(root, "")
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(repo: Repository, api: CodebergApi, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var basePath by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Upload via Android app") }

    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }

    var selectedFolderUri by remember { mutableStateOf<Uri?>(null) }
    var folderFileCount by remember { mutableStateOf(0) }

    var isUploading by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedFileUri = uri
        selectedFolderUri = null
        folderFileCount = 0
        selectedFileName = uri?.lastPathSegment ?: "file"
        if (basePath.isBlank() && selectedFileName != null) {
            basePath = selectedFileName!!
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }

            selectedFolderUri = uri
            selectedFileUri = null
            selectedFileName = null
            scope.launch {
                val files = withContext(Dispatchers.IO) {
                    collectFilesRecursive(context, uri)
                }
                folderFileCount = files.size
            }
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = basePath,
                onValueChange = { basePath = it },
                label = {
                    Text(
                        if (selectedFolderUri != null) {
                            "Базовый путь в репо (например docs/ или пусто)"
                        } else {
                            "Путь файла в репозитории"
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Commit message") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { filePicker.launch("*/*") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Файл")
                }
                Button(
                    onClick = { folderPicker.launch(null) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Папка")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when {
                selectedFolderUri != null -> {
                    Text(
                        "Выбрана папка: $folderFileCount файл(ов)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                selectedFileName != null -> {
                    Text(
                        "Выбран файл: $selectedFileName",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val hasFile = selectedFileUri != null
                    val hasFolder = selectedFolderUri != null
                    if (!hasFile && !hasFolder) {
                        Toast.makeText(context, "Выберите файл или папку", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (hasFile && basePath.isBlank()) {
                        Toast.makeText(context, "Укажите путь для файла", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isUploading = true
                    status = null
                    progressText = null

                    scope.launch {
                        try {
                            val owner = repo.owner.login
                            val repoName = repo.name

                            if (hasFolder) {
                                progressText = "Сканирование папки..."
                                val files = withContext(Dispatchers.IO) {
                                    collectFilesRecursive(context, selectedFolderUri!!)
                                }
                                if (files.isEmpty()) {
                                    status = "В папке нет файлов"
                                    return@launch
                                }

                                val operations = mutableListOf<ChangeFileOperation>()
                                var processed = 0
                                for ((relPath, uri) in files) {
                                    processed++
                                    progressText = "Читаю $processed / ${files.size}: $relPath"

                                    val bytes = withContext(Dispatchers.IO) {
                                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                    } ?: continue

                                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                                    val fullPath = if (basePath.isBlank()) {
                                        relPath
                                    } else {
                                        basePath.trimEnd('/') + "/" + relPath
                                    }

                                    operations.add(
                                        ChangeFileOperation(
                                            operation = "create",
                                            path = fullPath,
                                            content = base64
                                        )
                                    )
                                }

                                val batchSize = 50
                                var uploaded = 0
                                for (batch in operations.chunked(batchSize)) {
                                    progressText =
                                        "Загрузка ${uploaded + 1}–${uploaded + batch.size} / ${operations.size}"
                                    val response = api.changeFiles(
                                        owner,
                                        repoName,
                                        ChangeFilesOptions(
                                            files = batch,
                                            message = message.ifBlank {
                                                "Upload folder (${batch.size} files)"
                                            }
                                        )
                                    )
                                    if (!response.isSuccessful) {
                                        val err = response.errorBody()?.string()
                                        status =
                                            "Ошибка ${response.code()} на файлах ${uploaded + 1}+ : $err"
                                        return@launch
                                    }
                                    uploaded += batch.size
                                }

                                status = "Успешно загружено $uploaded файл(ов) из папки"
                                Toast.makeText(
                                    context,
                                    "Папка загружена ($uploaded файлов)",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                progressText = "Загрузка файла..."
                                val bytes = withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(selectedFileUri!!)!!
                                        .use { it.readBytes() }
                                }
                                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                                val path = basePath

                                var existingSha: String? = null
                                try {
                                    val existing = api.getContents(owner, repoName, path)
                                    if (existing.type == "file") {
                                        existingSha = existing.sha
                                    }
                                } catch (_: Exception) {
                                }

                                val response = if (existingSha != null) {
                                    api.updateFile(
                                        owner,
                                        repoName,
                                        path,
                                        UpdateFileOptions(
                                            content = base64,
                                            sha = existingSha,
                                            message = message.ifBlank { null }
                                        )
                                    )
                                } else {
                                    api.createFile(
                                        owner,
                                        repoName,
                                        path,
                                        CreateFileOptions(
                                            content = base64,
                                            message = message.ifBlank { null }
                                        )
                                    )
                                }

                                if (response.isSuccessful) {
                                    status =
                                        "Успешно загружено!\n${response.body()?.content?.htmlUrl ?: ""}"
                                    Toast.makeText(context, "Файл загружен", Toast.LENGTH_SHORT)
                                        .show()
                                } else {
                                    status =
                                        "Ошибка ${response.code()}: ${response.errorBody()?.string()}"
                                }
                            }
                        } catch (e: Exception) {
                            status = "Ошибка: ${e.message}"
                        } finally {
                            isUploading = false
                            progressText = null
                        }
                    }
                },
                enabled = !isUploading && (selectedFileUri != null || selectedFolderUri != null),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    when {
                        isUploading -> "Загрузка..."
                        selectedFolderUri != null -> "Загрузить папку ($folderFileCount файлов)"
                        else -> "Загрузить файл"
                    }
                )
            }

            progressText?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }

            status?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
