package no.gunbang.market.domain.market.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import no.gunbang.market.domain.market.entity.Trade;
import no.gunbang.market.domain.market.entity.TradeCount;
import no.gunbang.market.domain.market.repository.TradeCountRepository;
import no.gunbang.market.domain.market.repository.TradeRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeCountScheduler {

    private final TradeRepository tradeRepository;
    private final TradeCountRepository tradeCountRepository;

    @Scheduled(fixedRate = 180000) // 3분마다 실행
    @Transactional
    public void updateTradeCount() {
        log.info("Updating trade count...");

        //최근 3분 이내에 생성된 거래 조회 (최신순 정렬)
        LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(10);
        List<Trade> recentTrades = tradeRepository.findRecentTrades(oneMinuteAgo);

        //item_id 기준으로 거래 개수 집계
        Map<Long, Long> tradeCounts = recentTrades.stream()
            .collect(Collectors.groupingBy(trade -> trade.getMarket().getItem().getId(), Collectors.counting()));

        //기존 trade_count 값을 업데이트(동기화 처리)
        tradeCounts.forEach((itemId, count) -> tradeCountRepository.findById(itemId).ifPresentOrElse(
            tradeCount -> {
                    tradeCount.increaseCount(Math.toIntExact(count));
                    tradeCountRepository.save(tradeCount);
            },
            () -> tradeCountRepository.save(TradeCount.of(itemId, Math.toIntExact(count)))
        ));

        log.info("Trade count updated successfully.");
    }
}
