package no.gunbang.market.domain.auction.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import no.gunbang.market.common.entity.QItem;
import no.gunbang.market.common.query.CursorStrategy;
import no.gunbang.market.common.entity.Status;
import no.gunbang.market.domain.auction.cursor.AuctionCursorValues;
import no.gunbang.market.domain.auction.cursor.AuctionDefaultCursorStrategy;
import no.gunbang.market.domain.auction.cursor.CurrentMaxPriceCursorStrategy;
import no.gunbang.market.domain.auction.cursor.DueDateCursorStrategy;
import no.gunbang.market.domain.auction.cursor.StartPriceCursorStrategy;
import no.gunbang.market.domain.auction.dto.response.AuctionHistoryResponseDto;
import no.gunbang.market.domain.auction.dto.response.AuctionListResponseDto;
import no.gunbang.market.domain.auction.dto.response.BidHistoryResponseDto;
import no.gunbang.market.domain.auction.dto.response.QAuctionHistoryResponseDto;
import no.gunbang.market.domain.auction.dto.response.QAuctionListResponseDto;
import no.gunbang.market.domain.auction.dto.response.QBidHistoryResponseDto;
import no.gunbang.market.domain.auction.entity.QAuction;
import no.gunbang.market.domain.auction.entity.QBid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AuctionRepositoryImpl implements AuctionRepositoryCustom {

    private static final int POPULAR_LIMIT = 200;
    private static final int PAGE_SIZE = 10;

    private final JPAQueryFactory queryFactory;

    @Override
    public List<BidHistoryResponseDto> findUserBidHistory(Long userId) {
        QAuction auction = QAuction.auction;
        QBid bid = QBid.bid;

        return queryFactory
            .select(new QBidHistoryResponseDto(
                auction.id,
                auction.item.id,
                auction.item.name,
                bid.bidPrice,
                auction.dueDate,
                auction.status  //status가 completed면 입찰완료, 그게 아니라면 상회입찰중
            ))
            .from(bid)
            .join(bid.auction, auction)
            .where(bid.user.id.eq(userId))
            .fetch();
    }

    @Override
    public List<AuctionHistoryResponseDto> findUserAuctionHistory(Long userId) {
        QAuction auction = QAuction.auction;
        QBid bid = QBid.bid;

        return queryFactory
            .select(new QAuctionHistoryResponseDto(
                auction.id,
                auction.item.id,
                auction.item.name,
                auction.startingPrice,
                bid.bidPrice,
                auction.dueDate,
                auction.status
            ))
            .from(auction)
            .leftJoin(bid).on(auction.id.eq(bid.auction.id))
            .where(auction.user.id.eq(userId))
            .fetch();
    }

    @Override
    public List<AuctionListResponseDto> findPopularAuctionItemsCursor(
        LocalDateTime startDate,
        Long lastBidderCount,  //커서
        Long lastAuctionId     //tie-breaker
    ) {
        QBid bid = QBid.bid;
        QAuction auction = QAuction.auction;

        BooleanBuilder builder = new BooleanBuilder();
        builder
            .and(auction.status.eq(Status.ON_SALE))
            .and(auction.createdAt.goe(startDate));

        if (lastBidderCount != null) {
            builder.and(
                Expressions.booleanTemplate(
                    "({0} < {1}) OR ({0} = {1} AND {2} < {3})",
                    auction.bidderCount, lastBidderCount, auction.id, lastAuctionId
                )
            );
        } else {
            int maxBidderCount = Integer.MAX_VALUE;
            long maxAuctionId = Long.MAX_VALUE;
            builder.and(
                Expressions.booleanTemplate(
                    "({0} < {1}) OR ({0} = {1} AND {2} < {3})",
                    auction.bidderCount, maxBidderCount, auction.id, maxAuctionId
                )
            );
        }

        return queryFactory
            .select(new QAuctionListResponseDto(
                auction.id,
                auction.item.id,
                auction.item.name,
                auction.startingPrice,
                bid.bidPrice,
                auction.dueDate,
                auction.bidderCount
            ))
            .from(bid)
            .join(bid.auction, auction)
            .where(builder)
            .groupBy(
                auction.id, auction.item.id, auction.item.name, auction.startingPrice, auction.dueDate, bid.bidPrice, auction.bidderCount
            )
            .orderBy(auction.bidderCount.desc(), auction.id.desc())
            .limit(PAGE_SIZE)
            .fetch();
    }

    @Override
    public List<AuctionListResponseDto> findAllAuctionItemsCursor(
        LocalDateTime startDate,
        String searchKeyword,
        String sortBy,
        String sortDirection,
        Long lastAuctionId,
        AuctionCursorValues auctionCursorValues
    ) {
        QAuction auction = QAuction.auction;
        QBid bid = QBid.bid;
        QItem item = QItem.item;

        BooleanBuilder builder = new BooleanBuilder();
        if (searchKeyword != null && !searchKeyword.isBlank()) {
            builder.and(
                    Expressions.numberTemplate(Double.class, "match_against({0}, {1})", item.name, searchKeyword)
                            .gt(0)
            );
        }
        builder
            .and(auction.status.eq(Status.ON_SALE))
            .and(auction.createdAt.goe(startDate));

        Order order = "DESC".equalsIgnoreCase(sortDirection) ? Order.DESC : Order.ASC;
        if(lastAuctionId != null){
            //sortBy에 따라 커서 전략 선택
            CursorStrategy<AuctionCursorValues> cursorStrategy = getCursorStrategy(sortBy);
            builder.and(cursorStrategy.buildCursorPredicate(order, lastAuctionId, auctionCursorValues));
        }

        return queryFactory
            .select(new QAuctionListResponseDto(
                auction.id,
                auction.item.id,
                auction.item.name,
                auction.startingPrice,
                bid.bidPrice,
                auction.dueDate,
                auction.bidderCount
            ))
            .from(auction)
            .leftJoin(bid).on(auction.id.eq(bid.auction.id))
            .where(builder)
            .groupBy(auction.id, auction.item.id, auction.item.name, auction.startingPrice, auction.dueDate, bid.bidPrice, auction.bidderCount)
            .orderBy(determineSorting(sortBy, sortDirection))
            .limit(PAGE_SIZE)
            .fetch();
    }

    @Override
    public Page<AuctionListResponseDto> findPopularAuctionItems(LocalDateTime startDate, Pageable pageable) {
        QAuction auction = QAuction.auction;
        QBid bid = QBid.bid;
        QItem item = QItem.item;

        List<Long> auctionIds = queryFactory
                .select(auction.id)
                .from(auction)
                .where(
                        auction.status.eq(Status.ON_SALE),
                        auction.createdAt.goe(startDate)
                )
                .orderBy(auction.bidderCount.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        if (auctionIds.isEmpty()) return Page.empty(pageable);

        List<AuctionListResponseDto> results = queryFactory
                .select(new QAuctionListResponseDto(
                        auction.id,
                        item.id,
                        item.name,
                        auction.startingPrice,
                        bid.bidPrice,
                        auction.dueDate,
                        auction.bidderCount
                ))
                .from(auction)
                .join(auction.item, item)
                .leftJoin(bid).on(bid.auction.id.eq(auction.id))
                .where(auction.id.in(auctionIds))
                .groupBy(auction.id, item.id, item.name, auction.startingPrice, auction.dueDate, bid.bidPrice, auction.bidderCount)
                .orderBy(orderByField(auctionIds))
                .fetch();

        return new PageImpl<>(results, pageable, POPULAR_LIMIT);
    }


    @Override
    public Page<AuctionListResponseDto> findAllAuctionItems(LocalDateTime startDate, String searchKeyword, String sortBy, String sortDirection, Pageable pageable) {
        QAuction auction = QAuction.auction;
        QBid bid = QBid.bid;
        QItem item = QItem.item;

        BooleanBuilder builder = new BooleanBuilder();
        if (searchKeyword != null && !searchKeyword.isBlank()) {
            builder.and(item.name.containsIgnoreCase(searchKeyword));
        }
        builder.and(auction.status.eq(Status.ON_SALE))
                .and(auction.createdAt.goe(startDate));

        List<Long> auctionIds = queryFactory
                .select(auction.id)
                .from(auction)
                .where(builder)
                .orderBy(determineSorting(sortBy, sortDirection))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        if (auctionIds.isEmpty()) return Page.empty(pageable);

        List<AuctionListResponseDto> results = queryFactory
                .select(new QAuctionListResponseDto(
                        auction.id,
                        item.id,
                        item.name,
                        auction.startingPrice,
                        bid.bidPrice,
                        auction.dueDate,
                        auction.bidderCount
                ))
                .from(auction)
                .leftJoin(bid).on(auction.id.eq(bid.auction.id))
                .join(auction.item, item)
                .where(auction.id.in(auctionIds))
                .groupBy(auction.id, item.id, item.name, auction.startingPrice, auction.dueDate, bid.bidPrice, auction.bidderCount)
                .orderBy(orderByField(auctionIds))
                .fetch();

        Long count = queryFactory
                .select(auction.countDistinct())
                .from(auction)
                .where(builder)
                .fetchOne();

        return new PageImpl<>(results, pageable, count == null ? 0 : count);
    }

    private OrderSpecifier<?> orderByField(List<Long> ids) {
        String template = "FIELD({0}, " + ids.stream().map(String::valueOf).collect(Collectors.joining(", ")) + ")";
        return Expressions.numberTemplate(Integer.class, template, QAuction.auction.id).asc();
    }

    private CursorStrategy<AuctionCursorValues> getCursorStrategy(String sortBy) {
        return switch (sortBy) {
            case "startPrice" -> new StartPriceCursorStrategy();
            case "currentMaxPrice" -> new CurrentMaxPriceCursorStrategy();
            case "dueDate" -> new DueDateCursorStrategy();
            default -> new AuctionDefaultCursorStrategy();
        };
    }

    private OrderSpecifier<?> determineSorting(String sortBy, String sortDirection) {
        Order order = "DESC".equalsIgnoreCase(sortDirection) ? Order.DESC : Order.ASC;
        return switch (sortBy) {
            case "startPrice" -> new OrderSpecifier<>(order, QAuction.auction.startingPrice);
            case "currentMaxPrice" -> new OrderSpecifier<>(order, QBid.bid.bidPrice.coalesce(0L));
            case "dueDate" -> new OrderSpecifier<>(order, QAuction.auction.dueDate);
            default -> new OrderSpecifier<>(order, QAuction.auction.id);
        };
    }
}
