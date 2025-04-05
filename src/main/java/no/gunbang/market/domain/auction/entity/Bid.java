package no.gunbang.market.domain.auction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import no.gunbang.market.common.entity.BaseEntity;
import no.gunbang.market.common.exception.CustomException;
import no.gunbang.market.common.exception.ErrorCode;
import no.gunbang.market.domain.user.entity.User;
import org.hibernate.annotations.Comment;

@Entity
@Getter
@Table(name = "bid")
@NoArgsConstructor
public class Bid extends BaseEntity {

    @Comment("입찰 식별자")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT")
    private Long id;

    @Comment("입찰 가격")
    private long bidPrice;

    @Comment("마지막 입찰 성공 시간")
    private LocalDateTime updatedAt;

    @Comment("경매 외래키")
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_id")
    private Auction auction;

    @Comment("사용자 외래키")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    public static Bid of(
        User user,
        Auction auction,
        long bidPrice
    ) {
        validateAuctionNotExpired(auction);
        validateNewBid(auction, bidPrice);
        validateUserGold(user, bidPrice);

        Bid bid = new Bid();
        bid.user = user;
        bid.auction = auction;
        bid.bidPrice = bidPrice;
        return bid;
    }

    public void updateBid(
        long bidPrice,
        User user
    ) {
        validateAuctionNotExpired(this.auction);
        validateBidUpdate(bidPrice);
        validateUserGold(user, bidPrice);
        checkIfBidderIsSameAsPrevious(user.getId());

        this.bidPrice = bidPrice;
        this.user = user;
        this.updatedAt = LocalDateTime.now();
    }

    // 새로운 입찰 생성 시 최소 입찰 가격 검증
    private static void validateNewBid(
        Auction auction,
        long bidPrice
    ) {
        if (bidPrice < auction.getStartingPrice()) {
            throw new CustomException(ErrorCode.LACK_OF_GOLD);
        }
    }

    // 기존 입찰이 있을 때, 기존의 입찰 가격보다 같거나 낮은지 검증
    private void validateBidUpdate(long newBidPrice) {
        if (newBidPrice <= this.bidPrice) {
            throw new CustomException(ErrorCode.BID_TOO_LOW);
        }
    }

    // 동일한 사용자가 연속으로 입찰하는지 검증
    private void checkIfBidderIsSameAsPrevious(long newBidderId) {
        if (this.user.getId().equals(newBidderId)) {
            throw new CustomException(ErrorCode.CONSECUTIVE_BID_NOT_ALLOWED);
        }
    }

    // 입찰 가격이 보유한 골드보다 많은지 검증
    private static void validateUserGold(
        User user,
        long bidPrice
    ) {
        if (bidPrice > user.getGold()) {
            throw new CustomException(ErrorCode.EXCESSIVE_BID);
        }
    }

    // 경매 마감 시간이 지났는지 검증
    private static void validateAuctionNotExpired(Auction auction) {
        if (LocalDateTime.now().isAfter(auction.getDueDate())) {
            throw new CustomException(ErrorCode.AUCTION_EXPIRED);
        }
    }
}