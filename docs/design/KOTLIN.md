# lambdaQL Kotlin 지원 설계 (D17)

> 작성일 2026-11-09 · 기준 커밋 `70c5c01` · 상위 문서 [`ROADMAP.md`](ROADMAP.md) 3.8절
>
> - 이 문서의 "3.3", "3.6", "D14" 같은 절·결정 번호는 모두 `ROADMAP.md` 기준입니다.
> - 근거 파일(`/tmp/lql-kt/...`)은 작성 시점의 임시 산출물입니다. 재부팅 시 사라질 수 있으므로, 보존이 필요하면 저장소로 옮기십시오.
> - 실측 환경: kotlinc(embeddable) 1.7.22 · 2.1.10 · 2.2.21 · 2.3.0 · 2.3.21, JDK 21.0.7, H2 2.3.230. MariaDB·Oracle 의미 검증은 M5·M6에서 수행 예정입니다.


이 절은 아래 실측 결과에만 근거한다. 표에서는 근거 위치를 다음 약어로 적는다. 도구는 모두 lambdaql 저장소 밖에 있다.

| 약어 | 파일 | 내용 |
|---|---|---|
| `[P]` | `/tmp/lql-kt/pred/dump/cases-2321.txt` | 조건 람다 166개. 케이스 id는 c01–c118, k01–k03, j01–j12, r01–r05와 `[P]x01–x28`(`Cases2.kt`). 소스, `SerializedLambda` 정보, javap 포함 |
| `[V]` | `/tmp/lql-kt/value/cond-cases.txt` | 값, 정렬 키, 조인, 진입 80개 케이스(v·r·p·j·e 계열). 한 줄 형식은 `id \| impl \| kind \| cap \| body` |
| `[V2]` | `/tmp/lql-kt/value/cond-c2.txt` | 값 케이스 34개: `x01–x20`, `y01–y11`, `z01–z03`(`src/Cases2.kt`). `[P]`의 x 번호와는 다른 케이스 |
| `[VQ]` | `/tmp/lql-kt/value/src/Props.kt`, `/tmp/lql-kt/value/props.javap.txt` | 엔티티 속성 종류 q01–q12 |
| `[VF]` | `/tmp/lql-kt/value/src/Fl.kt`, `/tmp/lql-kt/value/fl.jp` | float/double 케이스 f01–f04 |
| `[VM]` | `/tmp/lql-kt/value/src/Misc.kt`, `/tmp/lql-kt/value/src/Inl.kt` | 진입 케이스 m01–m06, i01–i05 |
| `[VS]` | `/tmp/lql-kt/value/src/Std.kt` | stdlib 케이스 s01–s06 |
| `[H]` | `/tmp/lql-kt/sem/run.txt` | H2 2.3.230에서 행 단위 의미 검증. 케이스 id 111개(파라미터 변형 포함 139개), SUMMARY 블록 197행(918행부터). 이 절에서는 `[H]`를 케이스 id로만 인용한다 |
| `[H2]` | `/tmp/lql-kt/sem93/run93.txt` | `[H]` 하네스 복사본(`/tmp/lql-kt/sem93`)으로 다시 실행한 결과. c93(원래 하네스에서 SQL 구문 오류), `startsWith`/`contains(…, ignoreCase = true)`(c93s, c93c), nullable `until`(c93u, c93un, c93um) |
| `[F]` | `/tmp/lql-kt/fc/src/T.kt`, `T2.kt`, `T3.kt`. 출력은 `/tmp/lql-kt/fc/o2321`(2.3.21), `o1722`(1.7.22), `oj1.8`, `oj11` | 추가 형태 확인(t01–t20, u01–u02, s2n, s3, s4) |

- 컴파일러 버전별 원본 덤프: `/tmp/lql-kt/pred/dump/v{2321,2.3.0,2.2.21,2.1.10,1.7.22}.txt`, `/tmp/lql-kt/value/run-*.txt`, `body-*.txt`
- 버전 비교 도구: `/tmp/lql-kt/value/tools/cmp.py`

## 1. 결론

- **Java와 Kotlin은 같은 엔진(3.3 원자 그래프 + 구조 축약)으로 동시에 지원할 수 있다.**
  - Kotlin은 조건을 항상 소스 순서대로 낸다.
  - **3값 원자**(비교, 호출)를 복제하거나 재배치한 경우는 없었다. 166개 람다, 컴파일러 5개 버전 기준이다(`[P]`).
  - 하지만 **2값 `IsNull` 원자는 복제된다.**
    - nullable `when` 주제에서는 KP2가 분기마다 같은 주제의 null 확인을 다시 낸다(`[P]`x02: `aload_1; …; dup; ifnonnull 16 … 23: aload_1; …; dup; ifnonnull 34`).
    - c60, c63에서는 여러 null 확인이 null 블록 하나를 공유한다.
  - 따라서 "원자 1회 사용" 축약을 그대로 쓸 수는 없다. 3.8.6의 **I1 완화**(1회 사용을 3값 원자에만 적용)가 있어야 성립하며, 채택 여부는 3.8.8 #5에서 결정한다. `[H]`의 x02, c60, c60n은 이 완화를 전제로 모든 행에서 일치했다.
- **새로 필요한 작업은 `&&`/`||`/`!` 축약이 아니라 다음 네 가지다.**
  1. **값 스택**: 임시 local, `dup; ifnull` 분기 fork, 물질화된 boolean.
  2. **intrinsic과 stdlib 호출 정규화**: `Intrinsics.areEqual`, `Intrinsics.compare`, `checkNotNull*`, `StringsKt`, `CollectionsKt`, `IntRange`/`LongRange`, `RangesKt.until`, String/enum `when`의 switch 형태(KP17, KP18).
  3. **진입부**: impl 메서드가 synthetic이 아니고, `this`는 static impl의 첫 인자로 오며, 속성 레퍼런스가 있다.
  4. **렌더러의 join 규칙**: predicate 경로는 LEFT JOIN.
- **분류 결과**
  - 조건 람다(`[P]`): S 약 55, K 약 90, C 19.
  - 값·진입 케이스(`[V]` 80개, `[V2]` 34개, `[VQ]`·`[VF]`·`[VM]`·`[VS]`)도 같은 체계로 분류된다.
  - S는 Java와 같은 형태, K는 Kotlin 전용이지만 지원하는 형태, C는 v0.1 제약(명확한 예외)이다.
- **버전 안정성**
  - kotlinc 1.7.22, 2.1.10, 2.2.21, 2.3.0, 2.3.21 모두에서 리터럴 람다 → Java `Serializable` SAM은 indy로 컴파일되고 `SerializedLambda`를 얻는다.
  - K2(2.1.10~2.3.21)의 본문은 람다 이름을 정규화하면 바이트 단위로 같다.
  - K1(1.7.22)은 intrinsic과 boolean 물질화만 추가될 뿐 원자 구조는 같다. 추가 패턴(KP3, KP4, KP14)이 이를 흡수한다.
  - `[F]`의 String `hashCode` switch, enum `WhenMappings`, `Intrinsics.compare`, `RangesKt.until` 형태도 1.7.22와 2.3.21에서 같다.
- **Java와의 핵심 차이**

| 항목 | Java(javac) | Kotlin |
|---|---|---|
| `==` | 기본형은 분기, 참조는 `if_acmp` | 참조나 nullable이 한쪽이라도 있으면 `Intrinsics.areEqual(...)` **호출**(R11). boolean은 한쪽 이상이 `Boolean?`일 때만 `Boolean.valueOf` + `areEqual`이며, 이 경우 `is not distinct from`으로 번역한다(`[P]`c34, `[F]`t17). non-null `Boolean` 둘은 Java처럼 `if_icmpne`(`[F]`t01)이므로 3.3 규칙(G40)대로 v0.1에서 거부한다(KC7) |
| nullable 기본형 비교 | unboxing(NPE) | `dup; ifnonnull; pop; goto F`. 우변을 먼저 임시 local에 spill(R12 수정) |
| 조건 jump 시점 스택 | 비어 있음 | `?.`, `?:`, R12에서 **비어 있지 않음**. 3.3의 "값 ternary 거부" 규칙에 예외가 필요 |
| impl 메서드 | `lambda$x$0`, ACC_SYNTHETIC, `this` 캡처 시 인스턴스 메서드 | `<fn>$lambda$<n>`, `private static final`, **synthetic 아님**. `this`도 static 첫 인자 |
| 속성 레퍼런스 | `Order::getPrice`(impl = getter) | `Order::price`(impl = 합성 람다가 `Function1.invoke` 호출) |
| 엔티티 매핑 | 보통 getter와 Metamodel `javaMember`가 대응 | 어노테이션이 필드에 붙어 field-access. getter→속성 이름 fallback 필요 |
| 정렬 키 타입 | `R extends Comparable<? super R>` 통과 | nullable 키가 컴파일되지 않아 S2 bound 변경 필요 |
| 연관 경로 NULL | NPE | `?.`가 정의된 NULL 결과를 냄. **predicate에서 LEFT JOIN이 필수**(`[H]`c62or, j06or) |

- **정정 사항**: H2 검증에서 제안 번역 3개가 틀린 것으로 드러났고, 이 절에서 모두 정정했다. 아래에서는 이를 정정 1~3으로 부른다.
  1. **정정 1**(2026-11-10 D22로 재분류: 명시하지 않은 null은 SQL 의미이므로 가드 없는 `IN`이 **의도한 동작**. Kotlin 실행과 다른 점은 README에 명시): nullable `when` 주제를 가드 없이 `IN`으로 접는 번역(`[H]`c74, x02 naive IN)
  2. **정정 2**: nullable 쪽이 있는 `equals(ignoreCase = true)`를 `lower() = lower()`로 접는 번역(`[H]`c89n[s=null], c89n[s=x])
  3. **정정 3**: `!!`와 플랫폼 타입 경로의 inner join(`[H]`c62or, j06or)
- **문서화된 차이**: 아래 차이 1~3은 README에 적고 v0.1에서 허용한다.
  1. **차이 1, 박스 동일성**: `===`/R2 값 동등. 다른 박스의 `id === q`(1000)는 Kotlin에서 F, SQL에서 T다(`[H]`c25).
  2. **차이 2, Unicode 대소문자 접기**: `lower()` 양쪽 비교에서 다음 케이스가 BAD였다.
     - `equals(…, ignoreCase = true)`의 ſ, İ: `[H]`c89[s], c89[i]
     - i̇, i: `[H]`c89[İ]
     - `startsWith("s", ignoreCase = true)`의 ſ: `[H2]`c93s[s]

     Kelvin K는 `lower()`에서는 OK였고(`[H]`c89[k]) `upper()` 대안에서만 BAD였다. 그래서 `lower()`를 채택하며, K는 `lower()` 차이 목록에 넣지 않는다.
  3. **차이 3, NaN**
     - captured NaN(`[H]`c18[p=NaN])은 바인더가 거부한다(G6).
     - 저장된 NaN의 순서 비교(`[H]`c39, S 형태)는 H2에서 NaN이 최댓값이라 생기며, DB 의미를 따른다(G6).
- **검증 결과**: 제안 번역은 Kotlin이 예외 없이 결과를 내는 모든 행에서 `WHERE P`와 `WHERE NOT (P)` 둘 다 Kotlin과 같았다. 예외는 위 차이 2(c89[s], c89[i], c89[İ], c93s[s])와 차이 3(c18[p=NaN], c39), 그리고 R2로 결정된 차이 1(c25)뿐이다.
  - c93(`endsWith("z", ignoreCase = true)`)은 원래 하네스가 `ESCAPE`를 `LOWER()` 안에 넣어 SQL 구문 오류가 났다(`[H]` 699–701행). 그래서 `[H]` SUMMARY에는 c93이 없다.
  - `LOWER(o.code) LIKE LOWER(?) ESCAPE '\'`로 고쳐 `[H2]`에서 다시 실행했고, OK였다.

## 2. Kotlin 진입 메커니즘

**SAM 변환과 `SerializedLambda`**

`replaceObject` 방식(3.1)으로 얻었고 `setAccessible`은 쓰지 않았다(`/tmp/lql-kt/value/src/Api.java` `Api.grab`).

| 진입 형태 | 결과 | 근거 |
|---|---|---|
| 리터럴 람다 → Java `Serializable` SAM | indy, hidden class + `writeReplace`, SL 있음 | `[V]`e01–e36, `[P]` 전체 |
| 리터럴 람다 → Kotlin `fun interface … : Serializable` | indy, SL 있음. impl에 `checkNotNullParameter(it,"it")` prologue가 붙고, 반환이 기본형(`(LOrder;)D`)일 수 있으며 `instantiatedMethodType`은 `Double` | `[P]`k01–k03, `[V]`e23, e24 |
| SAM 생성자 `Where<Order>{…}`(저장 후 전달 포함) | indy | `[V]`e28, e29 |
| inline 함수의 SAM 타입 인자, `run{}`/`let{}`/`forEach{}` 안, non-inline Kotlin 람다 안, suspend 함수·람다 안 | indy | `[V]`e18, e21, e22, e36, `[VM]`i03–i05, m06 |
| Java 메서드 레퍼런스 `JOrder::getPrice` | impl = getter 자체, kind=invokeVirtual, cap0 | `[V]`r06, r07, r09 |
| Kotlin 속성 레퍼런스 `Order::price` → Java SAM | indy. impl `…$lambda$N(KMutableProperty1, Order)`, `captured[0]` = `MutablePropertyReference1Impl` 싱글톤 | `[V]`r01–r05, e31, `[VQ]`q11, `[VM]`m05 |
| `::isCheap`, `r::filterFn` | impl = 사용자 함수(public, synthetic 아님). 본문을 파싱 | `[V]`e32, e33 |
| `-Xsam-conversions=class` | SL 없음(실행한 81개 람다 전부 `SL=NONE`). `INSTANCE` 필드가 있는 named class | `/tmp/lql-kt/value/run-fsamconversionsclass.txt`, `/tmp/lql-kt/pred/samclass/` |
| `inline fun` 본문 안의 람다(plain·reified·crossinline) | SL 없음(`…$$inlined$…$1`, `InlKt$cheapInline$1`) | `[V]`e17, e20, `[VM]`i01 |
| 콜러블 레퍼런스 → Kotlin `fun interface` | SL 없음(`$sam$…$0` FunctionAdapter) | `[V]`r10, `[VM]`m01–m04 |
| `object : Where<Order>{…}`, Serializable이 아닌 SAM | SL 없음(Java와 같음) | `[V]`e25–e27 |

- `-Xlambdas=class`는 SAM 변환에 영향을 주지 않는다(`/tmp/lql-kt/value/run-flambdasclass.txt`).
- `-jvm-target` 1.8, 11, 17, 21 모두 `invokedynamic` + `LambdaMetafactory.altMetafactory`로 컴파일된다.
  - 1.8: `/tmp/lql-kt/fc/oj1.8`(2.3.21, class major 52), `/tmp/lql-kt/fc/o1722`(1.7.22)
  - 11: `/tmp/lql-kt/fc/oj11`(major 55)
  - 17: `/tmp/lql-kt/value/c2_17.jp`(major 61)
  - 21: `[P]` 전체
  - `/tmp/lql-kt/value/run-fjvmtarget18.txt`에는 실행 classpath 오류(`ClassNotFoundException: CasesKt`)만 있어 근거로 쓰지 않는다.

**impl 메서드 이름과 속성**

- 이름은 `<fn>$lambda$<n>`이다.

| 버전 | 최상위 람다 | 중첩 람다 |
|---|---|---|
| 2.2.21 이상 | 함수마다 카운터 | `$lambda$N$M` |
| 2.1.10, 1.7.22 | 파일 전체 카운터(`k01$lambda$123`, `joins$lambda$35`) | `$lambda$N$lambda$M` |

- 그 밖의 위치
  - companion: `Outer$Companion.<fn>$lambda$0`
  - `object`: `Obj.<fn>$lambda$0`
  - 속성 초기화식: `<prop>$lambda$0`
  - suspend: `<Continuation>.invokeSuspend$lambda$0`
- 이름은 컴파일러 버전이나 소스 편집에 따라 바뀐다. 캐시 키(3.5)에는 클래스, signature, 로더도 들어가므로 문제없다. 하지만 이름 형식에 의존하는 로직을 두지 않는다.
- impl은 `private static final`이고 **ACC_SYNTHETIC이 아니다.** `$deserializeLambda$`는 javac처럼 synthetic이다. 따라서 3.1의 "ACC_SYNTHETIC이면 메서드 레퍼런스"라는 판별은 모든 Kotlin 람다를 메서드 레퍼런스로 오판한다(3.8.6 변경 1).
- `implMethodKind`는 2.3.21 기준 항상 6(invokeStatic)이다. `this`를 캡처해도 그렇다.

**캡처**

| 캡처 대상 | impl 형태 | IR |
|---|---|---|
| `val`(기본형, nullable 포함) | descriptor 앞쪽 인자. `Long`은 `J`(2 slot), `Long?`은 `Ljava/lang/Long;` | `CapturedArg`(S). `[V]`e12–e14 |
| `var`(재대입이 없어도) | `Ref$IntRef`/`LongRef`/`ObjectRef`… 인자, 본문은 `getfield Ref$XRef.element` | 바인딩 시점에 `element`를 읽는 파라미터(KP13). `[P]`c114–c117, `[V]`e15, e16 |
| 클래스 멤버의 `this` | **static impl의 첫 인자**(`Repo.capThis$lambda$0(LRepo;LOrder;)Z`). 본문은 backing field `getfield`(private 포함) | HostExpr(KP13). `[P]`r01, r02, r04, `[V]`e02, e03 |
| extension receiver | static 첫 인자(`extFn$lambda$0(LOrder;LOrder;)Z`) | HostExpr. `[V]`e11 |
| companion `val` | `GETSTATIC`으로 `Repo`의 private static 필드 | HostExpr. `[P]`r03 |
| 최상위 `val`, `object` `val` | `invokestatic getTOP_STATUS`, `GETSTATIC Settings.INSTANCE` + getter | HostExpr(S). `[P]`c110, c111 |
| `const val` | 리터럴로 인라인 | Literal. `[P]`c109 |
| Kotlin 함수 타입 값 `val f: (Order)->Boolean` | 캡처한 `Function1.invoke` 호출 | C(KC4). `[V]`e30 |

- slot 순서: 캡처 값(long/double은 2 slot) → 람다 파라미터 → Kotlin 임시 local.
- 3.1의 static 슬롯 규칙이 그대로 적용된다. Kotlin은 instance impl이나 invokeSpecial impl을 만들지 않는다.

**컴파일러 버전 차이**

- 검증 범위: 1.7.22, 2.1.10, 2.2.21, 2.3.0, 2.3.21.
- SL 유무, kind, 캡처 수, signature는 모든 버전에서 같다.
- K2 버전끼리는 람다 이름을 정규화하면 본문이 같다.
- K1(1.7.22)에만 있는 것:
  - 캡처한 non-null 참조마다 `checkNotNullParameter(captured, "$name")` prologue(`"$dt"`, `"this$0"`, `"$tmp0"` …)
  - 플랫폼 타입 unboxing: `dup; checkNotNullExpressionValue(x,"it.id"); checkcast Number; Number.longValue()`
  - 분기가 여러 개인 `when`은 0/1 값을 먼저 만든 뒤 `ifeq`
  - `checkNotNullExpressionValue`의 메시지 문자열이 다름(`"it.tex"`와 `"getTex(...)"`). **이 문자열은 매칭하지 않는다.**
- 컴파일 환경 메모
  - 2.1.10은 stdlib 2.0.21이 필요하다.
  - 1.7.22는 `-Xskip-metadata-version-check`와 reflect 1.6.10이 필요하다.
  - classpath 파일: `/tmp/lql-kt/pred/cp-*.txt`, `/tmp/lql-kt/value/cp1722.txt`, `std1925.txt`

## 3. 지원 매트릭스

- 분류: **S** = Java와 같은 형태, **K** = Kotlin 전용이지만 지원, **C** = v0.1 제약(3.8.4).
- 패턴 이름(KPn)은 3.8.6에 정의한다.
- 번역의 `c1`, `s1`은 predicate·정렬용 LEFT JOIN alias다.
- H2 결과는 `[H]`/`[H2]`의 판정이고, "OK"는 `WHERE`와 `WHERE NOT` 모두 Kotlin과 같다는 뜻이다.
- "기본형"은 Kotlin `Int`, `Long`, `Double`, `Float`, `Boolean`(JVM 기본형) 속성이다. 이런 속성은 NULL을 담을 수 없다. Kotlin non-null **참조** 타입(`code: String`)은 런타임에 NULL이 아니라는 보장이 없다(R11).

**A. 비교와 동등**

| Kotlin 형태 | 바이트코드 패턴 | 분류 | 번역 | NULL 의미 | 근거 파일 |
|---|---|---|---|---|---|
| `status == 1`, `!= 1`, `count == 5L`, `<`·`<=`·`>`(Int, Long, Double) | `if_icmpxx` / `lcmp; ifxx` / `dcmpg\|dcmpl; ifxx` | S | `o.status = 1`, `<>`, `<` … | 기본형 컬럼이라 2값 | `[P]`c01–c03, c37–c40 |
| `status.compareTo(3) > 0`, `count.compareTo(5L) < 0` | `invokestatic Intrinsics.compare:(II)I` / `(JJ)I` 다음에 `ifxx`(0과 비교) | K | `o.status > 3`, `o.count < 5` | 기본형이라 2값. 0이 아닌 값과 비교(`compareTo(3) == 1`)는 거부 | `[F]`t02, t13(2.3.21, 1.7.22) |
| `price == 1.5` / `price != 1.5` | `dcmpg; ifne`(javac는 `dcmpl`) / `==`를 0/1로 물질화한 뒤 `ifne`(KP4) | S / K | `o.price = :p` / `o.price <> :p` | NaN은 G6. H2 -0.0도 OK | `[P]`c04, c05, `[H]`c04, c05, `/tmp/lql-kt/pred/javacheck` |
| `id == 1L`, `amount == 3`, `id == p`(p: `Long`)(`Long?`, `Int?`) | KP2: `getId; lconst_1; lstore t; dup; ifnonnull L; pop; goto F; L: longValue; lload t; lcmp; ifne F` | K | `(o.id IS NOT NULL AND o.id = 1)` | Kotlin 정의(`null == 1`은 거짓). `[H]` OK | `[P]`c06, c08, c12, `[VF]`f04, `[V2]`y01, `[H]`c06, c08, c12 |
| `id != 1L`(`Long?`) | KP2, X = TRUE 출구 | K | `(o.id IS NULL OR o.id <> 1)` | Kotlin 정의. `[H]` OK | `[P]`c07, `[H]`c07 |
| `status == q`(q: `Int?` 캡처) | KP2 변형: 우변 null 확인, null 경로 `pop; pop` | K | `o.status is not distinct from :p1`(권고) | `status`가 기본형이라 동치. 파라미터 1회 사용 | `[P]`c14, `[H]`c14[q=null], c14[q=1] |
| `rate == 2.5`(`Double?`), `tex == 1.5f`(`Float?`), `id == q`(`Long?`), `rate != p` | KP1 `Intrinsics.areEqual(Double,D)` / `(Float,F)` / `(Object,Object)` 후 `ifeq/ifne` | K | `o.rate is [not] distinct from :p` | null-safe 2값. captured NaN 거부(G6, 차이 3). `Float` 상수는 `Float`로 바인딩(G7) | `[P]`c09–c11, c18, `[VF]`f01–f03, `[H]`c09–c11, c10b, c18 |
| `name == "x"`, `name != s`, `!(name == "x")`(`String?`), `code == "x"`(`String`) | KP1 (+ 부정). non-null 타입 `code`도 null 확인 없이 `areEqual` | K | `o.name is [not] distinct from :p` | null-safe 2값. `=` 축약은 R11 개정 조건(스키마 non-null 또는 최종 양극성)에서만. Kotlin 타입이 `String`이어도 축약 근거가 아님 | `[P]`c19–c22, x08, `[H]`c19, c21, c22, x08 |
| `updateAt == dt`(`LocalDateTime?`) | KP1 | K | `o.updateAt is not distinct from :p1` | null-safe | `[P]`x05, `[H]`x05 |
| `flag == true` / `false`, `flag != true` | `iconst_1; Boolean.valueOf; areEqual` | K | `o.flag is not distinct from true` / `o.flag is distinct from true`(D14) | 2값 | `[P]`c28, c29, c32, x09, `[V2]`y05 |
| `active == flag`, `flag == active`(한쪽이 `Boolean?`) | `Boolean.valueOf(active); areEqual` | K | `o.active is not distinct from o.flag` | 2값. areEqual은 한쪽 이상이 `Boolean?`일 때만 나옴 | `[P]`c34, `[F]`t17, `[H]`c34 |
| `active == active2`(둘 다 non-null `Boolean`) | `getActive; getActive2; if_icmpne`(Java와 같음) | C | 거부(KC7, 3.3 G40). 컬럼끼리 `a = b` 지원은 3.8.8 #15 | – | `[F]`t01 |
| `active`, `!active`, `active == true` | `ifeq/ifne`, `iconst_1; if_icmpne` | S | `o.active` / `NOT` | – | `[P]`c26, c27, c33 |
| `id == null`, `!= null`, `flag == null` | `ifnonnull` / `ifnull` | S | `IS [NOT] NULL` | 2값(R3) | `[P]`c15, c16, c35 |
| `===`, `!==`, enum `==`/`===` | `if_acmpeq/ne` | S | R2 값 동등 `is [not] distinct from` | 다른 박스의 `id === q`(1000)는 Kotlin F, SQL T(차이 1) | `[P]`c23–c25, x12–x14, `[H]`c25 |
| 플랫폼 타입 `j.id == 1L`, `status == 1` | KP2(Kotlin은 `==`에서 플랫폼 타입을 nullable로 취급) | K | R12 형태 | Kotlin 정의 | `[P]`j01, j05, j11, `[H]`j01, j05, j11 |
| 플랫폼 타입 `== 캡처/리터럴` | KP1 | K | null-safe 동등 | 2값 | `[P]`j02, j03, `[H]`j02, j03 |
| 플랫폼 타입 `id > 3L`, `status < 3` | unboxing(K1: + KP3 + `checkcast Number; longValue`) | S | `o.id > 3` | SQL NULL(Kotlin은 NPE) | `[P]`j04, j10, `[H]`j04 |
| `createdAt > dt` | `compareTo; ifle` + `checkcast ChronoLocalDateTime`(no-op) | S | `o.createdAt > :p1` | SQL | `[P]`c45, j12 |
| `x.toLong()`, `%`, `+` | `i2l`, `irem`, `iadd` | S | 그대로(`%`는 D13) | SQL | `[P]`x06, x22, x23 |
| `active == (status > 1)`, `(id == 1L) == active` | G40(분기로 만든 boolean 두 개를 `if_icmpne`) | C | 거부(KC7) | – | `[P]`x01, x28 |
| `customer == c`(엔티티) | `areEqual(entity, entity)` | C | v0.2(R8, KC13) | – | `[P]`x07, `[V]`j05, e34 |
| `price.isNaN()` | 호출 | C | 거부(D11, KC14) | – | `[P]`x20 |

**B. `!!`, `?.`, `?:`, 스마트 캐스트, 스코프 함수**

| Kotlin 형태 | 바이트코드 패턴 | 분류 | 번역 | NULL 의미 | 근거 파일 |
|---|---|---|---|---|---|
| `flag!!`, `amount!! > 3`, `rate!! < 2.0`, `tex!! >= 1.5f`, `updateAt!! < dt` | KP3 `dup; Intrinsics.checkNotNull(Object)V` → identity | K | `o.flag`, `o.amount > 3` … | SQL NULL(NPE 행은 U) | `[P]`c36, c41, c43, c44, c46, `[H]`c36, c41, c46 |
| `name!! == "x"`, `customer!!.name == "x"` | KP3 + KP1 | K | `… is not distinct from :p`, 연관은 **LEFT JOIN** | NPE 행은 F. "`!!`은 `?.`과 같다"(R13) | `[P]`c62, x16, `[H]`c62, x16 |
| `status == 1 \|\| customer!!.name == "x"`(Java 엔티티 `customer.name`도 같음) | KP3 + 분기 | K | `o.status = 1 OR c1.name is not distinct from :p` + `left join o.customer c1` | inner join은 (1, null) 행을 잃음(**정정 3**) | `[H]`c62or, j06or |
| `count > p!!`(캡처) | 캡처 값에 KP3 | K | `o.count > :p1` | null `p`는 바인딩 시 거부(D10 ③) | `[P]`c48 |
| `customer?.name == "x"`, `customer?.name != null` | KP6 `dup; ifnull N; getName; goto M; N: pop; aconst_null; M:` 후 KP1 / `ifnonnull` | K | `left join o.customer c1 … c1.name is [not] distinct from :p` / `c1.name IS [NOT] NULL` | NULL 전파. inner join은 모두 BAD | `[P]`c58, c59, `[V2]`y03, y04, `[H]`c58, c58n, c59, c59n |
| `id == customer?.id` | KP6 + KP1, 경로 끝이 `@Id` | K | `o.id is not distinct from o.customer.id`(FK, join 없음) | FK는 연관이 NULL일 때 정확히 NULL | `[P]`c17, `[H]`c17 |
| `customer?.id == 5L` | KP7 + KP2 | K | `(o.customer.id IS NOT NULL AND o.customer.id = 5)`, 동치로 `(o.customer IS NOT NULL AND c1.id IS NOT NULL AND c1.id = 5)` | Kotlin 정의. `[H]` OK | `[P]`c60, `[V2]`y11, `[H]`c60, c60n, y11 |
| `(amount ?: 0) > 3`, `(amount ?: 0) == 0` | KP8 `dup; ifnonnull M; pop; <d>; M:` | K | `coalesce(o.amount, 0) > 3` / `= 0` | Kotlin 정의 | `[P]`c42, x26, `[H]`c42, x26 |
| `(name ?: "") == "x"`, `(name ?: label)` | KP8 + KP1 | K | `coalesce(o.name, '') is not distinct from :p` | Oracle `''`=NULL(R7) | `[P]`c61, `[V2]`y06, `[H]`c61 |
| `(customer?.name ?: "none") == "x"` | KP6 + KP8, 기본값 블록을 두 null 확인이 공유 | K | `coalesce(c1.name, 'none') is not distinct from :p` + LEFT JOIN | inner join은 BAD | `[P]`c63, `[H]`c63[x], c63[none] |
| `(name?.length ?: 0) > 3` | KP6 + KP8 | K | `coalesce(length(o.name), 0) > 3` | Kotlin 정의 | `[P]`c66, `[H]`c66 |
| `flag ?: false`, `!(flag ?: false)`, `flag ?: true`(조건 위치) | boolean KP8 → `ireturn`/`ifne`(KP4) | K | `(o.flag IS NOT NULL AND o.flag = TRUE)` / `(o.flag IS NULL OR NOT (o.flag = TRUE))`. `coalesce(flag,false) = true`는 쓰지 않음(G14, D14) | Kotlin 정의 | `[P]`c30, c31, `[H]`c30, c31 |
| `updateAt?.isAfter(dt) == true` | KP7 `dup; ifnull N; …; N: pop; iconst_0` + `isAfter` | K | `(o.updateAt IS NOT NULL AND o.updateAt > :p1)` | 가드 없는 형태는 NOT 아래에서 BAD | `[P]`c47, `[H]`c47, c47n |
| `name?.let{…} == true`, `id?.let{…} ?: false`, `name?.length == 3` | KP5(inline marker, `nop`) + KP7 + KP4. x10은 `checkcast Number; longValue` | K | `(x IS NOT NULL AND cond)` | Kotlin 정의 | `[P]`c96, x10, x11, x25, `[H]`c96, x10, x11, x11n, x25 |
| `label != null && label.length > 3`(스마트 캐스트), `name != null && name!!.length > 3`, `val t` 경유 | null-guard AND. getter 2회 호출 또는 local 2회 load | S (+K) | `(o.label IS NOT NULL AND length(o.label) > 3)` | 같은 컬럼이 원자 두 개에 나옴(2.3.7에서 안전함을 확인) | `[P]`c64, c65, c67, j09, x24, `[V2]`y07, y10, `[H]`c65, c65n, c67, y07, y10 |
| `run{…}`, `also{}`, `with(it){…}`, 사용자 `inline fun Order.isOpenInline()` | KP5(`it`를 local로 복사) + KP3 | K | 인라인된 본문 그대로 | 본문 규칙 | `[P]`c97, c98, c100, c102 |
| `takeIf{active}?.status == 1` | 엔티티 참조와 null의 merge(경로 의존) | C | 거부(KC6) | – | `[P]`c99 |
| 사용자 non-inline extension fun/property | 불투명 `invokestatic EntitiesKt.isOpen` | C | 거부(KC11) | – | `[P]`c101, c103 |

**C. 논리, `if`/`when` 제어**

| Kotlin 형태 | 바이트코드 패턴 | 분류 | 번역 | NULL 의미 | 근거 파일 |
|---|---|---|---|---|---|
| `&&`, `\|\|`, `!`, 드모르간 변형 | 원자 그래프(c51/c52는 javac처럼 바이트 동일) | S | R1~R4 축약 | 3값(R6) | `[P]`c49–c54 |
| `id == 1L \|\| amount == 3`, `!(id == 1L && amount != 3)` | 분기 그래프 안의 KP2. slot 1을 다른 타입으로 재사용(`lstore_1` 후 `istore_1`) | K | R12 형태의 OR / NOT-AND | `[H]` OK | `[P]`c55, c56, `[H]`c55, c56 |
| `if (a) x else false`, `if (a) true else x` | I6 | S | AND / OR | Kleene | `[P]`c68, c69 |
| `when { a -> true; b -> true; else -> false }` | OR | S | OR | – | `[P]`c71 |
| `if (a) x else y`, `when { a -> x; else -> y }` | 일반 ternary(I7) | C | 거부(KC6) | – | `[P]`c70, c75 |
| `when (status) { 1, 2 -> true; else -> false }`(`Int`) | KP12 `tableswitch`/`lookupswitch` | K | `o.status in (1, 2)` | 기본형 `Int`라 NULL이 없어 동치 | `[P]`c72, c73, `[F]`t15, `[H]`c72 |
| `when (name) { "a", "b" -> true; else -> false }`(`String?`, 상수 2개) | KP5(주제 `astore`) + KP1 두 개의 OR | K | `(o.name is not distinct from 'a' OR o.name is not distinct from 'b')`, 또는 `(o.name IS NOT NULL AND o.name IN ('a','b'))`. **가드 없는 `IN` 금지**(**정정 1**) | 가드 없는 `IN`은 NOT 아래에서 name=null 행을 잃음 | `[P]`c74, `[F]`s2n, t07, `[H]`c74 |
| `when (code) { "a", "b", "c" -> true; else -> false }`(String, 상수 3개 이상) | KP17: `astore t; aload t; invokevirtual String.hashCode; tableswitch\|lookupswitch; …; aload t; ldc "a"; invokevirtual String.equals; ifeq`. nullable 주제는 앞에 `aload t; ifnull <default>`. **`areEqual` 연쇄가 아님** | K(v0.1 범위는 3.8.8 #4, 미지원 시 KC10) | hashCode 분기는 버리고 `String.equals` 원자의 OR. nullable 주제는 `(o.code IS NOT NULL AND o.code IN ('a','b','c'))`, non-null 타입 주제는 `o.code IN (…)` | non-null 타입 주제가 DB NULL이면 Kotlin은 `hashCode`에서 NPE → SQL NULL(3.6) | `[F]`s3, s4, u01, u02(nullable). K1(`[F]`o1722)도 같음. H2 미검증 |
| `when (color) { Color.R, Color.G -> true; else -> false }`(enum) | KP18: `getstatic <File>Kt$WhenMappings.$EnumSwitchMapping$0:[I; swap; invokevirtual Color.ordinal; iaload; tableswitch`. nullable 주제는 `dup; ifnonnull L; pop; iconst_m1; goto M; L: …iaload; M:`(분기 1개면 `iconst_1; if_icmpne`) | K(v0.1 범위는 3.8.8 #4, 미지원 시 KC10) | `$EnumSwitchMapping$0`으로 switch 키를 enum 상수로 되돌려 `o.color in (:p1, :p2)`. nullable 주제의 -1 경로는 `IsNull` 원자: `(o.color IS NOT NULL AND o.color IN (…))` | Kotlin 정의(null은 -1 → else) | `[F]`t08, t09. K1도 같음. H2 미검증 |
| `when (amount) { 1, 2 -> true }`(`Int?`) | KP5 + KP2 두 번(IsNull 원자 중복) | K | `(a IS NOT NULL AND a = 1) OR (a IS NOT NULL AND a = 2)` | 2값 원자 중복은 안전(I1 완화) | `[P]`x02, `[H]`x02 |
| `when (status) { 1 -> active; else -> false }` | KP4(boolean 값이 `ireturn`으로 merge) | K | `(o.status = 1 AND o.active)` | – | `[P]`x03, `[H]`x03 |
| 레이블 `return@w true` | 다중 `ireturn`(I5) | S | OR | – | `[P]`x15, `[V]`e35 |
| `true && x`, `x && false`, `x \|\| true`, `const val` | 퇴화 jump(I5) / 인라인 리터럴 | S | `x` / FALSE / TRUE / 리터럴 | – | `[P]`c106–c109 |

**D. 범위와 컬렉션**

| Kotlin 형태 | 바이트코드 패턴 | 분류 | 번역 | NULL 의미 | 근거 파일 |
|---|---|---|---|---|---|
| `status in 1..5`, `!in`, `1 until 5`, `price in 1.0..10.0`, `status in lo..hi`(non-null 주제) | KP5(주제 임시 local) + 상수 왼쪽 비교. 상한 접힘(`<= 5` → `< 6`), `!in`은 KP4 | K | `(1 <= o.status AND o.status < 6)` / `NOT(...)` / `(:p1 <= o.status AND o.status <= :p2)` | 기본형 주제라 2값. 빈 범위 5..1도 OK | `[P]`c76–c78, c80, c85, `[H]`c76–c78, c77n, c80, c85 |
| `id in 1L..5L`(`Long?`), `amount in 1..5`, `amount in lo..hi`, `when (amount) { in 1..5 -> … }`(`Int?`) | KP11 `new LongRange\|IntRange; dup; a; b; <init>` + `ifnull` 가드 + `contains` | K(v0.1에서 C로 미룰 수 있음) | `(o.id IS NOT NULL AND o.id BETWEEN 1 AND 5)` / `NOT(...)` | 가드 없는 BETWEEN은 NOT 아래에서 BAD | `[P]`c79, x04, `[F]`t04, t05, `[H]`c79, c79n, x04, x04n |
| `amount in 1 until 5`, `!in`(`Int?`, `Long?`) | KP11 변형: `a; b; invokestatic RangesKt.until:(II)Lkotlin/ranges/IntRange;`(또는 `(JJ)…LongRange`) + `astore` + `ifnull` 가드 + `IntRange.contains(I)Z`. `new IntRange`가 아님 | K(KP11과 같은 범위 결정) | `(o.amount IS NOT NULL AND :a <= o.amount AND o.amount < :b)` / `NOT(...)` | 상한 배타. `b`가 `MIN_VALUE`면 Kotlin은 빈 범위이고 `x < MIN_VALUE`도 항상 거짓이라 같음. 가드 없는 `NOT`은 BAD | `[F]`t03(2.3.21, 1.7.22), `[H2]`c93u, c93un, c93um |
| `id in ids`, `id !in ids`, `name in names`(캡처 컬렉션) | KP10 `checkcast Iterable; CollectionsKt.contains(Iterable,Object)` | K | `(x IS NOT NULL AND x IN (:p1))` / `(x IS NULL OR x NOT IN (:p1))` | 빈 컬렉션은 `1=0`/`1=1`. null을 담은 컬렉션은 바인딩 시 거부 | `[P]`c81, c82, c84, `[H]`c81, c82, c84, c84n, c81e, c82e |
| `status in listOf(1,2,3)`, `setOf(1,2)` | `ANEWARRAY` + `listOf`/`setOf`를 람다 안에서 생성 | C | 거부(KC8) | – | `[P]`c83, x18 |

**E. 문자열**

| Kotlin 형태 | 바이트코드 패턴 | 분류 | 번역 | NULL 의미 | 근거 파일 |
|---|---|---|---|---|---|
| `code.startsWith("a")`, `code.contains("b")`, `startsWith(p)` | KP9 `StringsKt.startsWith$default` / `contains$default`(`checkcast CharSequence`). 호출이 `iconst_0; iconst_2; aconst_null`로 끝남. **`mask & 0x2`**(값 파라미터 index 1 = `ignoreCase`. extension receiver는 mask에 세지 않음)가 켜져 있으면 push된 Z는 무시하고 `ignoreCase = false` | K | `o.code like :p escape '\'`(`\`, `%`, `_` 이스케이프 필수) | 수신자가 NULL이면 Kotlin NPE → SQL NULL(3.6). 이스케이프가 없는 변형은 모두 BAD | `[P]`c86, c88, `[VS]`s02, `[H]`c86, c88 |
| `endsWith("z", ignoreCase = true)`, `startsWith(p, ignoreCase = true)`, `contains(p, ignoreCase = true)` | KP9 직접 호출 `StringsKt.endsWith\|startsWith(String,String,Z)Z`, `contains(CharSequence,CharSequence,Z)Z` | K | `lower(o.code) like lower(:p) escape '\'`(**`ESCAPE`는 `lower()` 밖**) | `[H2]`에서 OK. 예외는 ſ(c93s[s], 차이 2). `[H]`c93은 하네스 SQL이 `ESCAPE`를 `LOWER()` 안에 넣어 구문 오류였으며 재실행으로 대체 | `[P]`c93, `[H2]`c93, c93s, c93c |
| `name?.startsWith("a") == true`, `customer?.name?.startsWith("A") ?: false`, `name?.contains(p) == true` | KP7 + KP9 | K | `(x IS NOT NULL AND x like :p escape '\')` | 가드 없는 형태는 NOT 아래에서 BAD | `[P]`c87, x27, `[VS]`s03, `[H]`c87, x27, x27n |
| `code.equals(s, ignoreCase = true)` | KP9 `StringsKt.equals(String,String,Z)`(`String?.equals` 오버로드, `checkNotNull` 없음) | K | `lower(o.code) is not distinct from lower(:p1)`. `=`는 양쪽이 R11 기준 스키마상 non-null일 때만(**정정 2**). Kotlin 타입 `String`만으로는 부족 | Kotlin은 null-null이면 T, 한쪽 null이면 F. Unicode 차이(ſ, İ)는 문서화(차이 2). Kelvin K는 `upper()`에서만 차이라 `lower()`를 씀 | `[P]`c89, x21, `[H]`c89, c89n |
| `name.equals("x")`(`String?.equals`) | KP9 `equals$default`(mask 해석은 위와 같음) | K | `o.name is not distinct from :p` | null-safe | `[P]`c92, `[H]`c92 |
| `code.lowercase() == "abc"`, `{ it.name?.lowercase() }` | `getstatic Locale.ROOT; toLowerCase(Locale)` + `checkNotNullExpressionValue` | K | `lower(o.code) = :p` / `lower(o.name)` | – | `[P]`c95, `[V]`v14, `[H]`c95 |
| `name.isNullOrEmpty()` | 인라인: `checkcast CharSequence; astore; aload; ifnull T; aload; CharSequence.length; ifne F` | K | `(o.name IS NULL OR length(o.name) = 0)` | H2 `LENGTH`는 UTF-16 단위(Kotlin과 같음). MariaDB는 code point | `[P]`c90, `[VS]`s01, `[H]`c90 |
| `code.length > 3` | `String.length()` | S | `length(o.code) > 3` | 위와 같음 | `[P]`c94, `[H]`c94 |
| `isBlank()`, `isNullOrBlank()` | `StringsKt.isBlank`(Unicode 공백) | C | 거부(KC9) | SQL `trim`과 다름 | `[P]`c91, x17 |
| `"$a$b" == name`, `name == "pre-$a"`(캡처만) | `indy makeConcatWithConstants`, 캡처 값만 사용 | S | `:p1 is not distinct from o.name`(HostExpr) | – | `[P]`c104, c105 |
| `"${it.name}-${it.id}"`(엔티티) | `indy makeConcatWithConstants` | C | Java 문자열 결합과 같은 정책(KC17) | – | `[V2]`x09 |
| `coerceAtLeast(0)`, `name?.trim()`, `updateAt?.toLocalDate()` | `RangesKt.coerceAtLeast`, `StringsKt.trim; toString`, `LocalDateTime.toLocalDate` | C(v0.1) | `TranslationException`(KC20). 함수 매핑은 v0.2 | – | `[VS]`s05, s06, `[V2]`x08 |

**F. 캡처와 host 값**

| Kotlin 형태 | 바이트코드 패턴 | 분류 | 번역 | NULL 의미 | 근거 파일 |
|---|---|---|---|---|---|
| 캡처 `val`(`Long`, `Long?`, `String`, `Int`, `Double`, `Boolean`) | descriptor 앞쪽 인자 | S | 바인드 파라미터 | R9 | `[V]`e12–e14 |
| 캡처 `var` | KP13 `getfield Ref$XRef.element` | K | 바인딩 시점 값 | R9와 같음 | `[P]`c114–c117, `[V]`e15, e16 |
| 람다 안의 `n++` | `PUTFIELD Ref.element` | C | 거부(KC12) | – | `[P]`c118 |
| 클래스 안에서 `minStatus`, private `tag`, `minStatus + 1` | static impl 첫 인자 `Repo`, `getfield` | K | HostExpr(리플렉션) | – | `[P]`r01, r02, r04, `[V]`e02, e03 |
| companion `val LIMIT`, `const C` | private static 필드 `GETSTATIC` / 리터럴 | K | 파라미터 / 리터럴 | – | `[P]`r03 |
| 최상위 `val`, `object` `val` | `invokestatic` getter / `GETSTATIC INSTANCE` + getter | S | 파라미터 | – | `[P]`c110, c111 |
| `!f \|\| active`(f 캡처) | 캡처만 쓰는 원자 | S | `(:p1 = false OR o.active)` | – | `[P]`c112 |
| `lo == null \|\| count > lo` | 캡처 가드 + unboxing | S | `(:p1 IS NULL OR o.count > :p1)` | 가드가 있으면 D10 ③ 거부를 건너뜀(Java도 같은 바이트코드) | `[P]`x19, `[H]`x19 |
| `val f: (Order)->Boolean = {…}; where(f)` | 캡처한 `Function1.invoke` | C | 거부(KC4) | – | `[V]`e30 |

**G. 값, 정렬 키, 조인 람다**

| Kotlin 형태 | 바이트코드 패턴 | 분류 | 번역 | NULL 의미 | 근거 파일 |
|---|---|---|---|---|---|
| `{ it.price }`, `{ it.id }`, `{ it.updateAt }`, `{ it.tex }`, `{ it.active }` | getter(+ `valueOf`). boolean getter는 `getActive` | S | `o.price` … | NULL 키는 D16 | `[V]`v01, v07–v09, v17 |
| `{ it.status.toLong() }`, `{ it.status + 1 }`, `{ it.price * it.status }`, `-it.price`, `price * pLong` | `i2l`, `iadd`, `i2d; dmul`, `l2d` | S | 산술. 넓히는 변환은 CAST 없이 렌더링(G7) | strict | `[V]`v04c, v06, v11, v19, `[V2]`x06, x14–x16 |
| `{ it.status / 2 }` | `idiv` | S | 가드 아래에서만 거부(D13) | – | `[V2]`x07 |
| `{ it.customer?.name }`, `{ it.customer?.name?.length }`, `{ it.tex?.toDouble() }`, `{ it.tex?.times(2) }` | KP6 SafeNav 연쇄(null 경로들이 `pop; aconst_null` 하나를 공유) | K | `left join o.customer s1 … s1.name`, `length(s1.name)`, `o.tex`(**CAST를 넣지 않음**, G7), `o.tex * 2` | SQL NULL이 getter(left join), 산술, 함수로 전파되는 것은 Kotlin과 같다. float→double 넓힘의 값 일치는 G7(DB별 차이 문서화, v0.1 계약 밖) | `[V]`v02, v12, `[V2]`x01, x18 |
| `{ it.customer?.let { c -> c.name } }` | KP6 + KP5(`astore_1; iconst_0; istore_2` marker) | K | `s1.name` + LEFT JOIN | 위와 같음 | `[V2]`x02 |
| `{ it.customer!!.name }`, `{ it.name!!.length }`, `{ it.tex!! }`, `{ it.price * it.tex!! }` | KP3 | K | LEFT JOIN 경로(J2), `o.tex`, `o.price * o.tex` | NPE 행은 NULL 값. float 혼합 산술은 G7 | `[V]`v03, v04, v18, `[V2]`x10, x20 |
| `{ it.customer?.id }` | 경로 끝이 `@Id`인 KP6 | K | `o.customer.id`(join 없음, J3) | FK NULL = 연관 NULL | `[V]`v10 |
| `{ it.name ?: "" }`, `{ it.name.orEmpty() }` | KP8 `dup; ifnonnull Le; pop; ldc ""` | K | `coalesce(o.name, '')` | NULL 정렬 정책이 적용되지 않음. S9의 유일 키로 보지 않음 | `[V]`v05, v13, p05 |
| `{ it.customer?.name ?: "" }` | KP6의 null 경로가 KP8의 `pop`으로 감 | K | `coalesce(s1.name, '')` + LEFT JOIN | 정확 | `[V]`v20 |
| `{ it.id ?: 0L }`, `{ it.customer?.id ?: -1L }`, `{ it.price * (it.tex ?: 1f) }`, `{ it.tex ?: it.price.toFloat() }` | KP8 기본형: `dup; ifnull Ln; xxxValue; goto Le; Ln: pop; <d>; Le: [box]` | K | `coalesce(…, d)`(d는 식이어도 됨) | 정수·같은 타입끼리는 정확. float/double이 섞이면 G7 | `[V]`v04b, `[V2]`x11–x13, x19 |
| `{ it.flag ?: false }`, `{ it.flag ?: it.active }`(값·정렬 위치의 boolean elvis) | boolean KP8 | C | 거부(KC21). `coalesce(b, false)`는 OracleDialect에서 `coalesce(flag,0)` → ORA-00932(D14 b). CASE 형태는 v0.2 후보(3.8.8 #16) | – | `[V2]`x05, `[H]` SUMMARY의 c30 coalesce 변형(Oracle 금지 표시) |
| `{ if (it.active) it.price else 0.0 }`, `{ when (it.status) { 1 -> "a"; else -> "b" } }`, `{ it.name?.takeIf { … } }` | 값 ternary | C | 거부(KC6) | – | `[V2]`x03, x04, x17 |
| Java 엔티티 `{ it.customer.name }`, `{ it.price * it.tex }`(`Float!`) | getter 연쇄 / `checkNotNullExpressionValue; checkcast Number; doubleValue` | S / K | LEFT JOIN 경로(J2) / `o.price * o.tex` | NPE 행은 NULL 값. float 혼합은 G7 | `[V]`p02, p04 |
| `Api.key(Order::price)`, `Order::name`, `where(Order::active)`, `SortKey(Order::price)` | KP15: `aload_0; checkcast Function1; aload_1; invokeinterface Function1.invoke; checkcast R; [unbox]; xreturn`, `captured[0]` = `PropertyReference` | K | `Order::getPrice`와 같은 `MethodCall` → `o.price` | – | `[V]`r01–r05, e31, `[VQ]`q11, `[VM]`m05, `/tmp/lql-kt/value/src/PropIntro.java` |
| `Api.key(JOrder::getPrice)`, `::getName`, `::isActive` | impl = getter, invokeVirtual, cap0 | S | getter 합성 | – | `[V]`r06, r07, r09 |
| `{ o, c -> c.name }`, `{ _, c -> c.name }` | 인자가 여러 개인 static | S | `c.name` | – | `[V]`j01, j03 |
| `{ o, c -> o.customer?.id == c.id }`, 3인자 `i.order?.id == o.id && o.customer?.id == c.id`, `o.customer!!.id == c.id` | KP6/KP3 + KP1 | K | `join Customer c on o.customer.id = c.id`(c.id가 non-null PK라 ON에서 동치) | inner ON에서는 F와 U 모두 탈락(미검증) | `[V]`j02, j04, j06, `[V2]`z01, z03 |
| `{ o, c -> o.customer?.name ?: c.name }` | KP6 + KP8, 기본값이 다른 엔티티 경로 | K | `coalesce(s1.name, c.name)` | 정확 | `[V2]`z02 |
| `{ o, c -> o.price * c.id!! }` | KP3 + `longValue; l2d; dmul` | K | `o.price * c.id` | – | `[V]`j07 |

## 4. 제약사항 목록 (C)

- **예외 종류**
  - 진입 단계 실패는 `LambdaAnalysisException`이다.
  - 형태 실패는 `UnsupportedLambdaShapeException`이다.
  - 의미 실패는 `TranslationException`이다.
- **메시지 내용**: 메시지에는 3.3대로 Textifier dump, insn index, 소스 라인을 함께 넣는다.
- **진입 실패 감지 방법**
  - KC1~KC5는 모두 `replaceObject`가 처음 받은 객체가 `SerializedLambda`가 아닌 경우다.
  - 이때는 **즉시 중단**한다. 계속 직렬화하면 `Function1` 같은 필드에서 `NotSerializableException`이 난다.

| # | 거부 형태 | 오류 메시지 예 | 대안 문법 | 근거 |
|---|---|---|---|---|
| KC1 | `-Xsam-conversions=class`로 컴파일 | `Lambda compiled as class SKt$a$1 (no invokedynamic/SerializedLambda). Remove the -Xsam-conversions=class compiler option.` | 기본값 `-Xsam-conversions=indy` | `/tmp/lql-kt/pred/samclass/`, `/tmp/lql-kt/value/run-fsamconversionsclass.txt` |
| KC2 | `inline fun` 본문 안의 쿼리 람다(plain, reified, crossinline 래핑) | `Lambda compiled as class CasesKt$entry$$inlined$inlineWhere$1. Query lambdas must not be created inside inline functions; declare the helper non-inline or pass the lambda literal from the call site.` | inline이 아닌 헬퍼를 쓰거나, 호출부에서 리터럴 람다를 넘김(SAM 타입 인자는 지원, `[V]`e18) | `[V]`e17, e20, `[VM]`i01 |
| KC3 | Kotlin `fun interface`에 콜러블 레퍼런스(`kkey(Order::price)`, `kwhere(::isCheap2)`) | `Callable reference passed to Kotlin fun interface KSortKey is compiled as a FunctionAdapter (X$sam$KSortKey$0). Pass a lambda literal { it.price } or use the Java SAM overload.` | `kkey { it.price }`, 또는 Java SAM에 `Order::price`(KP15) | `[V]`r10, `[VM]`m01–m04 |
| KC4 | Kotlin 함수 타입 값을 SAM으로 변환(`val f: (Order)->Boolean; where(f)`) | `Lambda body invokes a captured kotlin.jvm.functions.Function1, which cannot be analyzed. Pass the lambda literal directly.` | `where { … }` 리터럴 | `[V]`e30 |
| KC5 | `object : Where<Order>{…}`, Serializable이 아닌 SAM | `Not a serializable lambda: anonymous class CasesKt$entry$16.` | 리터럴 람다 | `[V]`e25–e27 |
| KC6 | 값 `if`/`when`/`takeIf`, 일반 boolean ternary(`if (a) x else y`, `when { a -> x; else -> y }`), predicate의 `takeIf{…}?.x` | `Conditional value expression (if/when/takeIf) is not supported in v0.1 (insn 12, line 34).` | 조건을 `&&`/`\|\|`로 풀어 씀(`a && x \|\| !a && y`는 원자 중복이라 v0.2). 값 분기는 v0.2 PathEnumerator(CASE WHEN) | `[P]`c70, c75, c99, `[V2]`x03, x04, x17 |
| KC7 | boolean 식끼리 `==`(`active == (status > 1)`), non-null `Boolean` 컬럼끼리 `==`(`active == active2`, `if_icmpne`라 Java G40과 같은 형태) | `Comparison of two boolean expressions (XNOR) is not supported (G40).` | 한쪽이 `Boolean?`이면 `areEqual`이라 지원(`active == flag`). non-null 컬럼끼리는 `(active && active2) \|\| (!active && !active2)`(원자 중복이라 v0.2). 컬럼끼리 `a = b` 허용은 3.8.8 #15 | `[P]`x01, x28, `[F]`t01 |
| KC8 | 람다 안에서 만든 컬렉션(`in listOf(1,2,3)`, `setOf`) | `Collection created inside the lambda (listOf). Build it outside and capture it: val ids = listOf(1,2,3); where { it.status in ids }.` | 밖에서 만들고 캡처(KP10) | `[P]`c83, x18 |
| KC9 | `isBlank()`, `isNullOrBlank()` | `isBlank/isNullOrBlank use Unicode whitespace and have no portable SQL equivalent.` | `name.isNullOrEmpty()` 또는 명시 비교 | `[P]`c91, x17 |
| KC10 | **(D21 확정: v0.1 지원. M3 후반 항목이 일정상 늦어질 때만 쓰는 임시 대체)** 범위 객체(KP11, `RangesKt.until` 포함), `when` 주제 switch(KP12 Int, KP17 String `hashCode`, KP18 enum `WhenMappings`) | v0.1에서 범위가 너무 크면 `Nullable range / when-switch on String or enum subject is not supported in v0.1; use ==/\|\| or a null-checked comparison chain.` | 비교 연쇄 `x != null && x >= a && x < b`, `status == 1 \|\| status == 2`, `code == "a" \|\| code == "b" \|\| code == "c"`(KP1), `color == Color.R \|\| color == Color.G`(`if_acmp`) | 3.8.8 #4 |
| KC11 | 사용자 non-inline extension fun/property | `Call to non-inline function EntitiesKt.isOpen(Order) cannot be analyzed in v0.1.` | `inline fun`으로 선언(KP5), 또는 조건을 직접 씀. v0.2에서 호출 본문 분석 | `[P]`c101, c103 |
| KC12 | 캡처 변수 쓰기(`n++`) | `Lambda writes captured variable (PUTFIELD Ref$IntRef.element).` | 람다 밖에서 계산 | `[P]`c118 |
| KC13 | 엔티티끼리 `==` | `Entity equality (Intrinsics.areEqual(Customer, Customer)) is not supported until v0.2 (R8).` | `it.customer?.id == c.id` | `[P]`x07, `[V]`j05, e34 |
| KC14 | `isNaN()` | `isNaN is not supported (D11).` | – | `[P]`x20 |
| KC15 | `@JvmField` 엔티티 속성(GETFIELD) | `Entity field access POrder.jf (GETFIELD, @JvmField) is not supported; use a regular property (R10).` | 일반 `var`/`val` | `[VQ]`q01–q12 |
| KC16 | 이름이 맹글링된 getter(`internal` → `getInner$main`, value class → `getMoney-T4KvK0M`, `@get:JvmName`) | `Getter getInner$main cannot be mapped to a JPA attribute (mangled name).` | public 일반 속성 | `[VQ]`q01–q12 |
| KC17 | 엔티티 값이 들어간 문자열 템플릿 | Java 문자열 결합과 같은 정책 | – | `[V2]`x09 |
| KC18 | Java 합성 속성 레퍼런스 `JOrder::price` | 2.3.21에서 컴파일 오류("references to synthetic java properties is unsupported") → README에만 적음 | `JOrder::getPrice` | `[V]`r08 |
| KC19 | `Order::price.getter` 등 kotlin-reflect가 필요한 API | `KotlinReflectionNotSupportedError` → `kotlin-reflect is not supported; use Order::price.` | `Order::price` | `/tmp/lql-kt/value/src/PropMain.kt` |
| KC20 | 화이트리스트 밖 stdlib 함수(`coerceAtLeast`, `trim`, `toLocalDate`…) | `Unsupported function RangesKt.coerceAtLeast(II)I (TranslationException).` | v0.2 함수 매핑 | `[VS]`s05, s06, `[V2]`x08 |
| KC21 | 값·정렬 키 위치의 boolean elvis(`{ it.flag ?: false }`, `{ it.flag ?: it.active }`) | `Boolean elvis as a value/sort key is not supported in v0.1 (coalesce on boolean is not portable, ORA-00932).` | 정렬 키는 `{ it.flag }` + NULL 위치 지정(D16). 조건 위치의 `flag ?: false`는 지원(R15) | `[V2]`x05, D14 |

## 5. 3.6 규칙표에 추가할 행

**R11, R12 개정 문안** (기존 행을 바꿈)

| # | 람다 형태 | 의미(번역) | NULL 처리 | 근거 |
|---|---|---|---|---|
| R11 | **Kotlin `==`/`!=`**: `Intrinsics.areEqual(a, b)` 호출. 오버로드 `(Object,Object)`, `(Double,Double)`, `(Double,D)`, `(D,Double)`, `(Float,Float)`, `(Float,F)`, `(F,Float)`는 이름으로 매칭하고 descriptor는 가리지 않음. 이어지는 `ifeq/ifne`로 극성 결정. boxing된 boolean 상수(`iconst_1; Boolean.valueOf`)는 리터럴로 접음. non-null 기본형끼리(`Boolean` 둘 포함)는 `areEqual`이 아니라 분기라 3.3 규칙을 따름 | R2·R5와 같은 null-safe 값 동등 | `is [not] distinct from`. `=`/`<>` 축약은 다음 둘 중 하나일 때만. (a) 양쪽이 **스키마상 non-null**: 컬럼 쪽은 기본형 속성이거나 Metamodel `isOptional() == false`, `@Column(nullable = false)`, `@Id`이고, 캡처 쪽은 바인딩 시 non-null 확인. **Kotlin non-null 참조 타입(`code: String`)이나 getter의 `@NotNull`은 근거가 아니다.** Hibernate field access는 NULL 컬럼 값을 오류 없이 넣고, `areEqual`·`StringsKt.equals(String?,String?,Z)`는 null-safe라 Kotlin이 F(null-null이면 T)를 정의한다. (b) NOT push-down이 끝난 **최종 렌더링 시점의 양극성**이고 그 뒤에 술어를 감쌀 수 없을 때(`.not()`, 합성 없음). 그 밖에는 항상 `is [not] distinct from` | `[P]` KP1, c20, c89, `[H]`c19, x08, c11 |
| R12 | **Kotlin nullable 기본형과 기본형 비교**(`it.id == 1L`, `id: Long?`, 플랫폼 타입 포함): `<x>; [<rhs>; xSTORE t]; dup; ifnonnull L; pop; [pop;] goto X; L: unbox; [xLOAD t]; <cmp>; IFxx F`. X는 `==`이면 FALSE, `!=`이면 TRUE 출구. 우변이 nullable 캡처면 null 확인이 우변에 걸림(`pop; pop`) **(2026-11-10 D22 개정: 컴파일러가 삽입한 null 확인은 무시 → `x = c` / `x <> c`. 아래 원래 번역은 대체됨. ROADMAP R12)** | `(x IS NOT NULL AND x op c)`, `!=`는 `(x IS NULL OR x <> c)`. nullable 캡처 우변(`status == q`)은 `o.status is not distinct from :p` | Kotlin 정의(`null == 1`은 거짓) | `[P]`c06–c08, c12, c14, `[VF]`f04, `[V2]`y01, `[V]`e15, `[H]`c06, c07, c14 |

**신규 행**

| # | 람다 형태 | 의미(번역) | NULL 처리 | 근거 |
|---|---|---|---|---|
| R13 | **Kotlin `!!`와 null 확인 intrinsic**: `dup; Intrinsics.checkNotNull(Object[,String])V`, `dup; ldc; checkNotNullExpressionValue(Object,String)V`(플랫폼 타입), statement로 쓰인 `checkNotNullParameter(Object,String)V` **(2026-11-10 D23 최종: `!!`는 무시 — 컬럼 지정. ROADMAP D23)** | identity(값을 그대로 통과). `checkNotNullParameter`는 건너뜀. **(2026-11-10 D23 개정) `!!`가 연관 탐색에 쓰이면 ROADMAP R24의 오류 의미(도달 가드)** | Kotlin이라면 NPE인 곳이므로 **SQL NULL 값 의미**: 행을 지우지 않고 값을 NULL로 평가. 따라서 `x!!`와 `x?.`는 같은 HQL이 되고, `name!! == "x"`의 NPE 행은 F | 3.6 원칙, `[P]` KP3, `[H]`c62, x16 |
| R14 | **Kotlin `?.`**: `dup; ifnull N; <S1…Sn>; goto M; N: pop; aconst_null; M:`. 각 Si는 jump가 없는 getter, unbox, 넓히는 변환, 산술, 화이트리스트 함수 | `SafeNav(x, S1..Sn)` → 일반 SQL 식 `f(path)`. 넓히는 변환에는 CAST를 넣지 않음(G7) | Kotlin 정의(연결 중 하나라도 null이면 null) = SQL NULL 전파. 경로는 R16을 따름 | `[V]`v02, v12, `[V2]`x01, `[P]` KP6, `[H]`c58, c59 |
| R15 | **Kotlin `?:`**: `dup; ifnonnull M; pop; <d>; M:` 또는 `dup; ifnull N; <unbox\|member>; goto M; N: pop; <d>; M:` | `coalesce(x, d)`(d는 식이어도 됨). 단 **boolean elvis가 조건 jump나 `ireturn`으로 가면 원자로 분해**: `flag ?: false` → `(flag IS NOT NULL AND flag = TRUE)`, `!(flag ?: false)` → `(flag IS NULL OR NOT (flag = TRUE))`. 값·정렬 위치의 boolean elvis는 거부(KC21) | Kotlin 정의. boolean에 `coalesce`는 쓰지 않음(G14, D14 b: OracleDialect ORA-00932) | `[P]` KP8, `[V]`v05, v13, v20, `[V2]`x11–x13, `[H]`c30, c31, c42, c61, c63 |
| R16 | **연관 경로의 join**(Java·Kotlin 공통): where/order/select 안의 모든 to-one 홉. 소스가 `?.`, `!!`, 플랫폼 타입, Java getter 연쇄 중 무엇이든 같음 **(2026-11-10 D20 최종: 연관 경로는 컬럼 지정, LEFT JOIN, null 평가·도달 가드 없음. ROADMAP R24로 대체)** | 명시적 **LEFT JOIN**. 경로마다 alias 하나를 where·order·select에서 재사용. 경로가 대상의 `@Id`로 끝나면 join 없이 FK 경로(`o.customer.id`) | **(2026-11-10 D20 개정)** `?.` 탐색은 NULL 값 의미(행을 지우지 않음). `!!`·플랫폼 타입·Java getter 연쇄로 null 연관을 탐색하면 ROADMAP R24의 오류 의미(LEFT JOIN + 도달 가드). inner join은 `OR`/`NOT`/`IS NULL` 문맥에서 Kotlin·Java가 T인 행을 잃음 | `[H]`c62or, j06or, c17, c58, c63, `[V]`v02, v03, v10 |
| R17 | **Kotlin `===`/`!==`** | R2와 같은 값 동등. enum `==`도 `if_acmp`라 구분할 수 없으므로 거부하지 않음 | null-safe | `[P]`c23–c25, `[H]`c25(차이 1) |
| R18 | **범위 `x in a..b`, `a until b`, `!in`** | non-null 주제는 비교 연쇄 `(a <= x AND x <= b)`(컴파일러가 접은 상한 `< b+1` 포함). nullable 주제는 범위 객체를 씀. `new IntRange\|LongRange`(`..`)는 `(x IS NOT NULL AND x BETWEEN a AND b)`, `RangesKt.until(II\|JJ)`(`until`)는 `(x IS NOT NULL AND a <= x AND x < b)`. `!in`은 `NOT(...)` | Kotlin 정의(`null in 1..5`는 거짓). `until`의 상한은 배타이고, `b = MIN_VALUE`인 빈 범위도 `x < b`가 거짓이라 같음 | `[P]` KP11, `[F]`t03–t05, `[H]`c76–c80, x04, `[H2]`c93u, c93un, c93um |
| R19 | **컬렉션 `x in c`, `x !in c`**(`CollectionsKt.contains(Iterable,Object)`, c는 캡처 값) | `(x IS NOT NULL AND x IN (:p))` / `(x IS NULL OR x NOT IN (:p))` | Kotlin 정의. null을 담은 컬렉션은 바인딩 시 예외, 빈 컬렉션은 FALSE/TRUE(`IN (NULL)` 렌더링은 BAD) | `[P]` KP10, `[H]`c81, c82, c84, c84n, c81e, c82e |
| R20 | **`when (subject)`의 상수 분기**(모든 분기가 true/false로 끝남), OR로 이은 같음 비교 | 컴파일 형태가 주제 타입과 분기 수에 따라 다르다. `Int` 주제는 KP12 switch. String 주제는 상수 2개까지 R11/R12 원자의 OR, **상수 3개 이상이면 `areEqual` 연쇄가 아니라 `String.hashCode` switch + `String.equals`(KP17)**. enum 주제는 `WhenMappings` switch(KP18). 어느 경우든 분기별 같음 원자의 OR로 정규화. `IN`으로 접는 것은 주제가 기본형이거나 R11 기준 스키마상 non-null이거나 `(x IS NOT NULL AND x IN (...))` 가드가 있을 때만 | Kotlin 정의. **가드 없는 `IN` 금지**(nullable 주제) | `[P]`c72, c74, x02, `[F]`s2n, s3, t08, t09, `[H]`c74, x02 |
| R21 | **Kotlin 문자열 함수** `startsWith`/`endsWith`/`contains`(`$default`이면 `mask & 0x2`로 `ignoreCase` 기본값 판정, 직접 호출이면 push된 Z), `equals(s, ignoreCase)`, `String?.equals`, `lowercase()`, `length`, `isNullOrEmpty()` | LIKE는 `escape '\'`(`\`, `%`, `_` 이스케이프). `ignoreCase`는 `lower(x) like lower(:p) escape '\'`(`ESCAPE`는 `lower()` 밖). `equals(…, ignoreCase)`는 `lower(a) is not distinct from lower(b)`(R11 기준 양쪽이 스키마상 non-null일 때만 `=`). `isNullOrEmpty` → `(x IS NULL OR length(x) = 0)` | 수신자가 nullable이면 R14로 가드. Unicode 대소문자 접기(차이 2)와 `LENGTH` 단위는 DB에 따라 다름(문서화) | `[P]` KP9, `[H]`c86, c88, c89, c89n, c90, c92, c94, c95, `[H2]`c93, c93s, c93c |
| R22 | **Kotlin host 값**: 캡처 `var`(`Ref$XRef.element`), 캡처한 `this`의 private 필드, companion `val`의 private static 필드 | 바인드 파라미터 또는 HostExpr. 실행할 때마다 리플렉션으로 읽음. `PUTFIELD`는 거부 | R9와 같음 | `[P]` KP13, `[V]`e02, e15 |
| R23 | **Kotlin 속성 레퍼런스** `Order::price`(Java SAM에 전달) | getter `MethodCall`(Java `Order::getPrice`와 같은 IR). `captured[0]`의 `CallableReference.getSignature()/getOwner()`로 복원하며, 바인드 파라미터가 아니라 구조로 취급 | – | `[V]`r01–r05, `PropIntro.java` |

**R9 보충** (D10 ③ 수정)

캡처 값이 같은 캡처 인자에 대한 null 가드 아래에 있으면, 바인딩 시 null 거부를 하지 않는다.

- 예: `lo == null || count > lo` → `(:p1 IS NULL OR o.count > :p1)`
- Java도 같은 바이트코드다(`[P]`x19, `[H]`x19).

## 6. 분석기 변경점

**Kotlin 패턴 카탈로그**

- 두 보고서의 번호를 KP로 통일한다.
- 오른쪽 열은 이전 값 보고서의 K 번호다. 참고용이며 `[V]` 파일에는 없다.

| KP | 이름 | 형태 | IR | 이전 값 보고서 번호 |
|---|---|---|---|---|
| KP1 | `areEqual` | `invokestatic Intrinsics.areEqual(*,*)Z` | `Compare(EQ, a, b, nullSafe=true)` | K8 |
| KP2 | nullable 기본형 비교 | R12 개정 문안 | `IsNull(x)` 원자(T→X, F→L) + `Compare` 원자. rhs Expr은 1회 사용 | K7 |
| KP3 | null 확인 intrinsic | R13 | identity / statement 무시 | K1–K3 |
| KP4 | 물질화된 boolean 통과 | `iconst_0/1`이 goto, fall-through, `nop`을 거쳐 merge 라벨에 도달하고, 그 라벨이 `ifeq/ifne`, `iconst_1; if_icmpeq/ne`, `ireturn` 중 하나로 시작 | 상수를 jump 너머로 전달(threading). 한 경로가 boolean 식 e를 push하고 `ireturn`하면 `BoolTest(e)` 원자 | – |
| KP5 | 기호 local | 파라미터 뒤 slot에 대한 `xSTORE`/`xLOAD`. slot을 다른 타입으로 재사용할 수 있음. `iconst_0; istore_n` inline marker(load 없음), `nop` | slot → Expr 맵. dead store는 무시 | K12 |
| KP6 | SafeCall 값 | R14 | `SafeNav` | K4 |
| KP7 | 조건에 붙은 safe call | `dup; ifnull N; <cond>…; N: pop; iconst_0\|iconst_1`. N 블록은 여러 null 확인이 공유할 수 있음 | 주어진 극성의 `IsNull` 원자 + cond | K7b |
| KP8 | elvis | R15 | `Coalesce` 또는 원자 분해 | K5, K6 |
| KP9 | 문자열 함수 | R21. `$default` 변형은 `…, Z, I mask, Object`로 끝나고, **`mask & 0x2`**(값 파라미터 index 1 = `ignoreCase`. extension receiver는 mask에 포함되지 않음)가 켜져 있으면 push된 Z를 무시하고 `ignoreCase = false`. 관측된 mask 값은 `iconst_2`(`[P]`c86, c92, x27). 직접 호출 `(…, Z)Z`는 push된 Z가 상수여야 함 | `Like`/`Func`/`Compare` | K13 |
| KP10 | `CollectionsKt.contains` | `checkcast Iterable; …; CollectionsKt.contains(Iterable,Object)Z` | `In(x, CapturedArg)` + 가드 | – |
| KP11 | 범위 객체 | 범위 생성은 `new IntRange\|LongRange; dup; a; b; <init>`(`..`) 또는 `a; b; invokestatic RangesKt.until:(II)Lkotlin/ranges/IntRange;` / `(JJ)Lkotlin/ranges/LongRange;`(`until`). 결과는 보통 `astore`(KP5). 그다음에 `ifnull` 가드와 `contains(unbox x)` | `Range(a, b, upperExclusive)` → BETWEEN 또는 `a <= x AND x < b` + 가드 | – |
| KP12 | Int switch | `tableswitch`/`lookupswitch`(**Int 주제만**), 모든 target이 TRUE/FALSE로 끝남. String 주제(상수 3개 이상)와 enum 주제의 switch는 KP17, KP18 | 키를 target별로 묶어 `In` | – |
| KP13 | host 읽기 | `getfield Ref$*Ref.element`, 캡처한 `this`의 `getfield`, private static `getstatic` | HostExpr / `CapturedArg.element` | K10, K11 |
| KP14 | 투명 cast·unboxing | `checkcast` Iterable·CharSequence·ChronoLocalDateTime·Number, `Number.xxxValue()` | 무시 / unbox(`Number.doubleValue` on `Float!` = F2D) | – |
| KP15 | 속성 레퍼런스 | R23 본문 형태 | 합성 `MethodCall(EntityRef0, getter)` | K9 |
| KP16 | 기본형 반환 | fun interface impl의 `(LOrder;)D` 반환(`instantiatedMethodType`은 `Double`) | 기본형 `xreturn`과 `valueOf; areturn` 둘 다 허용. 키 타입은 `instantiatedMethodType`에서 가져옴 | K14 |
| KP17 | String `when` switch | `astore t; aload t; invokevirtual String.hashCode()I; tableswitch\|lookupswitch`. 각 case 블록은 `aload t; ldc "c"; invokevirtual String.equals(Object)Z; ifeq …`(해시 충돌이면 equals가 연쇄됨). nullable 주제는 앞에 `aload t; ifnull <default>` | hashCode 값과 dispatch는 버리고 `String.equals` 원자(`Compare(EQ)`)만 target별 OR로 남김. null 가드는 `IsNull` 원자 | – |
| KP18 | enum `when` switch | `getstatic <File>Kt$WhenMappings.$EnumSwitchMapping$0:[I; <subject>; invokevirtual E.ordinal()I; iaload; tableswitch\|lookupswitch`(non-null은 `swap` 포함). nullable 주제는 `dup; ifnonnull L; pop; iconst_m1; goto M; L: …iaload; M:` | 분석 시점에 `$EnumSwitchMapping$0`(ordinal → switch 키)을 읽어 switch 키를 enum 상수로 되돌림. 권고는 `WhenMappings.<clinit>`을 ASM으로 정적 분석하는 것(클래스 초기화 없음). 대안은 리플렉션 읽기. -1 경로는 `IsNull` 원자. 결과는 `In(x, enum 상수들)` | – |

**3.1 진입·로딩 변경**

1. **메서드 레퍼런스 판별을 바꾼다** (ACC_SYNTHETIC 판별 폐기).

   | 조건 | 처리 |
   |---|---|
   | `implMethodKind ∈ {invokeVirtual, invokeInterface, invokeSpecial}`이고, `capturedArgCount == 0`이고, impl owner가 `instantiatedMethodType` 첫 인자에서 assignable | getter 합성(Java `Order::getPrice`, Kotlin `JOrder::getPrice`) |
   | `newInvokeSpecial` | 거부 |
   | 그 밖 | **본문 파싱**. javac `lambda$…`, Kotlin `…$lambda$N`, `::isCheap`, `r::filterFn`, Java static 메서드 레퍼런스 |

   - KP15는 본문 파싱의 특수한 경우다.
   - 3.7 S13의 조인 키 메서드 레퍼런스 거부도 이 판별 위에서 다시 정의한다.
2. **SL 획득**: `replaceObject`의 첫 객체가 SL이 아니면 바로 중단하고 KC1~KC5 메시지를 낸다(class 이름의 `$$inlined$`, `$sam$`으로 메시지를 고름).
3. **KP15 복원**
   - `Class.forName("kotlin.jvm.internal.CallableReference", false, loader)`로 `getName`/`getSignature`/`getOwner().getJClass()`를 리플렉션 호출한다. kotlin-reflect 의존은 없다.
   - 대안은 `captured[0].getClass()`의 `get(Object)` 본문을 같은 분석기로 파싱하는 것이다.
   - 캐시 키는 impl 메서드 기준이고, 캡처한 레퍼런스는 call site별 `INSTANCE` 싱글톤이라 안전하다.
4. **KP16**: 키 타입은 `instantiatedMethodType`에서 가져온다.
5. **KP18 매핑 읽기**: `<File>Kt$WhenMappings` 클래스 바이트를 impl과 같은 로더에서 읽어 `<clinit>`의 `$EnumSwitchMapping$0[E.X.ordinal()] = k` 대입을 해석한다. 캐시 키는 impl 메서드 기준이다.

**3.3 1단계(값 스택) 추가**

- KP5 기호 local(slot 타입 재사용 허용), `nop`과 marker store는 no-op이다.
- KP14 투명 `checkcast` 목록과 `Number.xxxValue()` unboxing.
- KP3 intrinsic: `dup` 다음이면 통과, 아니면 인자만 소비.
- `NEW kotlin/ranges/IntRange|LongRange`와 `RangesKt.until(II|JJ)` 결과는 `Range` 값으로 둔다(KP11).
- KP17의 `String.hashCode()` 결과와 KP18의 `ordinal(); iaload` 결과는 switch 전용 `SwitchKey(x)` 값으로 두고, switch 밖에서 쓰이면 거부한다.
- **null 확인에서의 스택 fork**
  - 스택 fork는 **방금 `DUP`한 값에 대한 `IFNULL`/`IFNONNULL`** 에서만 허용한다.
  - join 라벨에서 스택 꼭대기만 다르면 `Conditional(IsNull(x), a, b)` 값을 만든다. 이를 `SafeNav`/`Coalesce`로 정규화하고, 정규화되지 않으면 거부한다.
  - 꼭대기가 boolean이면 KP4로 원자에 넣는다.
  - KP18 nullable 주제의 `iconst_m1` fork도 이 메커니즘으로 처리한다(`Conditional(IsNull(x), -1, SwitchKey(x))`).
  - 형태가 중첩되므로(`[P]`c60, c63, x25, x27) 바이트코드 템플릿이 아니라 이 일반 메커니즘으로 구현한다.
  - 캡처 값만 쓰는 diamond(`pd?.toFloat()`)는 HostExpr 하나로 접는다(`[VF]`f03).

**3.3 2단계(원자 기록) 추가**

- `==`/`!=`의 float bias 표는 `dcmpg`/`fcmpg`도 받는다. Kotlin은 `cmpg`를 쓰고, NaN 결과는 어느 쪽이든 같다.
- `<`, `<=`, `>`, `>=`는 javac와 같다.
- `Intrinsics.compare(II)I` / `(JJ)I` 다음의 `ifxx`(0과 비교)는 javac `lcmp; ifxx`와 같이 `Compare(op, a, b)` 원자로 기록한다. 0이 아닌 값과 비교하면 거부한다.
- KP2(우변 null 확인 변형 포함), KP7, KP12, KP17, KP18을 원자 기록 규칙에 넣는다.
- `if_acmp`는 Kotlin 클래스에서도 거부하지 않는다(enum `==`).
- boolean 값끼리 `if_icmpeq/ne`(G40)는 Kotlin에서도 거부한다. non-null `Boolean` 컬럼끼리의 `==`(`[F]`t01)도 이 형태다(3.8.8 #15).

**3.3 3단계(축약) 변경**

- KP4를 I5 퇴화 그래프 처리에 추가한다.
- "jump 시점 스택이 비어 있지 않으면 값 ternary로 보고 거부" 규칙에 예외를 둔다. KP2, KP6, KP7, KP8, KP18은 위의 fork 메커니즘으로 먼저 소화한다.
  - S7(정렬 키 본문의 분기 거부)에도 같은 예외를 둔다.
  - `if`/`when` 식과 `takeIf`는 계속 거부한다.
- **I1 완화**
  - "각 원자 1회 사용"은 **3값 원자**(비교, 호출)에만 적용한다.
  - 중복된 `IsNull` 원자와 캡처만 쓰는 원자는 2값이라, 값을 고정하면 read-once가 되어 Kleene 결과가 정확한 확장과 같다.
  - x02, c60/c60n이 `[H]`의 모든 행에서 일치했다.
  - 테스트 assert는 "3값 원자 수 = 3값 잎 수"로 바꾼다.
- 여러 null 확인이 공유하는 `pop; iconst_0` 블록은 공유 종단이지 원자 중복이 아니다.

**3.3 5단계 `ExprNormalizer` — intrinsic·stdlib 처리 목록**

| owner.method | 처리 |
|---|---|
| `Intrinsics.areEqual` (7개 오버로드) | KP1 → `Compare(EQ, nullSafe=true)` |
| `Intrinsics.compare(II)I`, `(JJ)I` + `ifxx` | `Compare(op, a, b)`(javac `lcmp`/`if_icmp`와 같음). `[F]`t02, t13 |
| `Intrinsics.checkNotNull(Object)`, `checkNotNull(Object,String)`, `checkNotNullExpressionValue(Object,String)` | KP3 identity(문자열 무시) |
| `Intrinsics.checkNotNullParameter(Object,String)` | statement 건너뜀 |
| `Boolean.valueOf`, `Integer.valueOf`, `Long.valueOf`, `Double.valueOf` / `xxxValue()`, `Number.xxxValue()` | boxing 통과 / unbox |
| `StringsKt.startsWith$default`, `endsWith$default`, `contains$default` | KP9 → LIKE(`mask & 0x2`이면 `ignoreCase = false`) |
| `StringsKt.startsWith(String,String,Z)`, `endsWith(String,String,Z)`, `contains(CharSequence,CharSequence,Z)` | KP9 → LIKE(Z 상수가 `ignoreCase`) |
| `StringsKt.equals(String,String,Z)`, `equals$default` | KP9 → null-safe (`lower`) 동등 |
| `String.toLowerCase(Locale)`(`lowercase()`), `CharSequence.length()`, `String.length()` | `lower()`, `length()` |
| `String.hashCode()` + switch, `String.equals(Object)` | KP17(switch 안에서만) |
| `<File>Kt$WhenMappings.$EnumSwitchMapping$0`, `Enum.ordinal()` + `iaload` + switch | KP18(switch 안에서만) |
| `CollectionsKt.contains(Iterable,Object)` | KP10 |
| `IntRange`/`LongRange.<init>`, `RangesKt.until(II)`/`(JJ)`, `IntRange`/`LongRange.contains` | KP11 |
| `LocalDateTime.isAfter`/`isBefore`, `compareTo` + `checkcast ChronoLocalDateTime` | `Compare` |
| `invokedynamic makeConcatWithConstants` | 캡처만 쓰면 HostExpr, 엔티티가 들어가면 KC17 |
| `StringsKt.isBlank`, `CollectionsKt.listOf`, `SetsKt.setOf`, `RangesKt.coerceAtLeast`, `StringsKt.trim`, `toLocalDate` | 거부(KC8, KC9, KC20) |

**R10 getter→속성 매핑 (Kotlin field-access 엔티티)**

1. Metamodel `javaMember`가 `Method`이면 그대로 쓴다.
2. 아니면 bean 규칙으로 찾고 Metamodel 속성 이름으로 확인한다.
   - `getX` → `x`
   - `isX()` → `isX`, 다음으로 `x`
   - boolean `active`는 getter가 `getActive`다.
3. 맹글링된 이름(`$module`, `-hash` 접미사, `@JvmName`)은 KC16, 엔티티 GETFIELD는 KC15로 거부한다.
4. R11/R20/R21의 `=`·`IN` 축약에 쓸 nullability도 이 단계에서 Metamodel(`isOptional()`, `@Column(nullable)`, `@Id`)로 정한다. Kotlin 타입의 nullability나 getter의 `@NotNull`은 쓰지 않는다.

**렌더러·바인더**

- R16 LEFT JOIN 계획
  - predicate에도 적용하고, alias는 where·order·select에서 공유한다.
  - `@Id`로 끝나는 경로는 FK 경로를 쓴다.
- `coalesce`를 렌더링한다. boolean `coalesce`는 렌더링하지 않는다(D14 b, KC21).
- 넓히는 숫자 변환에는 CAST를 넣지 않는다(G7).
- R11의 `=` 축약은 스키마 nullability 또는 최종 렌더링 시점의 극성으로 판정한다.
- R20의 가드 없는 `IN`을 금지한다.
- LIKE 이스케이프를 적용한다. `ignoreCase`에서는 `ESCAPE`를 `lower()` 밖에 둔다.
- `Float` 상수는 `Float`로 바인딩한다. 십진 텍스트를 거치지 않는다(G7, `[H]`c10b).
- null을 담은 컬렉션은 거부하고, 빈 컬렉션은 허용한다. Hibernate가 `1=0`/`1=1`로 렌더링한다(소스 확인).
- captured NaN은 거부한다.
- D10 ③ 가드 예외를 적용한다.
- `Ref.element`는 바인딩 시점에 읽는다.
- KP18의 enum 상수는 enum 파라미터로 바인딩한다(Hibernate 매핑이 ORDINAL/STRING을 결정).

**3.7 S2 bound 변경**

- `R extends Comparable<? super R>`를 `R extends Comparable<?>`로 바꾼다. 또는 와일드카드 `SortKey<T, ? extends Comparable<?>>`를 쓴다.
- 이렇게 해야 K2에서 `{ it.name }`(`String?`), `{ it.customer?.name }`, `Order::name`이 컴파일된다.
- 엔티티와 `Any?`는 계속 거부되고, Java 쪽 결과는 그대로다(`/tmp/lql-kt/value/bound/{B.java,B2.java,T.kt,T2.kt,JUse.java}`).

## 7. 마일스톤 반영안 (Kotlin을 v0.1 범위로)

**공통 기반**

- **Kotlin 테스트 소스**: Kotlin 테스트 코퍼스는 `query/src/test/kotlin`에 둔다. 케이스는 `/tmp` 실측 소스를 참고해 새로 작성한다.
  - **구현(2026-11-16)**: Kotlin Gradle 플러그인 2.3.21을 `query` 모듈에 적용했다. `src/test/kotlin`은 별도 source set `kotlinTest`이고, 컴파일된 Java 테스트 클래스(Lombok 엔티티)를 classpath로 본다. 실행은 기존 `test` 작업에 합쳐 `-Dlql.db`와 SQL 정답 파일 규칙을 그대로 쓴다.
  - 배포 의존성에는 Kotlin이 없다(`kotlin.stdlib.default.dependency=false`, `runtimeClasspath` 확인). Spring Boot BOM의 Kotlin 1.9 고정은 `ext['kotlin.version']`으로 2.3.21에 맞춘다.
  - `KotlinSelectTest` 3개(`?.` 경로, 캡처 값, `in`), `KotlinQueryTest` 11개(정렬, `exists`·`in`·스칼라 서브쿼리, data class·`Pair`·`Triple` projection, `groupBy`·`having`, 속성 대입·`by[열]` 대입, insert, `not exists` delete). H2·MariaDB 10.11·Oracle 23.26 통과(실측).
  - 컴파일러 버전 매트릭스(1.7.22~2.3.0)는 아직 두지 않았다.
- **컴파일러 버전 매트릭스**: Gradle 캐시의 `kotlin-compiler-embeddable`(1.7.22, 2.1.10, 2.2.21, 2.3.0, 2.3.21)로 같은 코퍼스를 별도 출력 디렉터리에 컴파일하는 테스트 전용 작업을 둔다(제안).
  - 버전별 classpath 조건은 3.8.2를 따른다.
- **런타임 의존**: main scope에 kotlin-stdlib나 kotlin-reflect 의존을 추가하지 않는다. KP15는 리플렉션 `Class.forName(…, false, loader)`로, KP18은 ASM 정적 분석으로 처리한다.

| 마일스톤 | 추가 작업 | 완료 기준 |
|---|---|---|
| **M1** 진입·로딩·슬롯 | 메서드 레퍼런스 판별 교체(3.8.6 3.1-1). KC1~KC5 즉시 중단과 메시지. KP15 속성 레퍼런스 복원. KP16 기본형 반환. Kotlin static impl의 `this`·extension receiver slot. Kotlin 테스트 소스 세트와 버전 매트릭스 작업 | `[V]` 진입 케이스 e01–e36, r01–r10, `[VM]`m01–m06, i01–i05, `[VQ]`q01–q12의 (kind, cap, slot, 판별 결과)가 2.3.21에서 기대값과 같음. KC1~KC5가 정의된 메시지로 실패. `Order::price`와 `JOrder::getPrice`가 같은 `MethodCall`. Kotlin `…$lambda$N`을 메서드 레퍼런스로 오판하는 경우 0건. main scope에 Kotlin 의존 0 |
| **M2** Expr IR·값 스택 | KP5 기호 local, KP14, KP3, `Range`(`NEW IntRange/LongRange`, `RangesKt.until`), `SwitchKey`. `IFNULL/IFNONNULL` 스택 fork와 `Conditional` → `SafeNav`/`Coalesce` 정규화(KP6, KP8). 캡처만 쓰는 diamond는 HostExpr | `ValueStackShapeTest` Kotlin판: `[V]`v02 → `SafeNav`, v20 → `Coalesce(SafeNav)`, `[V2]`x13 → `Coalesce(…, -1)`, `[VF]`f04 → spill이 반영된 `IsNull` + `Compare`, `[P]`c55 slot 재사용, `[V2]`x02 inline marker. 값 키 케이스(`[V]` v·p·j, `[V2]` x·y·z)에서 CCE·NPE·NoSuchElement 0건 |
| **M3** 원자 기록·축약 | KP2(우변 변형 포함), KP4, KP7, KP12, `Intrinsics.compare`. 범위 결정(3.8.8 #4)에 따라 KP11, KP17, KP18 또는 KC10. `cmpg` bias의 `==`/`!=`. 값 ternary와 S7 예외 처리. I1 완화(3값 원자만 1회 assert) | **Kotlin golden 코퍼스**: `[P]`의 S/K 케이스 전부(c01–c118, k01–k03, j01–j12, r01–r05, x01–x28)와 `[F]` t01–t20, u01–u02, s2n, s3, s4가 기대 정규 트리를 냄. C 케이스는 정의된 예외. Java와 의미가 같은 Kotlin 케이스는 Java G01~G41 그룹과 같은 트리(예: c49–c54, c64/j09). **3VL property test Kotlin판**: 2~4원자 read-once 트리를 Kotlin 원자 종류(non-null 비교, KP2, KP1, KP7, boolean KP8, 범위)로 생성하고 embeddable kotlinc로 배치 컴파일해 축약한 결과가 `RuleEvaluator`와 3^n 입력 전부에서 같고, 3값 원자는 1회 사용. 3값 원자를 중복시킨 대조군은 실패. **버전 매트릭스**: 같은 코퍼스의 정규 트리가 1.7.22, 2.1.10, 2.2.21, 2.3.0, 2.3.21에서 모두 같음 |
| **M4** 의미 해석 | 3.8.6 intrinsic·stdlib 표 구현. R10 field-access fallback과 Metamodel nullability. KC13~KC17, KC20, KC21 오류. KP13 host 읽기(`Ref.element`, private 필드, companion static). R17 `===` | `[VQ]`의 Kotlin 엔티티 속성 종류 q01–q12가 Metamodel 속성으로 매핑(`getActive` → `active`, `isOpen` → `isOpen`/`open`). KC15·KC16이 소스 라인과 함께 실패. `[P]`r01–r04가 HostExpr가 되고, 캡처 `var` 값을 바꾸면 다음 실행에 반영 |
| **M5** 렌더링·바인딩·실행 | R16 LEFT JOIN(predicate 포함, **Java getter 연쇄에도 적용**). R15 `coalesce`/원자 분해. R19·R20 가드. R21 LIKE 이스케이프와 `lower`. R11 `=` 축약 판정(스키마 nullability). 바인더 수정(Float, null 컬렉션, D10 ③ 가드 예외) | Hibernate를 거친 행 결과가 `[H]`(케이스 111개, 변형 139개)와 `[H2]`의 OK 판정과 같음. 정정 1(c74, x02), 정정 2(c89n), 정정 3(c62or, j06or) 회귀 테스트가 `WHERE`와 `WHERE NOT` 모두 통과. non-null 타입 `String` 컬럼에 NULL을 넣은 행에서 `code == "x"`의 `NOT`이 그 행을 유지. H2·MariaDB·Oracle에서 실행(Kotlin 케이스는 3.6 규칙 기준, DB별 차이는 G 표대로 분리) |
| **M5.5** 정렬 | S2 bound 변경. Kotlin 정렬 키 `{ it.customer?.name }`, `{ it.name ?: "" }`, `Order::price`. KP16 | Kotlin 컴파일 테스트: nullable 키·`?.`·`?:`·속성 레퍼런스는 컴파일되고, 엔티티와 `Any?` 키는 컴파일 실패. Java 컴파일 테스트 결과는 변경 전과 같음. 세 DB에서 고객 없는 주문이 `?.` 키 정렬 뒤에도 남음. boolean elvis 키는 KC21 |
| **M6** 오라클·캐시 | `PredicateOracle` Kotlin판: Kotlin 람다를 메모리에서 실행하고, NPE 행은 `RuleEvaluator`(R13: 값이 NULL인 것처럼)와 비교. 시드에 Unicode(ſ, İ, i̇, K, 😀), LIKE 특수문자, -0.0, NaN, null 연관, non-null 타입 컬럼의 NULL 추가. 버전 매트릭스를 CI 선택 작업으로. mutation 추가 | Kotlin 코퍼스가 H2·MariaDB에서 green(3.8.1의 문서화된 차이 1~3 제외). 다음 mutation이 각각 실패해야 함: `!!`/플랫폼 경로 inner join, nullable `when` 가드 없는 `IN`, nullable `lower(x) = lower(:p)`, KP2 `IsNull` 원자 누락, KP1 `=` 무조건 축약, Kotlin 타입 기반 `=` 축약, LIKE 이스케이프 누락, `ESCAPE`를 `lower()` 안에 넣기, nullable `until`의 가드 누락, null 컬렉션 허용. Kotlin 람다 1000회 호출에 바이트코드 해석 1회 |
| **M7** 마감 | README: "Kotlin ≥ 1.7 검증(1.7.22–2.3.21), 기본 `-Xsam-conversions=indy` 필요", KC 목록과 대안 문법, `===`는 값 동등, `x!!`는 `x?.`처럼 평가, Kotlin non-null 참조 타입은 NULL 보장이 아님, `ignoreCase`의 Unicode 차이, `LENGTH` 단위의 DB 차이, float/double 혼합(G7). 5절의 "컴파일러 제약(javac 전용)" 문구 교체. D17 지원 매트릭스 확정 | 외부 Kotlin 프로젝트(Gradle `kotlin("jvm")`)에서 `publishToMavenLocal` 아티팩트로 where·orderBy·join이 동작 |

## 8. 미해결 사항

1. **R16의 Java 영향**: predicate 경로 LEFT JOIN은 Java getter 연쇄에도 적용된다. 5절 "연관 경로 `o.getCustomer().getId()` → JPQL 암묵 join"과 충돌하므로 작성자 확인이 필요하다. 근거는 H2 j06or(Java 엔티티)뿐이고, 순수 Java 람다로는 실측하지 않았다.
2. **J3 FK 단축**: `o.customer.id`를 Hibernate 6이 join 없이 FK 컬럼으로 렌더링하는지는 측정하지 않았다. `[H]`에서는 SQL 수준 동치만 확인했다(c17).
3. **조인 람다 ON(J5)**: `=`와 `is not distinct from`이 inner ON에서 동치라는 것은 추론이고 실측하지 않았다.
4. **(해결, D21 확정 2026-11-10) KP11, KP12, KP17, KP18의 v0.1 범위** — 모두 v0.1 지원. 아래는 결정 전 기록: nullable 범위 객체(`..`, `until`), Int `when` switch, String `when`(상수 3개 이상, `hashCode` switch), enum `when`(`WhenMappings`)을 v0.1에서 지원할지, KC10으로 미룰지 결정이 필요하다.
   - KP17, KP18은 1.7.22와 2.3.21에서 형태가 같지만 H2 의미 검증은 하지 않았다.
   - 미루더라도 대안(`==`/`||` 연쇄)은 모두 지원 형태다.
5. **I1 완화 채택 여부**: "3값 원자만 1회"로 바꾸는 것은 2.3.5와 2.3.7의 불변식 문구를 수정해야 한다. 근거는 x02, c60/c60n의 H2 결과다. 이 완화가 없으면 3.8.1의 "원자 1회 사용" 축약은 Kotlin에서 성립하지 않는다.
6. **(해결, D23 확정 2026-11-10: `!!`는 오류 의미) `!!` 의미 선택**: 권고는 `!!`를 `?.`처럼 평가하는 것이다(NPE 행은 `is not distinct from`이면 F). `=`로 낮추면 NPE 행이 U가 되지만, `!!`와 `?.`가 다른 HQL을 낸다. README 문구 확정이 필요하다.
7. **`ignoreCase` Unicode 정책**: `lower()` 양쪽 적용 + 문서화가 권고다(ſ, İ, i̇는 차이 2, K는 `lower()`에서 차이 없음). 비ASCII 캡처 문자열을 거부하는 안은 권고하지 않는다. MariaDB에서는 collation이 적용된다.
8. **DB 범위**
   - Kotlin 의미 검증은 H2 2.3.230과 kotlinc 2.3.21에서만 했다. MariaDB·Oracle은 M5·M6에서 실행해야 한다.
   - MariaDB `CHAR_LENGTH`는 code point 단위다(미측정).
9. **c14 바인딩 타입**: `? IS NOT NULL`에 null 값을 바인딩할 때 Hibernate와 H2가 타입을 정할 수 있는지는 측정하지 않았다. 이 때문에 `col is not distinct from :p`를 권고한다.
10. **Hibernate 빈 리스트 렌더링** `1=0`/`1=1`: 6.6.5 소스(`AbstractSqlAstTranslator.visitInListPredicate`)에서 읽었을 뿐 end-to-end로 실행하지 않았다.
11. **v0.2 class-lambda 진입 경로**: crossinline, `-Xsam-conversions=class`로 만든 클래스에도 읽을 수 있는 본문(`test(Order)`, 캡처는 필드)이 있다. 이를 분석할지 검토가 필요하다. `$sam$` FunctionAdapter는 계속 불투명하다.
12. **(해결) `Intrinsics.compare`**: 명시적 `compareTo`(Int, Long)가 `Intrinsics.compare(II)I`/`(JJ)I` + `ifxx`로 컴파일된다(`[F]`t02, t13, 2.3.21과 1.7.22). 3.8.6 정규화 표에 `Compare(op, a, b)`로 넣었다.
13. **임시 local과 v0.2 `PathEnumerator`**: KP5 slot 맵을 ASM `Frame` 기반 `Interpreter<Expr>`로 옮길 때 같은 결과가 나오는지 확인하지 않았다.
14. **다른 실행 환경**: Kotlin 2.4 이후 버전, K2의 다른 백엔드 옵션, Kotlin Multiplatform은 실측 범위 밖이다.
15. **boolean 컬럼끼리 `==`**: non-null `Boolean` 컬럼 두 개의 `if_icmpeq/ne`(Kotlin `[F]`t01, Java도 같은 형태)는 3.3 G40 규칙에 따라 v0.1에서 거부한다.
    - 양쪽이 모두 단순 컬럼 읽기이고 기본형이라 NULL이 없으므로, XNOR 확장 없이 `a = b`로 번역할 수 있다.
    - Java·Kotlin 공통으로 이를 허용할지 결정이 필요하다.
16. **값·정렬 위치의 boolean elvis**: v0.1은 KC21로 거부한다. v0.2에서 `case when flag is null then … else flag end` 형태를 쓸 수 있는지는 OracleDialect에서 실측해야 한다(D14 b).
17. **R11 스키마 nullability 출처**: `SingularAttribute.isOptional()`이 `@Column(nullable = false)`만 있는 basic 속성에서도 false인지 Hibernate 6.6에서 확인하지 않았다. 확인 전에는 `@Id`, 기본형 속성, `@Basic(optional = false)`만 non-null로 본다.

## 9. 연산자 오버로딩 검토 (2026-11-10)

근거: kotlinc 2.3.21로 프로브 126개를 컴파일하고 `javap`로 확인한 뒤 새 Decoder로 디코딩함(OK 99, ERR 23). 산출물은 `/tmp/lql-kt-op/{a,b}`, 결과는 `/tmp/lql-kt-op/run.txt`. 바이트코드와 디코딩 결과는 실측이고, HQL 매핑과 DB 의미 차이는 추론이다.

### 9.1 쿼리 람다 안의 Kotlin 연산자

| 형태 | 바이트코드 | 처리 |
|---|---|---|
| `< > <= >=` (Int/Long/Double, `!!`) | `if_icmp*`, `lcmp`/`dcmpl` + `if*`, 언박싱 호출 | 지원. 언박싱은 건너뜀 |
| BigDecimal·String·LocalDate·BigInteger 대소 비교 | `compareTo(...)I` + `if*` (LocalDate는 `checkcast ChronoLocalDate`) | **의미 계층에서 `Cmp(op, compareTo(a,b), 0)` → `a op b`로 정규화 필요.** 왼쪽이 0인 형태(`0 op compareTo`)도 같음. JDK Comparable과 enum만 허용 |
| 명시적 `compareTo` (Int/Long/Double) | `Intrinsics.compare(II/JJ)I`, `Double.compare(DD)I` | 위와 같은 정규화 |
| enum `<` | `Enum.compareTo` (ordinal 비교) | STRING 매핑이면 SQL 결과가 달라지므로 `IN (ordinal이 더 작은 상수들)`로 펼친다 |
| `==` (String/BigDecimal/nullable) | `Intrinsics.areEqual(Object,Object)Z` | null-safe 동등(R11). **BigDecimal은 Kotlin `==`가 scale까지 비교(1.0 ≠ 1.00)하지만 SQL `=`는 값만 비교** → lambdaQL 규칙은 "값 동등"으로 하고 문서화 |
| enum `==` | `if_acmpne` | 지원(R2·R17) |
| 엔티티 `==` | `areEqual(Cust, Cust)` | 거부(KC13) |
| `+ - * / %`, 단항 `-` | `iadd…irem`, `ineg`, `i2l` | 지원. `%` → `mod`. **정수 `/`는 DB마다 결과가 다름**(MariaDB `5/2 = 2.5`) → 렌더러가 명시하거나 v0.1에서 거부 |
| BigDecimal·BigInteger `+ - * % -x` | stdlib inline operator라 `add/subtract/multiply/remainder/negate`가 직접 나옴 | SQL 연산자로 매핑 |
| BigDecimal `/` | `divide(x, RoundingMode.HALF_EVEN)` | SQL `/`. 그 밖의 `divide` 오버로드는 거부. 결과 scale은 DB 규칙을 따름(문서화) |
| String `+`, 문자열 템플릿 | indy `makeConcatWithConstants` | v0.2(`concat`). nullable이면 Kotlin은 `"null"`, SQL은 NULL |
| `x in list/set` | `List/Set.contains` (원소 non-null) 또는 `CollectionsKt.contains(Iterable,Object)` | IN(D29, R19). **`List/Set/Collection.contains`와 `ArraysKt.contains`도 IN 원천에 추가** |
| `in a..b`, `until`, `..<` (숫자) | 비교 연쇄로 접힘(상수가 왼쪽) | 지원(flip) |
| `in a..b` (LocalDate·BigDecimal·String) | `0 op x.compareTo(a)` 형태 | compareTo 정규화로 지원 |
| `d in d1..<d2`, `downTo` | `RangesKt.rangeUntil` + `OpenEndRange.contains`, `RangesKt.downTo` + `contains` | `a <= x AND x < b` 등으로 매핑. `step`은 거부 |
| nullable `x !in 1..10` | 상수 boolean 분기(KP4) + `ifne` | **Decoder 보완 필요**: 상수 0/1에 대한 `IFEQ/IFNE`는 한쪽 경로만 따라감 |
| `(cnt ?: 0) < 5`, `(price ?: ZERO) > q` | `dup; ifnull N; 언박싱; goto M; N: pop; 상수` | **Decoder 보완 필요**: 이 엘비스 형태와 `simplePush`의 `GETSTATIC`·`LCONST`·`DCONST`·`FCONST` |
| boolean infix `and`/`or`/`xor` | `iand`/`ior`/`ixor` (단락 평가 아님) | 상수는 접고 나머지는 `&&`/`||`로 안내하며 거부. 비트 연산은 거부 |
| 캡처 배열 `arr[0]` | `iaload` | Host 배열 원소로 지원하거나 친절한 오류 |
| 엔티티 경로의 `tags["k"]`, `lines[0]` | `Map.get`/`List.get` | 거부(캡처 값이면 Host) |
| 엔티티 멤버 `operator plus`/`compareTo`, 사용자 non-inline infix | `invokevirtual Ord.plus`, `invokestatic EKt.near` | 거부(getter가 아님, KC11) |
| `@JvmInline value class` 열 | 이름이 바뀐 getter(`getMoney2-SM3ulTg`), `equals-impl0`, `unbox-impl` | v0.1 거부(KC16). value class를 반환하는 select 람다는 indy가 아니라 별도 클래스로 컴파일되어 진입 단계에서 실패 → 오류 메시지에 안내 추가 |

### 9.2 lambdaQL API에 Kotlin 전용 operator를 둘까

**두지 않는다.** 기존 Java 메서드 이름만으로 Kotlin 연산자가 이미 동작한다(두 언어 컴파일 실측, `/tmp/lql-kt-op/b/src`).

| 후보 | 판단 |
|---|---|
| `by[o.status] = st` (DML `by.set(열, 값)`) | 유지. Java `<V> Ctx set(V, V)`가 Kotlin 인덱스 대입 연산자로 그대로 쓰임(`Ctx.set; pop`). setter 없는 `val` 엔티티에 유용. 분석기는 `set` 결과를 `pop`하는 문장 나열을 받아들이면 됨. `by[c] += 1`은 컴파일 오류(의도대로) |
| 서브쿼리 `x in sub.from(…).select { … }` / `!in` | 유지. `Values.contains`가 그대로 컴파일됨 |
| `Agg.sum(x) + 1 > 3` 등 | 유지. `checkcast` + 언박싱만 건너뛰면 됨 |
| 정렬 operator(`by + k`, `-k`, `by(k)`) | 거부. Java 사용자에게 `by.plus(x)` 같은 의미 없는 메서드가 보이고, `-k`는 Kotlin 전용 확장 함수가 필요하며 가독성도 나아지지 않음 |
| DB 함수 `invoke` operator | 거부. D31(인터페이스 static)과 충돌하고 인스턴스가 필요함 |
| `o["price"]` (`get` operator) | 거부. 문자열 경로라 타입 안전성이 깨짐 |
| infix | 불가. Java 메서드에는 `infix` 수식어를 붙일 수 없음 |
| BETWEEN | API 추가 없이 `x in a..b`로 씀(nullable은 `!!` 필요, `LocalDate?`는 컴파일 오류) |

### 9.3 반영할 작업

- **Decoder**: compareTo 정규화(의미 계층), 엘비스+언박싱 패턴, `simplePush` 확장, 상수 0/1 분기 접기(KP4), `IAND/IOR/IXOR` 처리, `xALOAD` 처리, value class 오류 메시지. (wide 슬롯 NPE는 이미 수정함)
- **의미 계층**: BigDecimal·BigInteger 메서드 → SQL 연산자, BigDecimal 값 동등 문서화, enum 대소 비교 → IN, 범위(`rangeUntil`, `downTo`, 캡처 `IntRange`/`ClosedRange`), IN 원천 확장, 정수 `/` 정책, 엔티티 경로의 `get`·사용자 operator·value class 거부.
