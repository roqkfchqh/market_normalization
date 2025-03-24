package no.gunbang.market.domain.auction.dto.response;

import com.querydsl.core.annotations.QueryProjection;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AuctionListResponseDto {

    private Long auctionId;
    private Long itemId;
    private String itemName;
    private long startPrice;
    private long currentMaxPrice;
    private String dueDate;
    private int bidCount;

    @QueryProjection
    public AuctionListResponseDto(Long auctionId, Long itemId, String itemName, long startPrice, long currentMaxPrice,
        LocalDateTime dueDate, int bidCount) {
        this.auctionId = auctionId;
        this.itemId = itemId;
        this.itemName = itemName;
        this.startPrice = startPrice;
        this.currentMaxPrice = currentMaxPrice;
        this.dueDate = dueDate.toString();
        this.bidCount = bidCount;
    }
}
