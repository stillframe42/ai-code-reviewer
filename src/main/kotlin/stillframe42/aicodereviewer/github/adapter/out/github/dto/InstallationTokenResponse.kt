package stillframe42.aicodereviewer.github.adapter.out.github.dto

import com.fasterxml.jackson.annotation.JsonProperty

// GitHub API POST /app/installations/{id}/access_tokens 응답 DTO
data class InstallationTokenResponse(
    val token: String,
    @param:JsonProperty("expires_at") val expiresAt: String,  // ISO 8601 형식 (예: "2026-03-23T01:00:00Z")
)
