package com.sunasterisk.socialanalytics.messaging;

import com.sunasterisk.socialanalytics.entity.PostStatus;
import com.sunasterisk.socialanalytics.entity.SocialProvider;
import com.sunasterisk.socialanalytics.messaging.ImportStatsCache.ImportStats;
import com.sunasterisk.socialanalytics.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Lắng nghe message từ queue IMPORT_COMPLETED và tính lại thống kê post tổng hợp. */
@Component
@RequiredArgsConstructor
public class ImportEventListener {

    private static final Logger log = LoggerFactory.getLogger(ImportEventListener.class);

    private final PostRepository postRepository;
    private final ImportStatsCache importStatsCache;

    /**
     * Khi có exception: re-throw để Spring JMS NACK message → Artemis tự retry →
     * chuyển sang DLQ.IMPORT_COMPLETED sau khi hết số lần retry cho phép.
     */
    @JmsListener(destination = JmsQueues.IMPORT_COMPLETED)
    public void onImportCompleted(ImportCompletedMessage message) {
        try {
            long totalPosts = postRepository.countByStatus(PostStatus.ACTIVE);
            long facebook   = postRepository.countByPlatformAndStatus(SocialProvider.FACEBOOK, PostStatus.ACTIVE);
            long twitter    = postRepository.countByPlatformAndStatus(SocialProvider.TWITTER,  PostStatus.ACTIVE);

            ImportStats stats = new ImportStats(totalPosts, Map.of(
                    SocialProvider.FACEBOOK.name(), facebook,
                    SocialProvider.TWITTER.name(),  twitter
            ));
            importStatsCache.update(stats);

            log.info("Stats recalculated after batchId={}: total={}, facebook={}, twitter={}",
                    message.batchId(), totalPosts, facebook, twitter);
        } catch (Exception ex) {
            log.error("Stats recalculation failed for batchId={}: {}",
                    message.batchId(), ex.getMessage(), ex);
            throw ex;   // NACK → Artemis retry → DLQ sau khi hết số lần thử
        }
    }
}
