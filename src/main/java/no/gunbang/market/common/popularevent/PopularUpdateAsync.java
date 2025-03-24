package no.gunbang.market.common.popularevent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import no.gunbang.market.domain.auction.dto.response.AuctionListResponseDto;
import no.gunbang.market.domain.auction.repository.AuctionRepository;
import no.gunbang.market.domain.market.dto.response.MarketPopularResponseDto;
import no.gunbang.market.domain.market.repository.MarketRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class PopularUpdateAsync {

    private final StringRedisTemplate redisTemplate;
    private final MarketRepository marketRepository;
    private final AuctionRepository auctionRepository;

    private static final String MARKET_CACHE_KEY = "popular:market:items";
    private static final String AUCTION_CACHE_KEY = "popular:auction:items";
    private static final int POPULAR_LIMIT = 200;

    @Async
    @PostConstruct
    public void updateMarketPopulars() {
        Pageable pageable = PageRequest.of(0, POPULAR_LIMIT);
        Page<MarketPopularResponseDto> result = marketRepository.findPopularMarketItems(getStartDate(), pageable);

        redisTemplate.opsForValue().set(
                MARKET_CACHE_KEY,
                serialize(result.getContent())
        );
    }

    @Async
    @PostConstruct
    public void updateAuctionPopulars() {
        Pageable pageable = PageRequest.of(0, POPULAR_LIMIT);
        Page<AuctionListResponseDto> result = auctionRepository.findPopularAuctionItems(getStartDate(), pageable);

        redisTemplate.opsForValue().set(
                AUCTION_CACHE_KEY,
                serialize(result.getContent())
        );
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
