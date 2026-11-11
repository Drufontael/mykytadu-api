package br.com.mykytadu.api.sync

enum class SyncMutationType {
    UPSERT,
    DELETE,
}

data class LibraryEntrySyncDto(
    val animeId: String,
    val status: String,
    val progressCurrent: Int = 0,
    val progressTotal: Int? = null,
    val isFavorite: Boolean = false,
    val rating: Int? = null,
    val notes: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

data class SyncMutationDto(
    val animeId: String,
    val type: SyncMutationType,
    val payload: LibraryEntrySyncDto? = null,
    val timestampEpochMillis: Long,
)

data class SyncBatchRequestDto(
    val lastSyncedAtEpochMillis: Long? = null,
    val mutations: List<SyncMutationDto> = emptyList(),
)

data class SyncBatchResponseDto(
    val syncedAtEpochMillis: Long,
    val appliedCount: Int = 0,
    val serverChanges: List<LibraryEntrySyncDto> = emptyList(),
    val deletedAnimeIds: List<String> = emptyList(),
)
