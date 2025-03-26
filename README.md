# Spring Lock 테스트 프로젝트

이 프로젝트는 Spring 애플리케이션에서 사용할 수 있는 다양한 락(Lock) 메커니즘을 테스트하고 비교하기 위한 샘플 프로젝트입니다.

## 소개

데이터베이스의 동시성 제어는 많은 애플리케이션에서 중요한 요구사항입니다. 이 프로젝트는 다음과 같은 다양한 락 메커니즘을 구현하고 테스트합니다:

1. **락 없음 (None)**: 락 메커니즘 없이 단순 업데이트
2. **낙관적 락 (Optimistic Lock)**: 버전 기반의 충돌 감지
3. **비관적 락 (Pessimistic Lock)**: 데이터베이스 수준의 행 잠금
4. **분산 락 (Distributed Lock)**: Redis를 이용한 분산 환경 락

각 락 메커니즘은 동일한 카운터 증가 시나리오를 통해 테스트되어 동시성 문제 처리 방식과 성능을 비교할 수 있습니다.

## 기술 스택

- Java 17
- Spring Boot 3
- Spring Data JPA
- Spring Data Redis
- Redisson (분산 락 구현)
- MySQL (또는 H2 인메모리 데이터베이스)
- JUnit 5

## 프로젝트 구조

```
src/main/java/org/example/springlock/
├── none           # 락 없는 구현
├── optimistic     # 낙관적 락 구현
├── pessimistic    # 비관적 락 구현
└── distributed    # 분산 락 구현 (Redis 기반)
```

## 설정 및 실행 방법

### 필수 요구사항

- JDK 17
- MySQL 서버 (또는 설정 변경을 통해 H2 사용 가능)
- Redis 서버 (분산 락 테스트용)

## 락 메커니즘 테스트

각 락 메커니즘은 테스트를 통해 검증됩니다. 테스트 실행:

```bash
# 모든 테스트 실행
./gradlew test

# 특정 테스트 클래스 실행 (예: 낙관적 락)
./gradlew test --tests OptimisticCounterServiceTest
```

### 테스트 시나리오

모든 테스트는 다음과 같은 시나리오를 따릅니다:

1. 카운터 초기화 (값: 0)
2. 여러 스레드(기본 10개)에서 동시에 카운터 증가 작업 수행 (기본 1000회)
3. 모든 작업 완료 후 결과 검증

### 테스트 결과 해석

각 락 메커니즘의 테스트 결과는 다음 정보를 제공합니다:

- 락 충돌/예외 횟수
- 최종 카운터 값
- 테스트 실행 시간
- 성공한 업데이트 수

## 각 락 메커니즘 특징

### 락 없음 (None)
- 가장 단순한 구현으로 동시성 보장 없음
- 동시 접근 시 데이터 손실 발생 가능

### 낙관적 락 (Optimistic Lock)
- JPA의 `@Version` 어노테이션을 사용한 구현
- 충돌 시 `OptimisticLockingFailureException` 발생
- 읽기 작업에 락이 없어 성능 좋음
- 충돌 빈도가 낮은 환경에 적합

### 비관적 락 (Pessimistic Lock)
- JPA의 `LockModeType.PESSIMISTIC_WRITE` 사용
- 데이터베이스 수준의 행 잠금으로 강력한 동시성 제어
- 잠금 획득 시 다른 트랜잭션은 대기
- 충돌 빈도가 높은 환경에 적합

### 분산 락 (Distributed Lock)
- Redisson을 사용한 Redis 기반 분산 락
- 여러 서버/인스턴스 간 동시성 제어 가능
- 분산 환경에서 안정적인 락 제공

## 테스트 코드

### 락 없음 테스트 (CounterServiceTest)

락이 없는 상태에서의 동시성 문제를 보여주는 테스트입니다.

```java
@Test
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
    
    int finalCount = counterService.getCounterById(counterId).getCount();
    
    // 락을 사용하지 않았기 때문에 동시성 문제가 발생할 수 있으며,
    // 성공한 업데이트 수와 count 값이 다를 수 있음
    assertNotEquals(successfulUpdates.get(), finalCount);
}
```

**특징:**
- 가장 단순한 구현으로 별도의 락 메커니즘 없음
- 모든 업데이트 작업이 성공했더라도(`successfulUpdates.get() == testCount`) 최종 카운터 값은 예상보다 낮음
- `assertNotEquals`를 통해 동시성 문제가 발생했음을 검증
- 실행 속도는 가장 빠르지만 데이터 정확성을 보장할 수 없음

**결과 분석:**
- 성공한 업데이트 수: 1000 (모든 스레드가 예외 없이 실행)
- 최종 카운터 값: 1000보다 작음 - 동시성 문제로 인한 데이터 손실
- 실제 프로덕션 환경에서는 사용하기 부적합한 방식

### 낙관적 락 테스트 (OptimisticCounterServiceTest)

낙관적 락(Optimistic Lock)을 사용한 동시성 제어 테스트입니다.

```java
@Test
public void testOptimisticLock() throws InterruptedException {
    int testCount = 1000;
    ExecutorService executorService = Executors.newFixedThreadPool(10);
    CountDownLatch latch = new CountDownLatch(testCount);

    // 예외 발생 횟수를 추적하기 위한 변수
    AtomicInteger optimisticLockExceptionCount = new AtomicInteger(0);

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
    
    int finalCount = optimisticCounterService.getCounterById(optimisticCounterId).getCount();
    int testedTotalCount = optimisticLockExceptionCount.get() + finalCount;
    
    // 성공한 업데이트 수 + 예외 발생 횟수 = 전체 테스트 횟수
    assertEquals(testedTotalCount, testCount);
}
```

**특징:**
- JPA의 `@Version` 어노테이션을 사용하여 엔티티 버전 관리
- 동시에 같은 버전의 엔티티를 수정하려 할 때 `OptimisticLockingFailureException` 발생
- 충돌 발생 시 예외를 캐치하여 처리 필요 (재시도 로직 구현 가능)
- 읽기 작업에 락이 없어 성능이 좋음

**결과 분석:**
- 최종 카운터 값: 성공한 업데이트 수 (예: 600~800)
- 발생한 예외 수: 충돌로 인한 실패 횟수 (예: 200~400)
- 최종 카운터 값 + 예외 발생 횟수 = 전체 테스트 횟수 (1000)
- 실제 환경에서는 충돌 시 재시도 로직을 구현하는 것이 일반적

### 비관적 락 테스트 (PessimisticCounterServiceTest)

비관적 락(Pessimistic Lock)을 사용한 동시성 제어 테스트입니다.

```java
@Test
public void testPessimisticLock() throws InterruptedException {
    int testCount = 1000;
    ExecutorService executorService = Executors.newFixedThreadPool(10);
    CountDownLatch latch = new CountDownLatch(testCount);

    AtomicInteger successfulUpdates = new AtomicInteger(0);

    for (int i = 0; i < testCount; i++) {
        executorService.submit(() -> {
            try {
                pessimisticCounterService.incrementCountWithPessimisticLock(pessimisticCounterId);
                successfulUpdates.incrementAndGet();
            } catch (Exception e) {
                System.out.println("비관적 락 충돌: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });
    }

    latch.await(); // 모든 스레드가 작업을 완료할 때까지 대기
    
    int finalCount = pessimisticCounterService.getCounterById(pessimisticCounterId).getCount();
    
    // 성공한 업데이트 수와 최종 카운터 값이 같음
    assertEquals(successfulUpdates.get(), finalCount);
}
```

**타임아웃 테스트:**
```java
@Test
public void testLockTimeoutException() throws InterruptedException {
    ExecutorService executorService = Executors.newFixedThreadPool(2);
    CountDownLatch latch = new CountDownLatch(2);

    executorService.submit(() -> {
        try {
            // 첫 번째 스레드가 락을 획득하고 5초 동안 유지
            pessimisticCounterService.lockCounterAndHold(pessimisticCounterId);
        } catch (InterruptedException e) {
            System.out.println("락 유지 중 예외 발생: " + e.getMessage());
        } finally {
            latch.countDown();
        }
    });

    executorService.submit(() -> {
        try {
            Thread.sleep(1000); // 1초 후 시도
            // 두 번째 스레드가 접근 시도 (락이 해제될 때까지 대기)
            pessimisticCounterService.incrementCountWithPessimisticLock(pessimisticCounterId);
        } catch (Exception e) {
            System.out.println("발생한 예외: " + e.getClass().getName() + " - " + e.getMessage());
        } finally {
            latch.countDown();
        }
    });

    latch.await(); // 모든 스레드가 완료될 때까지 대기
}
```

**특징:**
- JPA의 `LockModeType.PESSIMISTIC_WRITE`를 사용한 데이터베이스 수준의 행 잠금
- 락 획득 시 다른 트랜잭션은 락이 해제될 때까지 대기
- 기본적으로 모든 업데이트가 성공함 (락을 획득한 후에만 수정)
- 타임아웃 설정이 중요 (무한 대기 방지)

**결과 분석:**
- 성공한 업데이트 수: 1000 (모든 스레드가 순차적으로 락을 획득하여 작업)
- 최종 카운터 값: 1000 (모든 업데이트가 성공)
- 락 획득을 위한 대기 시간으로 인해 상대적으로 실행 시간이 김
- 타임아웃 테스트에서는 첫 번째 스레드가 락을 해제한 후에야 두 번째 스레드가 작업 수행

### 분산 락 테스트 (DistributedCounterProviderTest)

Redis 기반 분산 락을 사용한 동시성 제어 테스트입니다.

```java
@Test
public void testDistributedLock() throws InterruptedException {
    int testCount = 1000;
    ExecutorService executorService = Executors.newFixedThreadPool(10);
    CountDownLatch latch = new CountDownLatch(testCount);
    AtomicInteger successfulUpdates = new AtomicInteger(0);

    for (int i = 0; i < testCount; i++) {
        executorService.submit(() -> {
            try {
                distributedCounterService.incrementCounterWithDistributedLock(counterId);
                successfulUpdates.incrementAndGet();
            } catch (Exception e) {
                System.out.println("락 충돌: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });
    }

    latch.await(); // 모든 스레드가 작업을 완료할 때까지 대기
    
    DistributedCounter finalCounter = distributedCounterProvider.getCounter(counterId);
    
    // 성공한 업데이트 수와 최종 카운터 값이 같음
    assertEquals(successfulUpdates.get(), finalCounter.getCount());
}
```

**특징:**
- Redisson 라이브러리를 사용한 Redis 기반 분산 락 구현
- `RedissonDistributedLockManager`를 통해 락 획득 및 관리
- 여러 서버/인스턴스 간에도 동시성 제어 가능
- 락 획득 시도 시간(timeout) 및 유지 시간 설정 가능

**결과 분석:**
- 성공한 업데이트 수: 1000 (모든 스레드가 락을 획득하여 작업)
- 최종 카운터 값: 1000 (모든 업데이트가 성공)
- Redis 서버 의존성과 네트워크 지연으로 인해 다른 방식보다 느릴 수 있음
- 마이크로서비스 환경이나 여러 서버에서 동일 리소스 접근 시 유용

## 성능 비교 및 사용 시나리오

### 성능 비교

| 락 메커니즘 | 실행 시간 | 데이터 정확성 | 구현 복잡성 | 분산 환경 지원 |
|------------|---------|------------|-----------|--------------|
| 락 없음     | 매우 빠름 | 낮음        | 매우 간단   | 불가능        |
| 낙관적 락   | 빠름     | 중간        | 간단       | 제한적        |
| 비관적 락   | 중간     | 높음        | 중간       | 제한적        |
| 분산 락     | 느림     | 높음        | 복잡       | 우수          |

### 적합한 사용 시나리오

1. **락 없음:**
   - 읽기 전용 작업
   - 동시 접근이 거의 없는 데이터
   - 데이터 정확성이 절대적으로 중요하지 않은 경우
   - 예: 로깅, 통계 집계, 임시 데이터

2. **낙관적 락:**
   - 읽기 작업이 많고 쓰기 충돌이 적은 환경
   - 충돌 시 재시도가 가능한 경우
   - 높은 처리량이 필요한 경우
   - 예: 사용자 프로필 업데이트, 게시글 수정

3. **비관적 락:**
   - 쓰기 작업이 많고 충돌 가능성이 높은 환경
   - 데이터 정확성이 매우 중요한 경우
   - 짧은 시간 내에 처리되는 트랜잭션
   - 예: 재고 관리, 좌석 예약, 금융 거래

4. **분산 락:**
   - 마이크로서비스 아키텍처
   - 여러 서버/인스턴스에서 동일 리소스 접근
   - 클라우드 환경에서의 확장 가능한 애플리케이션
   - 예: 분산 스케줄러, 글로벌 카운터, 분산 작업 큐

## 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다. 
