package no.gunbang.market.domain.auction.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import no.gunbang.market.common.entity.Status;
import no.gunbang.market.domain.auction.entity.Auction;
import no.gunbang.market.domain.auction.repository.AuctionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AuctionScheduler {

    private final AuctionRepository auctionRepository;

    // 30초마다 마감이 지난 경매는 ON_SALE에서 COMPLETED로 변경
    @Scheduled(cron = "*/30 * * * * *")
    @Transactional
    public void checkExpiredAuctions() {

        List<Auction> auctionList = new ArrayList<>();

        auctionList = auctionRepository.findByDueDateBeforeAndStatus(
            LocalDateTime.now(),
            Status.ON_SALE
        );

        auctionList.forEach(this::makeExpiredAuctionCompleted);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void makeExpiredAuctionCompleted(Auction auction) {
        auction.makeExpiredAuctionCompleted();
        auctionRepository.save(auction);
    }
}