package org.example.springlock.optimistic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class OptimisticCounterServiceTest {

    @Autowired
    private OptimisticCounterService optimisticCounterService;

    @Autowired
    private OptimisticCounterRepository optimisticCounterRepository;

    private Long optimisticCounterId;

    @BeforeEach
    public void setup() {
        optimisticCounterRepository.deleteAll();
        OptimisticCounter optimisticCounter = new OptimisticCounter();
        optimisticCounter.setCount(0);
        OptimisticCounter savedOptimisticCounter = optimisticCounterService.saveCounter(optimisticCounter);
        optimisticCounterId = savedOptimisticCounter.getId();
    }

    @Test
    @DisplayName("낙관적 락 충돌을 감지하고 예외를 집계한다")
    public void testOptimisticLock() throws InterruptedException {
        int testCount = 1000;
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(testCount);

        // 예외 발생 횟수를 추적하기 위한 변수
        AtomicInteger optimisticLockExceptionCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < testCount; i++) {
            executorService.submit(() -> {
                try {
                    optimisticCounterService.incrementCount(optimisticCounterId);
                } catch (OptimisticLockingFailureException e) {
                    // 낙관적 락 충돌 발생 시 처리 및 예외 카운트 증가
                    optimisticLockExceptionCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // 모든 스레드가 작업을 완료할 때까지 대기
        executorService.shutdown();

        long endTime = System.currentTimeMillis();
        long durationInMillis = endTime - startTime;
        double durationInSeconds = durationInMillis / 1000.0;

        // 하나 이상의 OptimisticLockingFailureException 발생해야 함
        assertTrue(optimisticLockExceptionCount.get() > 0);

        int finalCount = optimisticCounterService.getCounterById(optimisticCounterId).getCount();
        int testedTotalCount = optimisticLockExceptionCount.get() + finalCount;
        assertEquals(testedTotalCount, testCount);

        System.out.println("발생한 예외 수: " + optimisticLockExceptionCount.get());
        System.out.println("최종 count: " + finalCount);
        System.out.println("테스트 실행 시간: " + durationInSeconds + "초");
        System.out.println("testCount: " + testCount + ", testedTotalCount: " + testedTotalCount);
    }
}
