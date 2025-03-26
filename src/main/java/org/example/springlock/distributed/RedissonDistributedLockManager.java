package org.example.springlock.distributed;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedissonDistributedLockManager implements DistributedLockManager {

    private final RedissonClient redissonClient;
    private static final String LOCK_KEY_PREFIX = "distributed-counter-lock:";

    @Override
    public void executeWithLock(Long key, Runnable task) throws InterruptedException {
        String lockKey = LOCK_KEY_PREFIX + key;
        RLock lock = redissonClient.getFairLock(lockKey);

        // 10초 내로 락 획득 시도, 15초 대기
        if (lock.tryLock(10, 15, TimeUnit.SECONDS)) {
            try {
                task.run();
            } finally {
                if (lock.isHeldByCurrentThread()) { // 현재 스레드가 해당 락을 실제로 가지고 있는지 확인
                    lock.unlock(); // 작업 완료 후 락 해제
                }
            }
        } else {
            throw new IllegalStateException("락 획득 실패: " + lockKey);
        }
    }
}
