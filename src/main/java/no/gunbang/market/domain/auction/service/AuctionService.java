package no.gunbang.market.domain.auction.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import no.gunbang.market.common.entity.Item;
import no.gunbang.market.common.entity.ItemRepository;
import no.gunbang.market.common.entity.Status;
import no.gunbang.market.common.aop.annotation.SemaphoreLock;
import no.gunbang.market.common.exception.CustomException;
import no.gunbang.market.common.exception.ErrorCode;
import no.gunbang.market.common.popularevent.PopularUpdateTarget;
import no.gunbang.market.common.popularevent.TriggerPopularUpdate;
import no.gunbang.market.domain.auction.cursor.AuctionCursorValues;
import no.gunbang.market.domain.auction.dto.request.AuctionRegistrationRequestDto;
import no.gunbang.market.domain.auction.dto.request.BidAuctionRequestDto;
import no.gunbang.market.domain.auction.dto.response.AuctionListResponseDto;
import no.gunbang.market.domain.auction.dto.response.AuctionRegistrationResponseDto;
import no.gunbang.market.domain.auction.dto.response.AuctionResponseDto;
import no.gunbang.market.domain.auction.dto.response.BidAuctionResponseDto;
import no.gunbang.market.domain.auction.entity.Auction;
import no.gunbang.market.domain.auction.entity.Bid;
import no.gunbang.market.domain.auction.repository.AuctionRepository;
import no.gunbang.market.domain.auction.repository.BidRepository;
import no.gunbang.market.domain.user.entity.User;
import no.gunbang.market.domain.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AuctionService {

    private final StringRedisTemplate redisTemplate;
    private final AuctionRepository auctionRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final BidRepository bidRepository;
    private final AuctionScheduler auctionScheduler;

    public List<AuctionListResponseDto> getPopularsCursor(Long lastBidderCount, Long lastAuctionId) {
        return auctionRepository.findPopularAuctionItemsCursor(
            getStartDate(),
            lastBidderCount,
            lastAuctionId
        );
    }

    public List<AuctionListResponseDto> getAllAuctionsCursor(
        Long lastAuctionId,
        String searchKeyword,
        String sortBy,
        String sortDirection,
        AuctionCursorValues auctionCursorValues
    ) {
        return auctionRepository.findAllAuctionItemsCursor(
            getStartDate(),
            searchKeyword,
            sortBy,
            sortDirection,
            lastAuctionId,
            auctionCursorValues
        );
    }

    public Page<AuctionListResponseDto> getPopulars(Pageable pageable) {
        String json = redisTemplate.opsForValue().get("popular:auction:items");
        if (json == null) {
            //fallback: 기존 쿼리
            return auctionRepository.findPopularAuctionItems(getStartDate(), pageable);
        }

        List<AuctionListResponseDto> fullList = deserialize(json);

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), fullList.size());

        if (start >= fullList.size()) {
            return Page.empty(pageable);
        }

        List<AuctionListResponseDto> pageContent = fullList.subList(start, end);
        return new PageImpl<>(pageContent, pageable, fullList.size());
    }


    public Page<AuctionListResponseDto> getAllAuctions(
            Pageable pageable,
            String searchKeyword,
            String sortBy,
            String sortDirection
    ) {
        return auctionRepository.findAllAuctionItems(
                getStartDate(),
                searchKeyword,
                sortBy,
                sortDirection,
                pageable
        );
    }

    public AuctionResponseDto getAuctionById(Long auctionId) {

        Auction foundAuction = findAuctionById(auctionId);

        Optional<Bid> currentBid = bidRepository.findByAuction(foundAuction);

        long currentMaxPrice = currentBid.map(Bid::getBidPrice)
            .orElse(foundAuction.getStartingPrice());

        return AuctionResponseDto.toDto(
            foundAuction,
            currentMaxPrice
        );
    }

    @Transactional
    public AuctionRegistrationResponseDto registerAuction(
        Long userId,
        AuctionRegistrationRequestDto requestDto
    ) {
        User foundUser = findUserById(userId);

        Long itemId = requestDto.getItemId();

        Item foundItem = findItemByItem(itemId);

        Auction auctionToRegister = Auction.of(
            foundUser,
            foundItem,
            requestDto.getStartingPrice(),
            requestDto.getAuctionDays()
        );

        Auction registeredAuction = auctionRepository.save(auctionToRegister);
        return AuctionRegistrationResponseDto.toDto(registeredAuction);
    }

    @SemaphoreLock(key = "bid_auction")
    @Transactional
    @TriggerPopularUpdate(target = PopularUpdateTarget.AUCTION)
    public BidAuctionResponseDto bidAuction(
        Long userId,
        BidAuctionRequestDto requestDto
    ) {
        User foundUser = findUserById(userId);

        Long auctionId = requestDto.getAuctionId();

        Auction foundAuction = auctionRepository.findByIdAndStatus(
            auctionId,
            Status.ON_SALE
        ).orElseThrow(
            () -> new CustomException(ErrorCode.AUCTION_NOT_ACTIVE)
        );

        auctionScheduler.makeExpiredAuctionCompleted(foundAuction);

        Bid foundBid = createNewBidOrUpdateExistingBid(
            requestDto,
            foundAuction,
            foundUser
        );

        bidRepository.save(foundBid);

        foundAuction.incrementBidderCount();

        auctionRepository.save(foundAuction);
        return BidAuctionResponseDto.toDto(foundBid);
    }

    private Bid createNewBidOrUpdateExistingBid(
        BidAuctionRequestDto requestDto,
        Auction foundAuction,
        User foundUser
    ) {
        long bidPrice = requestDto.getBidPrice();

        Optional<Bid> foundBid = bidRepository.findWithLockByAuction(foundAuction);

        if (foundBid.isEmpty()) {

            return Bid.of(foundUser, foundAuction, bidPrice);
        }

        Bid existingBid = foundBid.get();

        existingBid.updateBid(bidPrice, foundUser);
        return existingBid;
    }

    @Transactional
    public void deleteAuction(Long userId, Long auctionId) {

        boolean hasBid = bidRepository.existsByAuctionId(auctionId);

        if (hasBid) {
            throw new CustomException(ErrorCode.CANNOT_CANCEL_AUCTION);
        }

        Auction foundAuction = findAuctionById(auctionId);

        foundAuction.validateUser(userId);
        foundAuction.delete();
    }

    /*
    helper
     */
    private LocalDateTime getStartDate() {
        return LocalDateTime.now().minusDays(30);
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(
                () -> new CustomException(ErrorCode.USER_NOT_FOUND)
            );
    }

    private Item findItemByItem(Long itemId) {
        return itemRepository.findById(itemId)
            .orElseThrow(
                () -> new CustomException(ErrorCode.ITEM_NOT_FOUND)
            );
    }

    private Auction findAuctionById(Long auctionId) {
        return auctionRepository.findById(auctionId)
            .orElseThrow(
                () -> new CustomException(ErrorCode.AUCTION_NOT_FOUND)
            );
    }

    private List<AuctionListResponseDto> deserialize(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return Arrays.asList(
                    mapper.readValue(json, AuctionListResponseDto[].class)
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("캐시 역직렬화 실패", e);
        }
    }
}