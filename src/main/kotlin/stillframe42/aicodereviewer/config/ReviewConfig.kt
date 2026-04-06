package stillframe42.aicodereviewer.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ReviewProperties::class, AiReviewerProperties::class)
class ReviewConfig
