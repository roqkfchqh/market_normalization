package no.gunbang.market.common.popularevent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PopularUpdateStreamConsumer {

    private final StringRedisTemplate redisTemplate;
    private final PopularUpdateAsync popularUpdateAsync;

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
        List<MapRecord<String, Object, Object>> records =
                redisTemplate.opsForStream().read(StreamOffset.fromStart(streamKey));

        if (records == null || records.isEmpty()) return;

        long count = records.size();
        if (count >= 3) {
            onUpdate.run();
            redisTemplate.delete(streamKey);
        }
    }
}
