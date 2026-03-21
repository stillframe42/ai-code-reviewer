// 픽스처: 비효율적 루프/컬렉션 연산 — PERFORMANCE/MAJOR 이슈 감지 테스트용
// 기대: PERFORMANCE 카테고리, MAJOR 이상 심각도 이슈 최소 1개

@Service
class ReportService {

    // 문제 1: O(n²) 중첩 루프 — 중복 항목 탐지
    fun findDuplicates(items: List<String>): List<String> {
        val duplicates = mutableListOf<String>()
        for (i in items.indices) {
            for (j in i + 1 until items.size) {
                if (items[i] == items[j] && !duplicates.contains(items[i])) {
                    duplicates.add(items[i])
                }
            }
        }
        return duplicates
    }

    // 문제 2: 불필요한 중간 컬렉션 생성 + 루프 내 반복 contains 호출
    fun filterActiveUserIds(users: List<User>, blockedIds: List<Long>): List<Long> {
        val activeUsers = users.filter { it.active }          // 중간 컬렉션 1
        val activeUserIds = activeUsers.map { it.id }         // 중간 컬렉션 2
        val result = mutableListOf<Long>()
        for (id in activeUserIds) {
            if (!blockedIds.contains(id)) {                   // O(n) contains를 매 반복마다 호출
                result.add(id)
            }
        }
        return result
    }

    // 문제 3: 문자열 루프 내 반복 연결 (StringBuilder 미사용)
    fun buildReport(entries: List<String>): String {
        var report = ""
        for (entry in entries) {
            report += "$entry\n"  // 매 반복마다 새 String 객체 생성
        }
        return report
    }
}
