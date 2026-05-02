package stillframe42.aicodereviewer.rag.application

import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory

// 관측(span) 입력 맵에서 카테고리 필터를 라벨로 표기할 때 사용한다.
// null 은 "필터 없음 = 전체 카테고리 검색" 을 의미하므로 "ALL" 로 평탄화한다.
fun ConventionCategory?.nameOrAll(): String = this?.name ?: "ALL"
