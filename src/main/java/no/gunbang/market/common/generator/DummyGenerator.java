package no.gunbang.market.common.generator;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class DummyGenerator {

    private static final Random random = new Random();
    private static final int USER_COUNT = 400_000;
    private static final int ITEM_COUNT = 10_000;
    private static final int MARKET_COUNT = 3_000_000;
    private static final int TRADE_COUNT = 6_000_000;
    private static final int AUCTION_COUNT = 1_500_000;
    private static final int BID_COUNT = AUCTION_COUNT; //bid 개수 == auction 개수
    private static final HashMap<Integer, Long> auctionStartingPrices = new HashMap<>();

    public static void main(String[] args) {
        try {
            generateUsersCSV();
            generateItemsCSV();
            generateMarketCSV();
            generateTradesCSV();
            generateAuctionsCSV();
            generateBidsCSV();
            System.out.println("CSV 파일 생성 완료");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void generateUsersCSV() throws IOException {
        FileWriter writer = new FileWriter("users.csv");
        writer.append("id,nickname,server,level,job,gold,email,password\n");

        for (int i = 1; i <= USER_COUNT; i++) {
            writer.append(String.valueOf(i)).append(",User").append(String.valueOf(i)).append(",Server").append(String.valueOf(i % 10)).append(",")
                    .append(String.valueOf(random.nextInt(100) + 1)).append(",Job").append(String.valueOf(i % 10)).append(",")
                    .append(String.valueOf(random.nextLong(1_000_000))).append(",user").append(String.valueOf(i)).append("@example.com,password\n");
        }
        writer.flush();
        writer.close();
    }

    private static void generateItemsCSV() throws IOException {
        FileWriter writer = new FileWriter("items.csv");
        writer.append("id,name\n");

        for (int i = 1; i <= ITEM_COUNT; i++) {
            writer.append(String.valueOf(i)).append(",Item").append(String.valueOf(i)).append("\n");
        }
        writer.flush();
        writer.close();
    }

    private static void generateMarketCSV() throws IOException {
        FileWriter writer = new FileWriter("market.csv");
        writer.append("id,user_id,item_id,amount,price,status\n");

        for (int i = 1; i <= MARKET_COUNT; i++) {
            writer.append(String.valueOf(i)).append(",").append(String.valueOf(random.nextInt(USER_COUNT) + 1)).append(",")
                    .append(String.valueOf(random.nextInt(ITEM_COUNT) + 1)).append(",")
                    .append(String.valueOf(random.nextInt(100) + 1)).append(",")
                    .append(String.valueOf(random.nextLong(1_000_000))).append(",ON_SALE\n");
        }
        writer.flush();
        writer.close();
    }

    private static void generateTradesCSV() throws IOException {
        FileWriter writer = new FileWriter("trades.csv");
        writer.append("id,user_id,market_id,amount,totalPrice\n");

        for (int i = 1; i <= TRADE_COUNT; i++) {
            writer.append(String.valueOf(i)).append(",").append(String.valueOf(random.nextInt(USER_COUNT) + 1)).append(",")
                    .append(String.valueOf(random.nextInt(MARKET_COUNT) + 1)).append(",")
                    .append(String.valueOf(random.nextInt(10) + 1)).append(",")
                    .append(String.valueOf(random.nextLong(1_000_000))).append("\n");
        }
        writer.flush();
        writer.close();
    }

    private static void generateAuctionsCSV() throws IOException {
        FileWriter writer = new FileWriter("auctions.csv");
        writer.append("id,user_id,item_id,startingPrice,dueDate,status,bidderCount\n");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (int i = 1; i <= AUCTION_COUNT; i++) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime dueDate = now.plusMinutes(random.nextInt(3 * 24 * 60)); // 3일(=4320분) 내 랜덤 시간 추가
            long startingPrice = random.nextLong(10_000, 1_000_000); // 최소 10,000 ~ 최대 1,000,000

            // 해당 auction의 시작 가격 저장 (bid에서 활용)
            auctionStartingPrices.put(i, startingPrice);

            writer.append(String.valueOf(i)).append(",")
                    .append(String.valueOf(random.nextInt(USER_COUNT) + 1)).append(",")
                    .append(String.valueOf(random.nextInt(ITEM_COUNT) + 1)).append(",")
                    .append(String.valueOf(startingPrice)).append(",") // startingPrice 추가
                    .append(dueDate.format(formatter)).append(",ON_SALE,0\n");
        }
        writer.flush();
        writer.close();
    }

    private static void generateBidsCSV() throws IOException {
        FileWriter writer = new FileWriter("bids.csv");
        writer.append("id,user_id,auction_id,bidPrice,updatedAt\n");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        Set<Integer> usedAuctionIds = new HashSet<>();
        for (int i = 1; i <= BID_COUNT; i++) {
            int auctionId;
            do {
                auctionId = random.nextInt(AUCTION_COUNT) + 1;
            } while (!usedAuctionIds.add(auctionId));

            long startingPrice = auctionStartingPrices.getOrDefault(auctionId, 10_000L);
            long bidPrice = startingPrice + random.nextLong(1_000, 100_000); // 최소 입찰가는 startingPrice보다 높게 설정

            writer.append(String.valueOf(i)).append(",")
                    .append(String.valueOf(random.nextInt(USER_COUNT) + 1)).append(",")
                    .append(String.valueOf(auctionId)).append(",")
                    .append(String.valueOf(bidPrice)).append(",") // 입찰가 반영
                    .append(LocalDateTime.now().format(formatter)).append("\n");
        }
        writer.flush();
        writer.close();
    }
}