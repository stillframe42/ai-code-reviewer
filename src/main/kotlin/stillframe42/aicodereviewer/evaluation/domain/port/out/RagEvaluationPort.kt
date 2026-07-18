package stillframe42.aicodereviewer.evaluation.domain.port.out

import stillframe42.aicodereviewer.evaluation.domain.model.EvaluationScore
import stillframe42.aicodereviewer.rag.domain.model.RagDocument

interface RagEvaluationPort {

    fun evaluateFaithfulness(
        context: List<RagDocument>,
        generatedReview: String,
    ): EvaluationScore

    fun evaluateContextPrecision(
        query: String,
        retrievedDocs: List<RagDocument>,
        relevantConvention: String,
    ): EvaluationScore

    fun evaluateContextRecall(
        expectedIssues: List<String>,
        retrievedDocs: List<RagDocument>,
    ): EvaluationScore

    fun evaluateAnswerRelevancy(
        inputCode: String,
        expectedIssues: List<String>,
        generatedReview: String,
    ): EvaluationScore
}
