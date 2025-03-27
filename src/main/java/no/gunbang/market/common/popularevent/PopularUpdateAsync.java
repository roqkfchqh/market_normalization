package no.gunbang.market.common.popularevent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import no.gunbang.market.domain.auction.dto.response.AuctionListResponseDto;
import no.gunbang.market.domain.auction.repository.AuctionRepository;
import no.gunbang.market.domain.market.dto.response.MarketPopularResponseDto;
import no.gunbang.market.domain.market.repository.MarketRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.Semaphore;

@Component
@RequiredArgsConstructor
public class PopularUpdateAsync {

    private final StringRedisTemplate redisTemplate;
    private final MarketRepository marketRepository;
    private final AuctionRepository auctionRepository;

    private static final String MARKET_CACHE_KEY = "popular:market:items";
    private static final String AUCTION_CACHE_KEY = "popular:auction:items";
    private static final int POPULAR_LIMIT = 200;

    private final Semaphore marketSemaphore = new Semaphore(1);
    private final Semaphore auctionSemaphore = new Semaphore(1);

    @Async
    @Transactional(readOnly = true)
    @EventListener(ApplicationReadyEvent.class)
    public void updateMarketPopulars() {
        if (!marketSemaphore.tryAcquire()) {
            return;
        }
        try {
            Pageable pageable = PageRequest.of(0, POPULAR_LIMIT);
            Page<MarketPopularResponseDto> result = marketRepository.findPopularMarketItems(getStartDate(), pageable);
            redisTemplate.opsForValue().set(
                    MARKET_CACHE_KEY,
                    serialize(result.getContent())
            );
        } finally {
            marketSemaphore.release();
        }
    }

    @Async
    @Transactional(readOnly = true)
    @EventListener(ApplicationReadyEvent.class)
    public void updateAuctionPopulars() {
        if (!auctionSemaphore.tryAcquire()) {
            return;
        }
        try {
            Pageable pageable = PageRequest.of(0, POPULAR_LIMIT);
            Page<AuctionListResponseDto> result = auctionRepository.findPopularAuctionItems(getStartDate(), pageable);

            redisTemplate.opsForValue().set(
                    AUCTION_CACHE_KEY,
                    serialize(result.getContent())
            );
        } finally {
            auctionSemaphore.release();
        }
    }

    private String serialize(Object value) {
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private LocalDateTime getStartDate() {
        return LocalDateTime.now().minusDays(30);
    }
}
