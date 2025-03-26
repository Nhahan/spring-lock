package org.example.springlock.none;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SpringBootTest
class CounterServiceTest {

    @Autowired
    private CounterService counterService;

    @Autowired
    private CounterRepository counterRepository;

    private Long counterId;

    @BeforeEach
    public void setup() {
        counterRepository.deleteAll();
        Counter counter = new Counter();
        counter.setCount(0);
        Counter savedCounter = counterService.saveCounter(counter);
        counterId = savedCounter.getId();
        System.out.println("counterId: " + counterId);
    }

    @Test
    @DisplayName("락 없이 동시에 증가하면 최종 카운트가 줄어든다")
    public void testNoLock() throws InterruptedException {
        int testCount = 1000;
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(testCount);

        AtomicInteger successfulUpdates = new AtomicInteger(0);

        for (int i = 0; i < testCount; i++) {
            executorService.submit(() -> {
                try {
                    counterService.incrementCount(counterId);
                    successfulUpdates.incrementAndGet();
                } catch (Exception e) {
                    System.out.println("예외 발생: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // 모든 스레드가 작업을 완료할 때까지 대기
        executorService.shutdown();

        int finalCount = counterService.getCounterById(counterId).getCount();
        System.out.println("최종 count: " + finalCount);
        System.out.println("성공한 업데이트 수: " + successfulUpdates.get());

        // 락을 사용하지 않았기 때문에 동시성 문제가 발생할 수 있으며, 성공한 업데이트 수와 count 값이 다를 수 있음
        assertNotEquals(successfulUpdates.get(), finalCount);
    }
}
