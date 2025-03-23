package no.gunbang.market.domain.market.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import no.gunbang.market.common.entity.QItem;
import no.gunbang.market.common.query.CursorStrategy;
import no.gunbang.market.common.entity.Status;
import no.gunbang.market.domain.market.cursor.AmountCursorStrategy;
import no.gunbang.market.domain.market.cursor.MarketCursorValues;
import no.gunbang.market.domain.market.cursor.MarketDefaultCursorStrategy;
import no.gunbang.market.domain.market.cursor.PriceCursorStrategy;
import no.gunbang.market.domain.market.dto.response.*;
import no.gunbang.market.domain.market.entity.QMarket;
import no.gunbang.market.domain.market.entity.QTrade;
import no.gunbang.market.domain.market.entity.QTradeCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MarketRepositoryImpl implements MarketRepositoryCustom {

    private static final int PAGE_SIZE = 10;
    private static final int POPULAR_LIMIT = 200;

    private final JPAQueryFactory queryFactory;

    @Override
    public List<TradeHistoryResponseDto> findUserTradeHistory(Long userId) {
        QTrade trade = QTrade.trade;
        QMarket market = QMarket.market;

        return queryFactory
            .select(new QTradeHistoryResponseDto(
                trade.id,
                market.item.id,
                market.item.name,
                trade.amount,
                trade.totalPrice,
                trade.createdAt
            ))
            .from(trade)
            .join(trade.market, market)
            .where(trade.user.id.eq(userId))
            .fetch();
    }

    @Override
    public List<MarketHistoryResponseDto> findUserMarketHistory(Long userId) {
        QMarket market = QMarket.market;

        return queryFactory
            .select(new QMarketHistoryResponseDto(
                market.id,
                market.item.id,
                market.item.name,
                market.amount,
                market.price,
                market.status,
                market.createdAt
            ))
            .from(market)
            .where(market.user.id.eq(userId))
            .fetch();
    }

    @Override
    public List<MarketPopularResponseDto> findPopularMarketItemsCursor(
        LocalDateTime startDate,
        Long lastTradeCount,
        Long lastItemId
    ) {
        QMarket market = QMarket.market;
        QTradeCount tradeCount = QTradeCount.tradeCount;

        BooleanBuilder builder = new BooleanBuilder();
        builder
            .and(market.status.eq(Status.ON_SALE))
            .and(market.createdAt.goe(startDate));

        if (lastTradeCount != null) {
            builder.and(
                Expressions.booleanTemplate(
                    "({0} < {1}) OR ({0} = {1} AND {2} < {3})",
                        tradeCount.count, lastTradeCount, market.item.id, lastItemId
                )
            );
        } else {
            int maxTradeCount = Integer.MAX_VALUE;
            long maxItemId = Long.MAX_VALUE;
            builder.and(
                Expressions.booleanTemplate(
                    "({0} < {1}) OR ({0} = {1} AND {2} < {3})",
                    tradeCount.count, maxTradeCount, market.item.id, maxItemId
                )
            );
        }

        return queryFactory
            .select(new QMarketPopularResponseDto(
                market.item.id,
                market.item.name,
                market.amount.sum().coalesce(0),
                market.price.min().coalesce(0L),
                tradeCount.count.intValue()
            ))
            .from(market)
            .leftJoin(tradeCount).on(market.item.id.eq(tradeCount.itemId))
            .where(builder)
            .groupBy(market.item.id, market.item.name)
            .orderBy(tradeCount.count.desc(), market.item.id.desc())
            .limit(PAGE_SIZE)
            .fetch();
    }

    @Override
    public List<MarketListResponseDto> findAllMarketItemsCursor(
        String searchKeyword,
        String sortBy,
        String sortDirection,
        Long lastItemId,
        MarketCursorValues marketCursorValues
    ) {
        QMarket market = QMarket.market;
        QItem item = QItem.item;

        BooleanBuilder builder = new BooleanBuilder();
        if (searchKeyword != null && !searchKeyword.isBlank()) {
            builder.and(
                    Expressions.numberTemplate(Double.class, "match_against({0}, {1})", item.name, searchKeyword)
                            .gt(0)
            );
        }
        builder.and(market.status.eq(Status.ON_SALE));

        Order order = "DESC".equalsIgnoreCase(sortDirection) ? Order.DESC : Order.ASC;
        Predicate havingClause = null;
        if(lastItemId != null) {
            havingClause = getCursorStrategy(sortBy).buildCursorPredicate(order, lastItemId, marketCursorValues);
        }

        return queryFactory
            .select(new QMarketListResponseDto(
                item.id,
                item.name,
                market.amount.sum().coalesce(0),
                market.price.min().coalesce(0L)
            ))
            .from(market)
            .join(market.item, item)
            .where(builder)
            .groupBy(item.id, item.name)
            .having(havingClause)
            .orderBy(determineSorting(sortBy, sortDirection))
            .limit(PAGE_SIZE)
            .fetch();
    }

    @Override
    public Page<MarketPopularResponseDto> findPopularMarketItems(LocalDateTime startDate, Pageable pageable) {
        QMarket market = QMarket.market;
        QTradeCount tradeCount = QTradeCount.tradeCount;
        QItem item = QItem.item;

        List<Long> rawTopItemIds = queryFactory
                .select(tradeCount.itemId)
                .from(tradeCount)
                .orderBy(tradeCount.count.desc())
                .limit(POPULAR_LIMIT)   //1만개 중 200개라 이렇게 가져와도 괜찮음(인기순이라 뒤에서 짤릴 일 없음)
                .fetch();

        List<Long> filteredItemIds = queryFactory
                .select(market.item.id)
                .from(market)
                .where(
                        market.item.id.in(rawTopItemIds),
                        market.status.eq(Status.ON_SALE),
                        market.createdAt.goe(startDate)
                )
                .groupBy(market.item.id)
                .orderBy(orderByField(rawTopItemIds))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        if (filteredItemIds.isEmpty()) {
            return Page.empty(pageable);
        }

        List<MarketPopularResponseDto> results = queryFactory
                .select(new QMarketPopularResponseDto(
                        item.id,
                        item.name,
                        market.amount.sum().coalesce(0),
                        market.price.min().coalesce(0L),
                        tradeCount.count.intValue()
                ))
                .from(market)
                .join(item).on(item.id.eq(market.item.id))
                .join(tradeCount).on(market.item.id.eq(tradeCount.itemId))
                .where(
                        market.item.id.in(filteredItemIds),
                        market.status.eq(Status.ON_SALE),
                        market.createdAt.goe(startDate)
                )
                .groupBy(item.id, item.name, tradeCount.count)
                .orderBy(orderByField(filteredItemIds))
                .fetch();

        return new PageImpl<>(results, pageable, POPULAR_LIMIT);

    }

    @Override
    public Page<MarketListResponseDto> findAllMarketItems(
            String searchKeyword,
            String sortBy,
            String sortDirection,
            Pageable pageable
    ) {
        QMarket market = QMarket.market;
        QItem item = QItem.item;

        BooleanBuilder builder = new BooleanBuilder();
        if (searchKeyword != null && !searchKeyword.isBlank()) {
            builder.and(item.name.containsIgnoreCase(searchKeyword));
        }
        builder.and(market.status.eq(Status.ON_SALE));

        List<Long> itemIds = queryFactory
                .select(market.item.id)
                .from(market)
                .where(builder)
                .groupBy(market.item.id)
                .orderBy(determineSorting(sortBy, sortDirection))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        if (itemIds.isEmpty()) {
            return Page.empty(pageable);
        }

        List<MarketListResponseDto> content = queryFactory
                .select(new QMarketListResponseDto(
                        item.id,
                        item.name,
                        market.amount.sum().coalesce(0),
                        market.price.min().coalesce(0L)
                ))
                .from(market)
                .join(market.item, item)
                .where(
                        market.status.eq(Status.ON_SALE),
                        item.id.in(itemIds)
                )
                .groupBy(item.id, item.name)
                .orderBy(orderByField(itemIds))
                .fetch();

        Long count = queryFactory
                .select(item.countDistinct())
                .from(item)
                .fetchOne();

        return new PageImpl<>(content, pageable, count == null ? 0 : count);
    }

    private OrderSpecifier<?> orderByField(List<Long> ids) {
        String template = "FIELD({0}, " + ids.stream().map(String::valueOf).collect(Collectors.joining(", ")) + ")";
        return Expressions.numberTemplate(Integer.class, template, QItem.item.id).asc();
    }

    /*
    helper
     */
    private CursorStrategy<MarketCursorValues> getCursorStrategy(String sortBy) {
        return switch (sortBy) {
            case "price" -> new PriceCursorStrategy();
            case "amount" -> new AmountCursorStrategy();
            default -> new MarketDefaultCursorStrategy();
        };
    }

    private OrderSpecifier<?> determineSorting(String sortBy, String sortDirection) {
        Order order = "DESC".equalsIgnoreCase(sortDirection) ? Order.DESC : Order.ASC;
        return switch (sortBy) {
            case "price" -> new OrderSpecifier<>(order, QMarket.market.price.min());
            case "amount" -> new OrderSpecifier<>(order, QMarket.market.amount.sum());
            default -> new OrderSpecifier<>(order, QItem.item.id);
        };
    }
}
