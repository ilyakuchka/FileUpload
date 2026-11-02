package com.example.fileupload.data

import com.google.gson.annotations.SerializedName

data class Repository(
    val id: Long,
    val name: String,
    @SerializedName("full_name") val fullName: String,
    val private: Boolean,
    val description: String?,
    val owner: Owner
)

data class Owner(
    val login: String,
    val id: Long
)

data class CreateFileOptions(
    val content: String,
    val message: String? = null,
    val branch: String? = null
)

data class UpdateFileOptions(
    val content: String,
    val sha: String,
    val message: String? = null,
    val branch: String? = null
)

data class DeleteFileOptions(
    val sha: String,
    val message: String? = null,
    val branch: String? = null
)

data class ChangeFileOperation(
    val operation: String,
    val path: String,
    val content: String? = null,
    val sha: String? = null
)

data class ChangeFilesOptions(
    val files: List<ChangeFileOperation>,
    val message: String? = null,
    val branch: String? = null
)

data class FileResponse(
    val content: ContentInfo?,
    val commit: CommitInfo?
)

data class FilesResponse(
    val files: List<ContentInfo>?,
    val commit: CommitInfo?
)

data class ContentInfo(
    val name: String?,
    val path: String?,
    val sha: String?,
    @SerializedName("html_url") val htmlUrl: String?
)

data class CommitInfo(
    val sha: String?,
    val message: String?
)

/** Single entry or element of directory listing */
data class ContentsResponse(
    val name: String? = null,
    val path: String? = null,
    val sha: String? = null,
    val type: String? = null,
    val size: Long? = null,
    val content: String? = null,
    @SerializedName("download_url") val downloadUrl: String? = null,
    @SerializedName("html_url") val htmlUrl: String? = null
)

data class ApiError(
    val message: String?,
    val url: String?
)
