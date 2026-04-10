package stillframe42.aicodereviewer.config

import org.springframework.ai.transformer.splitter.TokenTextSplitter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class VectorStoreConfig(private val ragProperties: RagProperties) {

    @Bean
    fun tokenTextSplitter(): TokenTextSplitter =
        TokenTextSplitter.builder()
            .withChunkSize(ragProperties.chunkSize)
            .withMinChunkSizeChars(ragProperties.minChunkSizeChars)
            .withMinChunkLengthToEmbed(ragProperties.minChunkLengthToEmbed)
            .withMaxNumChunks(ragProperties.maxNumChunks)
            .withKeepSeparator(ragProperties.keepSeparator)
            .build()
}
