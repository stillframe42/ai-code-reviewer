package stillframe42.aicodereviewer.rag.domain.service

import org.springframework.ai.document.Document

// RRF 공식: score(d) = Σ 1 / (k + rank_i(d))
// k=60: 논문(Cormack et al. 2009) 표준값. 상위 순위 간 점수 차이를 평탄화한다.
// forEachIndexed의 index는 0부터 시작, RRF rank는 1부터 시작이므로 rank + 1 사용.
fun reciprocalRankFusion(
    vectorResults: List<Document>,
    keywordResults: List<Document>,
    topK: Int,
    k: Int = 60,
): List<Document> {
    val scores = mutableMapOf<String, Double>()
    scores.accumulate(vectorResults, k)
    scores.accumulate(keywordResults, k)

    // 동일 id가 있으면 keywordResults 버전이 남는다(Last-Wins).
    // 두 어댑터 모두 vector_store 동일 테이블에서 조회하므로 내용이 같아 순위에 영향 없음.
    val allDocuments = (vectorResults + keywordResults).associateBy { it.id }

    return scores.entries
        .sortedByDescending { it.value }
        .take(topK)
        .mapNotNull { (id, _) -> allDocuments[id] }
}

// 결과 목록의 RRF 점수를 수신 맵에 누적한다.
private fun MutableMap<String, Double>.accumulate(results: List<Document>, k: Int) {
    results.forEachIndexed { rank, doc ->
        doc.id.let { id ->
            this[id] = (this[id] ?: 0.0) + 1.0 / (k + rank + 1)
        }
    }
}
