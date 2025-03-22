#tradeCount 초기 데이터 삽입
INSERT INTO trade_count (item_id, count)
SELECT market.item_id, COUNT(*)
FROM trade
JOIN market ON trade.market_id = market.id
GROUP BY market.item_id;

#조회용 커버링 인덱스
CREATE INDEX idx_auction_covering
    ON auction (status DESC, created_at DESC, bidder_count DESC, id DESC);
CREATE INDEX idx_market_covering
    ON market (status DESC, created_at DESC, item_id DESC, amount DESC, price DESC);
CREATE INDEX idx_trade_count_order
    ON trade_count (count DESC, item_id);

#스케줄링용 인덱스
CREATE INDEX idx_auction_duedate ON auction (due_date DESC);

#풀텍스트 인덱스
CREATE FULLTEXT INDEX idx_fulltext ON item (name)