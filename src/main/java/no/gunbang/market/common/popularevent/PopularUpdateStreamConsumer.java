package no.gunbang.market.common.popularevent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class PopularUpdateStreamConsumer {

    private final StringRedisTemplate redisTemplate;
    private final PopularUpdateAsync popularUpdateAsync;
    private final RedissonClient redissonClient;

    private static final String STREAM_PREFIX = "stream:popular:update:";

    @Scheduled(fixedDelay = 1000)
    public void pollMarketStream() {
        pollStreamForTarget("MARKET", popularUpdateAsync::updateMarketPopulars);
    }

    @Scheduled(fixedDelay = 1000)
    public void pollAuctionStream() {
        pollStreamForTarget("AUCTION", popularUpdateAsync::updateAuctionPopulars);
    }

    private void pollStreamForTarget(String target, Runnable onUpdate) {
        String streamKey = STREAM_PREFIX + target.toLowerCase();
        String lockKey = "lock:" + streamKey;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(100, 1000, TimeUnit.MILLISECONDS);
            if (!locked) {
                log.info("락을 획득하지 못했습니다. streamKey: {}", streamKey);
                return;
            }
            List<MapRecord<String, Object, Object>> records =
                    redisTemplate.opsForStream().read(StreamOffset.fromStart(streamKey));

            if (records == null || records.isEmpty()) return;

            long count = records.size();
            if (count >= 3) {
                onUpdate.run();
                redisTemplate.delete(streamKey);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("락 획득 도중 인터럽트 발생. streamKey: {}", streamKey, e);
        } finally {
            if (locked) {
                lock.unlock();
            }
        }
    }
}
