package no.gunbang.market;

import lombok.extern.slf4j.Slf4j;
import no.gunbang.market.common.popularevent.PopularUpdateAsync;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@SpringBootTest
class PopularUpdateAsyncConcurrencyTest {

    @Autowired
    private PopularUpdateAsync popularUpdateAsync;

    @Test
    void 인기_마켓_동시_갱신_실행_테스트_락_없이() throws InterruptedException {
        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        log.info("=== 테스트 시작 ===");

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    log.info("스레드 {}: 호출 시작", Thread.currentThread().getName());
                    popularUpdateAsync.updateMarketPopularsError();
                    log.info("스레드 {}: 호출 완료", Thread.currentThread().getName());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        int executions = popularUpdateAsync.getExecutionCount();
        log.info(">>> 실행된 쿼리 횟수 = " + executions);

        Assertions.assertEquals(5, executions, "이게왜안됨!");
    }

    @Test
    void 인기_마켓_동시_갱신_실행_테스트_세마포어() throws InterruptedException {
        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    log.info("스레드 {}: 호출 시작", Thread.currentThread().getName());
                    popularUpdateAsync.updateMarketPopulars();
                    log.info("스레드 {}: 호출 완료", Thread.currentThread().getName());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        int executions = popularUpdateAsync.getExecutionCount();
        log.info(">>> 실행된 쿼리 횟수 = " + executions);

        Assertions.assertEquals(1, executions, "이게왜안됨!");
    }
}
