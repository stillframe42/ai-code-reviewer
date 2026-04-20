package stillframe42.aicodereviewer.evaluation.domain.port.out

import org.springframework.ai.document.Document
import stillframe42.aicodereviewer.evaluation.domain.model.EvaluationScore

interface RagEvaluationPort {

    suspend fun evaluateFaithfulness(
        context: List<Document>,
        generatedReview: String,
    ): EvaluationScore

    suspend fun evaluateContextPrecision(
        query: String,
        retrievedDocs: List<Document>,
        relevantConvention: String,
    ): EvaluationScore

    suspend fun evaluateContextRecall(
        expectedIssues: List<String>,
        retrievedDocs: List<Document>,
    ): EvaluationScore

    suspend fun evaluateAnswerRelevancy(
        inputCode: String,
        expectedIssues: List<String>,
        generatedReview: String,
    ): EvaluationScore
}
