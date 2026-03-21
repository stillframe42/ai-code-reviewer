// 픽스처: SQL Injection 취약점 — SECURITY/CRITICAL 이슈 감지 테스트용
// 기대: SECURITY 카테고리, CRITICAL 심각도 이슈 최소 2개

@Repository
class UserRepository(private val db: JdbcTemplate) {

    // 취약점 1: 문자열 직접 연결로 SQL Injection 가능
    fun findByUsername(username: String): User? {
        val sql = "SELECT * FROM users WHERE username = '" + username + "'"
        return db.queryForObject(sql, User::class.java)
    }

    // 취약점 2: 문자열 템플릿으로 SQL Injection 가능
    fun findByEmail(email: String): User? {
        val sql = "SELECT * FROM users WHERE email = '$email'"
        return db.queryForObject(sql, User::class.java)
    }

    // 취약점 3: 검색 필터에도 동일 패턴 반복
    fun searchUsers(keyword: String): List<User> {
        val sql = "SELECT * FROM users WHERE name LIKE '%" + keyword + "%' OR email LIKE '%" + keyword + "%'"
        return db.query(sql, UserRowMapper())
    }
}
