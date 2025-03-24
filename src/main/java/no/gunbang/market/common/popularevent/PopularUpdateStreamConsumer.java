package no.gunbang.market.common.popularevent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PopularUpdateStreamConsumer {

    private final StringRedisTemplate redisTemplate;
    private final PopularUpdateAsync popularUpdateAsync;

    private static final String STREAM_KEY = "stream:popular:update";

    @Scheduled(fixedDelay = 1000) //1초마다 polling
    public void pollStream() {
        List<MapRecord<String, Object, Object>> records =
                redisTemplate.opsForStream().read(StreamOffset.fromStart(STREAM_KEY));

        if (records == null || records.isEmpty()) return;

        log.info("Stream 이벤트 수신됨: {}", records.size());

        //이벤트 누적 개수 계산
        Map<String, Long> countByTarget = records.stream()
                .map(MapRecord::getValue)
                .map(data -> (String) data.get("target"))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        //마켓, 경매 둘 다 별도로 처리
        countByTarget.forEach((target, count) -> {
            if (count >= 3) {
                if (target.equals("MARKET")) {
                    popularUpdateAsync.updateMarketPopulars();
                    redisTemplate.delete(STREAM_KEY);
                }
                else if (target.equals("AUCTION")) {
                    popularUpdateAsync.updateAuctionPopulars();
                    redisTemplate.delete(STREAM_KEY);
                }
            }
        });
    }
}
