# lambdaQL 벤치마크

같은 쿼리를 여러 방식으로 작성해 실행 시간을 비교한 기록이다. 모든 수치는 실측값이다(2026-11-23).
측정 코드는 `benchmark` 모듈(JMH 1.37)에 있고 배포하지 않는다.

## 1. 비교 대상

| 이름 | 방식 |
|---|---|
| `jdbc` | 일반 SQL. `PreparedStatement`로 실행하고 행을 직접 객체로 옮김 |
| `jpql` | JPA 표준. JPQL 문자열(`EntityManager.createQuery`) |
| `criteria` | JPA 표준. Criteria API |
| `lambdaql` | lambdaQL `QueryBuilder` |
| `springDerived` | Spring Data JPA 파생 쿼리 메서드(`findByStatusAndPriceGreaterThan`) |
| `springQuery` | Spring Data JPA `@Query`(JPQL) |
| `springSpec` | Spring Data JPA `findAll(Specification)` |
| `springFindById` | Spring Data JPA `findById`(쿼리가 아니라 `EntityManager.find`) |
| `springLambdaql` | lambdaQL 리포지토리 실행기(`LambdaQlExecutor`) |

- JPA·lambdaQL 방식은 연산마다 `EntityManager`를 하나 열고 닫는다. JDBC는 같은 커넥션 풀(HikariCP)에서 커넥션 하나를 빌린다.
- Spring Data JPA 방식은 Spring Boot 없이 구성한 Spring 컨텍스트에서 같은 커넥션 풀을 쓴다.
- 방식마다 표현할 수 있는 쿼리만 잰다. `Specification`은 조건만 표현하므로 조회와 조인에만 있고, 파생 쿼리 메서드는 집계를 만들 수 없다.
- 측정 전에 모든 방식이 같은 행을 내는지 확인한다(`./gradlew :benchmark:benchCheck`).

## 2. 쿼리와 데이터

데이터는 고객 100명, 주문 10,000건이다. 주문 `i`는 status `i % 10`, price `(i * 7) % 1000`이다. 데이터를 넣은 뒤 DB 통계를 갱신한다(`ANALYZE`).

| 이름 | 쿼리 | 결과 |
|---|---|---|
| `select` | `status = 1 and price > 900`, 엔티티 | 100행 |
| `byId` | `id = 4242`, 엔티티 | 1행 |
| `join` | `customer.name = 'c7'`, 엔티티 | 100행 |
| `group` | 고객별 `count`, `sum(price)`, `having sum(price) > 50000`, record로 받음 | 49행 |
| `exists` | 가격이 990을 넘는 주문이 있는 고객 | 9행 |
| `update` | `status = 1`인 주문의 `note` 변경(커밋 포함) | 1,000행 |

## 3. 환경과 방법

| 항목 | 값 |
|---|---|
| 하드웨어 | Apple M3 Pro, 11코어, 메모리 36GB |
| JVM | OpenJDK 21.0.7(ASM 리더) |
| 라이브러리 | Spring Boot 4.1.2 BOM: Hibernate 7.4.13, Spring Data JPA 4.1.2 |
| 데이터베이스 | H2 2.3.230 인메모리, PostgreSQL 17.12(로컬 Docker, 다른 컨테이너와 메모리 8GB 공유) |
| 측정 | 평균 시간. 워밍업 1초 × 3회, 측정 1초 × 5회, 포크 2회. 첫 호출은 새 JVM 10개에서 1회씩 |

같은 Docker에서 다른 컨테이너(Oracle, Jenkins 등)가 돌고 있어 PostgreSQL 수치에는 잡음이 있다. 첫 측정에서 PostgreSQL의 `byId_jpql`이 약 3ms로 나왔지만 다시 재면 260µs였다. 그래서 포크를 2회로 늘리고 측정 전에 통계를 갱신했다. 오차(±)는 99.9% 신뢰 구간이다.

재현: `./gradlew :benchmark:jmh`(H2), `./gradlew :benchmark:jmh -Dlql.db=postgresql`(비밀번호는 `LQL_DB_PASSWORD`).

## 4. 결과: 쿼리 실행 전체(µs, 낮을수록 빠름)

### 4.1 H2 인메모리

DB 왕복 비용이 거의 없어 라이브러리 자체의 비용이 드러난다.

| 쿼리 | jdbc | jpql | criteria | lambdaql | springDerived | springQuery | springSpec | springLambdaql |
|---|---|---|---|---|---|---|---|---|
| select | 5.1 | 47.3 | 56.9 | 48.8 | 50.5 | 57.2 | 60.6 | 51.6 |
| byId | 0.7 | 2.4 | 9.3 | 3.1 | – | – | – | 5.0(`findById` 4.7) |
| join | 5.4 | 47.1 | 57.6 | 48.6 | 49.3 | 51.0 | 64.2 | 53.3 |
| group | 2.0 | 4.7 | 14.3 | 7.2 | – | 5.9 | – | 8.9 |
| exists | 0.9 | 3.5 | 12.4 | 6.2 | – | 4.6 | – | 7.0 |
| update | 2,585 | 3,408 | 2,855 | 2,802 | – | 3,044 | – | 2,939 |

### 4.2 PostgreSQL 17

실제 네트워크 왕복과 DB 실행이 들어간 경우다.

| 쿼리 | jdbc | jpql | criteria | lambdaql | springDerived | springQuery | springSpec | springLambdaql |
|---|---|---|---|---|---|---|---|---|
| select | 595 | 679 | 756 | 679 | 704 | 702 | 1,110 | 728 |
| byId | 238 | 246 | 298 | 246 | – | – | – | 256(`findById` 462) |
| join | 691 | 777 | 858 | 806 | 797 | 770 | 1,238 | 778 |
| group | 1,192 | 1,220 | 1,534 | 1,293 | – | 1,279 | – | 1,333 |
| exists | 613 | 813 | 605 | 692 | – | 597 | – | 589 |
| update | 1,757 | 1,851 | 1,977 | 2,066 | – | 2,144 | – | 2,132 |

## 5. 결과: 쿼리 만들기만(ns, DB 왕복 없음, H2)

| 항목 | 시간 |
|---|---|
| JPQL `createQuery`(Hibernate 쿼리 계획 캐시 적중) | 178 |
| lambdaQL `toHql()`(조건 람다 1개) | 646 |
| lambdaQL `toHql()` + `createQuery` | 896 |
| lambdaQL `toHql()`(집계: 람다 3개) | 1,208 |
| Criteria API 객체 + `createQuery` | 1,703 |

## 6. 결과: 새 JVM의 첫 호출(ms)

| jdbc | criteria | jpql | lambdaql |
|---|---|---|---|
| 8.3 | 107.6 | 220.7 | 246.9 |

JPQL과 lambdaQL은 Hibernate의 HQL 파서를 처음 불러오는 비용(약 110ms)이 들어간다. lambdaQL은 여기에 람다 바이트코드 분석 첫 호출이 더해져 JPQL보다 약 26ms 길다. 분석 결과는 캐시되므로 JVM당 한 번이다(PostgreSQL에서도 213ms, 248ms로 같은 경향).

## 7. 해석

- **엔티티를 읽는 쿼리는 lambdaQL과 JPQL이 같은 수준이다.** H2에서 select 48.8µs와 47.3µs, join 48.6µs와 47.1µs다. 시간의 대부분은 Hibernate가 엔티티를 만드는 비용이며, JDBC(5µs)와의 차이도 여기서 나온다.
- **lambdaQL이 쿼리마다 더하는 비용은 약 0.5~3µs다.** 람다 정보 추출(람다 하나에 약 150ns, 실측)과 HQL 렌더링(람다 1개 646ns, 3개 1,208ns)이다. 결과가 적은 집계·exists 쿼리에서는 이 비용이 비율로 커서 H2에서 JPQL보다 2.5~2.7µs 느리다. 같은 집계를 JPQL로 튜플(`Object[]`)로 받아 직접 객체를 만들어도 `select new`와 같은 시간(4.47µs, 4.52µs)이었으므로, 이 차이는 결과 객체를 만드는 방식이 아니라 람다 추출과 렌더링에서 생긴다.
- **실제 DB에서는 이 차이가 측정 오차 안으로 들어간다.** PostgreSQL에서 lambdaQL과 JPQL은 select 679µs로 같고, 나머지 쿼리도 서로 오차 범위 안에 있다.
- **Criteria API는 H2에서 가장 느리다.** 쿼리 객체를 매번 만드는 비용(1.7µs)과 Hibernate의 Criteria 변환 비용 때문이다. Spring Data의 `Specification`은 PostgreSQL에서도 다른 방식보다 약 400µs 느렸다(select 1,110µs, join 1,238µs). 원인은 분석하지 않았다.
- **Spring Data JPA 리포지토리를 거치면 약 1~2µs가 더해진다**(H2에서 `select_springLambdaql` 51.6µs와 `select_lambdaql` 48.8µs). 리포지토리 프록시 비용이며, lambdaQL 실행기도 파생 쿼리 메서드·`@Query`와 같은 수준이다.
- **`findById`는 PostgreSQL에서 쿼리보다 느렸다**(462µs, 쿼리 246µs). `SimpleJpaRepository`의 읽기 전용 트랜잭션이 커넥션 설정을 바꾸는 왕복이 더해진 것으로 보인다(추정, 미측정).
- **UPDATE는 DB 실행이 지배한다.** 1,000행 수정과 커밋이 대부분이라 방식 간 차이는 오차 범위다.
