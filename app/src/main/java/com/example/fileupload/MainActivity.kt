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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import com.example.fileupload.data.ContentsResponse
import com.example.fileupload.data.CreateFileOptions
import com.example.fileupload.data.DeleteFileOptions
import com.example.fileupload.data.Repository
import com.example.fileupload.data.TokenStore
import com.example.fileupload.data.UpdateFileOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
        FileManagerScreen(
            repo = selectedRepo!!,
            api = api,
            onBack = { selectedRepo = null }
        )
    }
}

fun collectLocalFilesRecursive(context: Context, treeUri: Uri): List<Pair<String, Uri>> {
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

suspend fun listRepoDir(api: CodebergApi, owner: String, repo: String, path: String): List<ContentsResponse> {
    return if (path.isBlank()) {
        api.getRootContents(owner, repo)
    } else {
        val body = api.getContentsRaw(owner, repo, path).string()
        val trimmed = body.trim()
        if (trimmed.startsWith("[")) {
            val type = object : TypeToken<List<ContentsResponse>>() {}.type
            Gson().fromJson(trimmed, type)
        } else {
            emptyList()
        }
    }
}

suspend fun collectRepoFilesRecursive(
    api: CodebergApi,
    owner: String,
    repo: String,
    dirPath: String
): List<Pair<String, String>> {
    val result = mutableListOf<Pair<String, String>>()
    val entries = listRepoDir(api, owner, repo, dirPath)
    for (e in entries) {
        val p = e.path ?: continue
        when (e.type) {
            "file" -> {
                val sha = e.sha ?: continue
                result.add(p to sha)
            }
            "dir" -> {
                result.addAll(collectRepoFilesRecursive(api, owner, repo, p))
            }
        }
    }
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(repo: Repository, api: CodebergApi, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val owner = repo.owner.login
    val repoName = repo.name

    var currentPath by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<ContentsResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    var showUpload by remember { mutableStateOf(false) }
    var uploadFileName by remember { mutableStateOf("") }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFolderUri by remember { mutableStateOf<Uri?>(null) }
    var folderFileCount by remember { mutableStateOf(0) }
    var commitMessage by remember { mutableStateOf("Upload via Android app") }

    var deleteTarget by remember { mutableStateOf<ContentsResponse?>(null) }

    fun reload() {
        isLoading = true
        error = null
        scope.launch {
            try {
                entries = withContext(Dispatchers.IO) {
                    listRepoDir(api, owner, repoName, currentPath)
                        .sortedWith(compareBy({ it.type != "dir" }, { it.name ?: "" }))
                }
            } catch (e: Exception) {
                error = e.message
                entries = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(currentPath) {
        reload()
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedFileUri = uri
        selectedFolderUri = null
        folderFileCount = 0
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
            uploadFileName = name
            showUpload = true
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
            uploadFileName = ""
            scope.launch {
                val files = withContext(Dispatchers.IO) {
                    collectLocalFilesRecursive(context, uri)
                }
                folderFileCount = files.size
                showUpload = true
            }
        }
    }

    val pathLabel = if (currentPath.isBlank()) "/" else "/$currentPath"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(repo.fullName, style = MaterialTheme.typography.titleMedium)
                        Text(pathLabel, style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentPath.isBlank()) {
                            onBack()
                        } else {
                            currentPath = currentPath.substringBeforeLast('/', missingDelimiterValue = "")
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { reload() }, enabled = !isBusy) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                    IconButton(onClick = { filePicker.launch("*/*") }, enabled = !isBusy) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Файл")
                    }
                    IconButton(onClick = { folderPicker.launch(null) }, enabled = !isBusy) {
                        Icon(Icons.Default.Upload, contentDescription = "Папка")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { reload() }) { Text("Повторить") }
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (entries.isEmpty()) {
                            item {
                                Text(
                                    "Папка пуста",
                                    modifier = Modifier.padding(24.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        items(entries, key = { it.path ?: it.name ?: "" }) { entry ->
                            val isDir = entry.type == "dir"
                            ListItem(
                                headlineContent = { Text(entry.name ?: "?") },
                                supportingContent = {
                                    Text(
                                        if (isDir) "папка" else "${entry.size ?: 0} байт"
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        if (isDir) Icons.Default.Folder else Icons.Default.Description,
                                        contentDescription = null
                                    )
                                },
                                trailingContent = {
                                    IconButton(
                                        onClick = { deleteTarget = entry },
                                        enabled = !isBusy
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Удалить",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                },
                                modifier = Modifier.clickable(enabled = isDir && !isBusy) {
                                    if (isDir) {
                                        currentPath = entry.path ?: ""
                                    }
                                }
                            )
                            Divider()
                        }
                    }
                }
            }

            status?.let {
                Text(
                    it,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    deleteTarget?.let { target ->
        val isDir = target.type == "dir"
        AlertDialog(
            onDismissRequest = { if (!isBusy) deleteTarget = null },
            title = { Text(if (isDir) "Удалить папку?" else "Удалить файл?") },
            text = {
                Text(
                    if (isDir) {
                        "Папка «${target.name}» и все файлы внутри будут удалены."
                    } else {
                        "Файл «${target.name}» будет удалён из репозитория."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isBusy = true
                        status = null
                        scope.launch {
                            try {
                                if (isDir) {
                                    status = "Сбор файлов папки..."
                                    val files = withContext(Dispatchers.IO) {
                                        collectRepoFilesRecursive(api, owner, repoName, target.path ?: "")
                                    }
                                    if (files.isEmpty()) {
                                        status = "Папка уже пуста"
                                    } else {
                                        val ops = files.map { (path, sha) ->
                                            ChangeFileOperation(
                                                operation = "delete",
                                                path = path,
                                                sha = sha
                                            )
                                        }
                                        var deleted = 0
                                        for (batch in ops.chunked(50)) {
                                            status = "Удаление ${deleted + 1}–${deleted + batch.size} / ${ops.size}"
                                            val resp = api.changeFiles(
                                                owner,
                                                repoName,
                                                ChangeFilesOptions(
                                                    files = batch,
                                                    message = "Delete folder ${target.path}"
                                                )
                                            )
                                            if (!resp.isSuccessful) {
                                                status = "Ошибка ${resp.code()}: ${resp.errorBody()?.string()}"
                                                return@launch
                                            }
                                            deleted += batch.size
                                        }
                                        status = "Удалено $deleted файл(ов)"
                                        Toast.makeText(context, "Папка удалена", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    val sha = target.sha
                                    if (sha == null) {
                                        status = "Нет SHA файла"
                                        return@launch
                                    }
                                    val resp = api.deleteFile(
                                        owner,
                                        repoName,
                                        target.path ?: target.name ?: "",
                                        DeleteFileOptions(
                                            sha = sha,
                                            message = "Delete ${target.name}"
                                        )
                                    )
                                    if (resp.isSuccessful) {
                                        status = "Файл удалён"
                                        Toast.makeText(context, "Удалено", Toast.LENGTH_SHORT).show()
                                    } else {
                                        status = "Ошибка ${resp.code()}: ${resp.errorBody()?.string()}"
                                    }
                                }
                                deleteTarget = null
                                reload()
                            } catch (e: Exception) {
                                status = "Ошибка: ${e.message}"
                            } finally {
                                isBusy = false
                            }
                        }
                    },
                    enabled = !isBusy
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }, enabled = !isBusy) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showUpload) {
        val isFolder = selectedFolderUri != null
        AlertDialog(
            onDismissRequest = {
                if (!isBusy) {
                    showUpload = false
                    selectedFileUri = null
                    selectedFolderUri = null
                }
            },
            title = { Text(if (isFolder) "Загрузить папку" else "Загрузить файл") },
            text = {
                Column {
                    Text("Текущая папка в репо: $pathLabel")
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isFolder) {
                        Text("Файлов: $folderFileCount")
                        Text("Структура подпапок сохранится относительно $pathLabel")
                    } else {
                        OutlinedTextField(
                            value = uploadFileName,
                            onValueChange = { uploadFileName = it },
                            label = { Text("Имя файла в репозитории") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = commitMessage,
                        onValueChange = { commitMessage = it },
                        label = { Text("Commit message") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isBusy = true
                        status = null
                        scope.launch {
                            try {
                                if (isFolder && selectedFolderUri != null) {
                                    status = "Сканирование..."
                                    val files = withContext(Dispatchers.IO) {
                                        collectLocalFilesRecursive(context, selectedFolderUri!!)
                                    }
                                    if (files.isEmpty()) {
                                        status = "В папке нет файлов"
                                        return@launch
                                    }
                                    val operations = mutableListOf<ChangeFileOperation>()
                                    var n = 0
                                    for ((rel, uri) in files) {
                                        n++
                                        status = "Читаю $n / ${files.size}"
                                        val bytes = withContext(Dispatchers.IO) {
                                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                        } ?: continue
                                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                                        val full = if (currentPath.isBlank()) rel
                                        else currentPath.trimEnd('/') + "/" + rel
                                        operations.add(
                                            ChangeFileOperation(
                                                operation = "create",
                                                path = full,
                                                content = b64
                                            )
                                        )
                                    }
                                    var uploaded = 0
                                    for (batch in operations.chunked(50)) {
                                        status = "Загрузка ${uploaded + 1}–${uploaded + batch.size} / ${operations.size}"
                                        val resp = api.changeFiles(
                                            owner,
                                            repoName,
                                            ChangeFilesOptions(
                                                files = batch,
                                                message = commitMessage.ifBlank { "Upload folder" }
                                            )
                                        )
                                        if (!resp.isSuccessful) {
                                            status = "Ошибка ${resp.code()}: ${resp.errorBody()?.string()}"
                                            return@launch
                                        }
                                        uploaded += batch.size
                                    }
                                    status = "Загружено $uploaded файл(ов)"
                                    Toast.makeText(context, "Готово", Toast.LENGTH_SHORT).show()
                                } else if (selectedFileUri != null) {
                                    val name = uploadFileName.ifBlank {
                                        selectedFileUri!!.lastPathSegment?.substringAfterLast('/') ?: "file"
                                    }
                                    val fullPath = if (currentPath.isBlank()) name
                                    else currentPath.trimEnd('/') + "/" + name
                                    status = "Загрузка $fullPath..."
                                    val bytes = withContext(Dispatchers.IO) {
                                        context.contentResolver.openInputStream(selectedFileUri!!)!!
                                            .use { it.readBytes() }
                                    }
                                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

                                    var existingSha: String? = null
                                    try {
                                        val existing = api.getContents(owner, repoName, fullPath)
                                        if (existing.type == "file") existingSha = existing.sha
                                    } catch (_: Exception) {
                                    }

                                    val resp = if (existingSha != null) {
                                        api.updateFile(
                                            owner, repoName, fullPath,
                                            UpdateFileOptions(
                                                content = b64,
                                                sha = existingSha,
                                                message = commitMessage.ifBlank { null }
                                            )
                                        )
                                    } else {
                                        api.createFile(
                                            owner, repoName, fullPath,
                                            CreateFileOptions(
                                                content = b64,
                                                message = commitMessage.ifBlank { null }
                                            )
                                        )
                                    }
                                    if (resp.isSuccessful) {
                                        status = "Файл загружен: $fullPath"
                                        Toast.makeText(context, "Загружено", Toast.LENGTH_SHORT).show()
                                    } else {
                                        status = "Ошибка ${resp.code()}: ${resp.errorBody()?.string()}"
                                    }
                                }
                                showUpload = false
                                selectedFileUri = null
                                selectedFolderUri = null
                                reload()
                            } catch (e: Exception) {
                                status = "Ошибка: ${e.message}"
                            } finally {
                                isBusy = false
                            }
                        }
                    },
                    enabled = !isBusy && (selectedFileUri != null || (selectedFolderUri != null && folderFileCount > 0))
                ) {
                    Text("Загрузить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUpload = false
                        selectedFileUri = null
                        selectedFolderUri = null
                    },
                    enabled = !isBusy
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}
