package no.gunbang.market.common.scheduler;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import no.gunbang.market.common.entity.QItem;
import no.gunbang.market.common.entity.Status;
import no.gunbang.market.domain.auction.entity.QAuction;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CountCacheScheduler {

    private final RedisTemplate<String, Long> redisTemplate;
    private final JPAQueryFactory queryFactory;

    private static final String ITEM_COUNT_KEY = "count:item";
    private static final String AUCTION_COUNT_KEY = "count:auction";

    public Long getCachedMarketCount() {
        return redisTemplate.opsForValue().get(ITEM_COUNT_KEY);
    }

    public Long getCachedAuctionCount() {
        return redisTemplate.opsForValue().get(AUCTION_COUNT_KEY);
    }

    @Scheduled(fixedRate = 300000) // 5분마다 갱신
    public void updateItemCount() {
        Long count = queryFactory
                .select(QItem.item.countDistinct())
                .from(QItem.item)
                .fetchOne();

        redisTemplate.opsForValue().set(ITEM_COUNT_KEY, count != null ? count : 0L);
    }

    @Scheduled(fixedRate = 300000)
    public void updateAuctionCount() {
        Long count = queryFactory
                .select(QAuction.auction.countDistinct())
                .from(QAuction.auction)
                .where(QAuction.auction.status.eq(Status.ON_SALE),
                        QAuction.auction.createdAt.goe(getStartDate()))
                .fetchOne();

        redisTemplate.opsForValue().set(AUCTION_COUNT_KEY, count != null ? count : 0L);
    }

    private LocalDateTime getStartDate() {
        return LocalDateTime.now().minusDays(30);
    }
}