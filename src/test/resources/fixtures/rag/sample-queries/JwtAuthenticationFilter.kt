package stillframe42.aicodereviewer.security

import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

// JWT 인증 필터 fixture — RAG 측정 테스트용 placeholder
// 실제 프로덕션 코드는 아니며, FileCategoryMapper의 SECURITY 카테고리 매핑 검증 목적으로만 존재한다.
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = extractToken(request)
        if (token != null && jwtTokenProvider.validate(token)) {
            val auth = jwtTokenProvider.getAuthentication(token)
            SecurityContextHolder.getContext().authentication = auth
        }
        filterChain.doFilter(request, response)
    }

    private fun extractToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization") ?: return null
        return if (header.startsWith("Bearer ")) header.substring(7) else null
    }
}

// 토큰 발급/검증 책임 — placeholder 인터페이스
interface JwtTokenProvider {
    fun validate(token: String): Boolean
    fun getAuthentication(token: String): org.springframework.security.core.Authentication
}
