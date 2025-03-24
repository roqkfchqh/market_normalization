package no.gunbang.market.domain.auction.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class AuctionService {

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
        return auctionRepository.findPopularAuctionItems(
                getStartDate(),
                pageable
        );
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
}