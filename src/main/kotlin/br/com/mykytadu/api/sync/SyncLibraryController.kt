package br.com.mykytadu.api.sync

import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@RestController
@Profile("!test")
@RequestMapping("/api/v1/sync/library")
@PreAuthorize("isAuthenticated()")
internal class SyncLibraryController {

    private val userLibraries = ConcurrentHashMap<String, ConcurrentHashMap<String, LibraryEntrySyncDto>>()

    @PostMapping
    fun syncLibrary(
        @AuthenticationPrincipal principal: Jwt?,
        @RequestBody request: SyncBatchRequestDto,
    ): ResponseEntity<SyncBatchResponseDto> {
        val userId = principal?.subject ?: "anonymous"
        val userEntries = userLibraries.computeIfAbsent(userId) { ConcurrentHashMap() }
        val now = Instant.now().toEpochMilli()

        var appliedCount = 0
        for (mutation in request.mutations) {
            when (mutation.type) {
                SyncMutationType.UPSERT -> {
                    mutation.payload?.let { entry ->
                        userEntries[entry.animeId] = entry
                        appliedCount++
                    }
                }

                SyncMutationType.DELETE -> {
                    if (userEntries.remove(mutation.animeId) != null) {
                        appliedCount++
                    }
                }
            }
        }

        val serverChanges = if (request.lastSyncedAtEpochMillis != null) {
            userEntries.values
                .filter { it.updatedAtEpochMillis > request.lastSyncedAtEpochMillis }
                .toList()
        } else {
            emptyList()
        }

        val response = SyncBatchResponseDto(
            syncedAtEpochMillis = now,
            appliedCount = appliedCount,
            serverChanges = serverChanges,
            deletedAnimeIds = emptyList(),
        )

        return ResponseEntity.ok(response)
    }
}
