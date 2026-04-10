package stillframe42.aicodereviewer.config

import org.springframework.ai.transformer.splitter.TokenTextSplitter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class VectorStoreConfig {

    // 컨벤션 문서 청킹 설정
    // chunkSize=512: 코드 예제 포함 섹션을 하나의 청크로 유지
    // keepSeparator=true: 마크다운 섹션 구분자(--)를 청크 경계로 보존
    @Bean
    fun tokenTextSplitter(): TokenTextSplitter =
        TokenTextSplitter.builder()
            .withChunkSize(512)
            .withMinChunkSizeChars(100)
            .withMinChunkLengthToEmbed(50)
            .withMaxNumChunks(10000)
            .withKeepSeparator(true)
            .build()
}
