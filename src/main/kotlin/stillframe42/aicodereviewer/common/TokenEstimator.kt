package stillframe42.aicodereviewer.common

import kotlin.math.ceil

// 텍스트 길이 기반 토큰 수 간이 추정 유틸 (실제 토크나이저 없이 4자 ≈ 1토큰 규칙 사용)
object TokenEstimator {
    fun estimate(text: String): Int = ceil(text.length / 4.0).toInt()
}
