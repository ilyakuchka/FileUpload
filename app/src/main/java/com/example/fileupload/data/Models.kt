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
    val content: String, // base64
    val message: String? = null,
    val branch: String? = null
)

data class UpdateFileOptions(
    val content: String, // base64
    val sha: String,
    val message: String? = null,
    val branch: String? = null
)

data class FileResponse(
    val content: ContentInfo?,
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

data class ContentsResponse(
    val name: String?,
    val path: String?,
    val sha: String?,
    val type: String?, // file or dir
    val content: String? // base64 if file
)

data class ApiError(
    val message: String?,
    val url: String?
)
