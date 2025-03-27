# Kotlin의 동시성 제어 방식

## `synchronized` 키워드 사용 방식

`synchronized`는 Java/Kotlin에서 가장 기본적인 동기화 방식으로서 사용되고 있습니다.  
'임계 영역'은 메서드 전체로 지정하거나 혹은 자체적으로 블록을 생성해서 지정할 수도 있습니다.  
JVM에서 '모니터 락'을 기반으로 임계 영역에 진입하는 스레드를 하나만 허용하는 방식입니다.  
JVM 내장 기능이기 때문에 별도의 import 없이도 사용 가능 하며, unlock을 수행하지 않아도 동기화 블록을 넘어가면 자동으로 락이 해제된다는 특징이 있습니다.

### 활용 방식

- 동기화 블록을 지정하는 방식

```kt
synchronized(lockObject) { /* 하나의 스레드만 진입할 수 있는 임계 영역 */ }
```

- 함수 단위로 락을 거는 방식

```kt
@Synchronized
fun lockedFun() { /* 임계 영역 */ }
```

### 장점

- JVM 내장 기능이기 때문에 별도의 import가 필요 없음
- 문법이 직관적이고 구현이 간단함
- lock과 unlock 작업은 JVM이 관리해주므로 개발자가 직접 할 필요가 없음 

### 단점

- **락을 얻지 못한 스레드는 블로킹되며, 락을 얻기 위한 시도조차 할 수 없음**
  - `tryLock` 과 같은 동작의 부재
- 락 보유 여부나 순서를 조작할 수 없음

<br/>

## Atomic & CAS (Compare-And-Swap)

CAS는 하드웨어 수준에서 지원하는 원자적 연산 기법으로, 스레드 간의 경쟁 없이도 상태를 갱신하는 방법입니다.  
그러므로 CAS를 활용하는 Atomic 방식은 락-프리 하며, 데드락이 있을 수 없습니다.  
Java 및 Kotlin에서는 `java.util.concurrent.atomic` 패키지의
`AtomicInteger`, `AtomicLong`, `AtomicReference` 등의 클래스를 활용해서 Atomic 방식을 구현합니다.  
그리고 해당 클래스들에 내부적으로 CAS 알고리즘이 구현되어 있습니다.

### 활용 방식

```kt
val counter = AtomicInteger(0)
val result = counter.incrementAndGet()
```

### 장점

- 단일 숫자나 boolean 타입 등의 변수에 대한 동시성을 다룰 때 유용함
- 락-프리 하므로 락에 대한 관리가 전혀 필요 없으며, 동작이 빠름
- `compareAndSet` 메서드를 활용한 재시도 로직 구현 가능 

### 단점

- 한번에 여러 값들이 변경되는 로직에는 적합하지 않음
- CAS 실패 로직 구현으로 인한 코드 복잡도가 늘어날 수 있음
- 값이 A → B → A로 바뀔 때 변경을 감지하지 못하는 'ABA 문제'가 발생할 수 있음

<br/>

## ReentrantLock

이름 그대로 동일한 스레드가 여러 번 재진입해서 락을 획득해도 문제가 없는 락 방식입니다.  
Java 및 Kotlin에서 `java.util.concurrent.locks` 패키지를 통해 지원하고 있으며,
`synchronized`에 비해 훨씬 더 세밀하고 유연한 제어가 가능합니다.  
또한 내부적으로 `int` 값 기반의 `compareAndSwap` 메서드도 활용하기 때문에,
락에 대한 경합이 없는 경우에 한해서는 락-프리 하게 동작합니다.

### 활용 방식

```kt
val userLocks = ConcurrentHashMap<Long, ReentrantLock>()

fun chargeUserPoint(userId: Long, amount: Long) {
    val lock = userLocks.computeIfAbsent(userId) { ReentrantLock() }

    lock.lock()
    try {
        // locked logic
    } finally {
        lock.unlock()
    }
}
```

### 장점

- `lock`, `unlock`, `tryLock`, `lockInterruptibly` 등의 다양한 락 제어 메서드 활용 가능
- 락 상태 확인 가능
- 재진입이 가능하므로 스레드 중첩 호출에 대해 안전함

### 단점

- `unlock` 메서드를 활용한 락의 명시적 해제 필요
- `synchronized` 키워드 활용에 비해 장황해지는 코드

<br/>

## CountDownLatch & ExecutorService 테스트 전략

래치는 모든 스레드가 순서대로 진행되는 것이 보장되어 있는 상황에서 활용하기에 좋은 도구입니다.  
그리고 ExecutorService는 스레드 풀에서 태스크 실행 메커니즘(풀에 담긴 스레드 수, 스레드 관리 방식 등)을 규정하는 인터페이스입니다.  
이 도구들을 함께 활용하면 요청이 동시적으로 발생하는 케이스에 대한 재현을 정확하게 할 수 있습니다.

### 활용 방식

```kt
val latch = CountDownLatch(2)
val executor = Executors.newFixedThreadPool(2)
val results = mutableListOf<Result>()

repeat(2) {
    executor.submit {
        try {
            val response = mockMvc.perform(request).andReturn().response
            results.add(response)
        } finally {
            latch.countDown()
        }
    }
}

latch.await()
```

### 장점

- 실제 서버 환경에 대한 동시성 시뮬레이션을 정확하게 수행할 수 있음
- 실제 로직의 동시성 관련 기능을 수정할 필요 없이 그대로 활용 가능
- 에러 파악 및 모니터링에 용이

### 단점

- 테스트 코드가 복잡해질 수 있음

---

## 정리

저는 기능 구현의 측면에서는 `ReentrantLock`을 활용하여 포인트 충전 및 사용에 대한 동시성 제어를 구현했습니다.  
이로써 충전 및 사용에 대한 동시 요청에 대한 락을 정확히 수행하면서도, 경합하는 스레드가 없는 경우에는 락-프리 하게 동작하도록 구현을 할 수 있었습니다.  
그리고 테스트 코드에서는 `CountDownLatch`와 `ExecutorService`를 활용해
여러 요청이 동시에 실행되는 상황에 대해 테스트하는 코드를 작성했습니다.  
이를 통해 락 경합 상태에 대해 정확하게 테스트할 수 있었습니다.
