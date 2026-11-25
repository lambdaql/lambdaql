package org.lambdaql.spring;

import org.lambdaql.query.Assign;
import org.lambdaql.query.AssignEach;
import org.lambdaql.query.DeleteQuery;
import org.lambdaql.query.InsertQuery;
import org.lambdaql.query.SelectQuery;
import org.lambdaql.query.SelectQuery.Where;
import org.lambdaql.query.SortSpec;
import org.lambdaql.query.UpdateQuery;
import org.lambdaql.query.UpsertQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.SequencedSet;

/**
 * lambdaQL queries for a Spring Data JPA repository (ROADMAP D34).
 * <p>
 * Add this interface to your repository. Spring Data finds the implementation by itself
 * ({@code META-INF/spring.factories}), so you write no {@code Impl} class:
 * {@code interface MemberRepository extends JpaRepository<Member, Long>, LambdaQlExecutor<Member>}.
 * Then use the methods in the repository's {@code default} methods, or from your services.
 * <ul>
 *   <li>The queries use Spring's transaction-bound {@code EntityManager}: inside {@code @Transactional} they see
 *       changes that are not flushed yet, and roll back with the transaction.</li>
 *   <li>Errors become Spring {@code DataAccessException}s, the same as other repository methods
 *       (for example {@link #findOne} with two rows gives {@code IncorrectResultSizeDataAccessException}).</li>
 *   <li>Lambdas with one argument never clash with {@code JpaSpecificationExecutor}, whose
 *       {@code Specification} lambdas have three.</li>
 * </ul>
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java">MemberRepository</a>):
 * {@snippet class="org.lambdaql.spring.sample.MemberRepository" region="repository"}
 *
 * @param <T> the entity type of the repository
 */
public interface LambdaQlExecutor<T> {

    /**
     * Starts a query on the repository's entity, for everything the short methods cannot do
     * (many {@code where} calls, {@code limit}/{@code offset}, run-time sort directions).
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java">MemberRepository</a>):
     * {@snippet class="org.lambdaql.spring.sample.MemberRepository" region="query"}
     *
     * @return a new select query on the repository's entity
     */
    SelectQuery<T> query();

    /**
     * Starts a query on another entity of the same persistence unit.
     *
     * @param type an entity class
     * @param <E>  the entity type
     * @return a new select query
     */
    <E> SelectQuery<E> from(Class<E> type);

    /**
     * Returns all rows that match.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java">MemberRepository</a>):
     * {@snippet class="org.lambdaql.spring.sample.MemberRepository" region="repository"}
     *
     * @param where the condition, for example {@code m -> m.isActive()}
     * @return the rows, in no fixed order
     */
    List<T> findAll(Where<T> where);

    /**
     * Returns all rows that match, sorted.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java">ExecutorTest</a>):
     * {@snippet class="org.lambdaql.spring.ExecutorTest" region="find-all-sort"}
     *
     * @param where the condition
     * @param sort  the sort
     * @return the rows in order
     */
    List<T> findAll(Where<T> where, SortSpec<T> sort);

    /**
     * Returns all rows that match, sorted with a Spring {@link Sort}.
     * Every property must be a persistent property ({@code PropertyReferenceException} otherwise, as in Spring Data).
     * {@code NullHandling.NATIVE} uses the lambdaQL default (NULL first for ascending, last for descending, ROADMAP D16).
     * {@code ignoreCase} is not supported yet.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java">ExecutorTest</a>):
     * {@snippet class="org.lambdaql.spring.ExecutorTest" region="find-all-spring-sort"}
     *
     * @param where the condition
     * @param sort  the Spring sort; {@code Sort.unsorted()} gives no order
     * @return the rows in order
     * @throws UnsupportedOperationException if an order uses {@code ignoreCase}
     */
    List<T> findAll(Where<T> where, Sort sort);

    /**
     * Returns one page of the rows that match.
     * The total comes from a {@code count} query, which is skipped when the page alone shows the total.
     * When the keys do not contain the id, the id is added as the last key, so rows do not move between pages (S9).
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java">ExecutorTest</a>):
     * {@snippet class="org.lambdaql.spring.ExecutorTest" region="page"}
     *
     * @param where    the condition
     * @param pageable the page number, size and sort; {@code Pageable.unpaged()} gives all rows
     * @return the page
     * @throws IllegalArgumentException      if the offset is larger than {@code Integer.MAX_VALUE}
     * @throws UnsupportedOperationException if an order uses {@code ignoreCase}
     */
    Page<T> findAll(Where<T> where, Pageable pageable);

    /**
     * Returns all rows that match as a set ({@code LinkedHashSet}). Like {@link #findAll(Where)}, the order is not fixed;
     * give a sort to fix it.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java">ExecutorTest</a>):
     * {@snippet class="org.lambdaql.spring.ExecutorTest" region="find-set"}
     *
     * @param where the condition
     * @return the rows
     */
    SequencedSet<T> findSet(Where<T> where);

    /**
     * Returns all rows that match as a set that keeps the sort order.
     *
     * @param where the condition
     * @param sort  the sort
     * @return the rows in order
     */
    SequencedSet<T> findSet(Where<T> where, SortSpec<T> sort);

    /**
     * Returns all rows that match as a set that keeps the order of a Spring {@link Sort}.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java">ExecutorTest</a>):
     * {@snippet class="org.lambdaql.spring.ExecutorTest" region="find-set"}
     *
     * @param where the condition
     * @param sort  the Spring sort
     * @return the rows in order
     */
    SequencedSet<T> findSet(Where<T> where, Sort sort);

    /**
     * Returns the row with the smallest id among the rows that match.
     *
     * @param where the condition
     * @return the row, or empty
     */
    Optional<T> findFirst(Where<T> where);

    /**
     * Returns the first row in the given order.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java">ExecutorTest</a>):
     * {@snippet class="org.lambdaql.spring.ExecutorTest" region="find-first"}
     *
     * @param where the condition
     * @param sort  the order
     * @return the row, or empty
     */
    Optional<T> findFirst(Where<T> where, SortSpec<T> sort);

    /**
     * Returns the first row in the order of a Spring {@link Sort}.
     *
     * @param where the condition
     * @param sort  the Spring sort
     * @return the row, or empty
     * @throws UnsupportedOperationException if an order uses {@code ignoreCase}
     */
    Optional<T> findFirst(Where<T> where, Sort sort);

    /**
     * Returns the only row that matches.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java">ExecutorTest</a>):
     * {@snippet class="org.lambdaql.spring.ExecutorTest" region="find-one"}
     *
     * @param where the condition
     * @return the row, or empty
     * @throws org.springframework.dao.IncorrectResultSizeDataAccessException if two or more rows match
     */
    Optional<T> findOne(Where<T> where);

    /**
     * Counts the rows that match.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java">ExecutorTest</a>):
     * {@snippet class="org.lambdaql.spring.ExecutorTest" region="count-exists"}
     *
     * @param where the condition
     * @return the number of rows
     */
    long count(Where<T> where);

    /**
     * Checks if a row matches. Reads one row at most and loads no entity.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java">ExecutorTest</a>):
     * {@snippet class="org.lambdaql.spring.ExecutorTest" region="count-exists"}
     *
     * @param where the condition
     * @return {@code true} if a row matches
     */
    boolean exists(Where<T> where);

    /**
     * Starts an UPDATE on the repository's entity (ROADMAP D28, D34). The name avoids a clash with
     * {@code JpaRepository} methods.
     * <p>
     * The statement runs when you call {@code execute()}, with Spring's shared {@code EntityManager}, so it needs
     * a transaction ({@code @Transactional}). Without one, the JPA provider throws {@code TransactionRequiredException};
     * inside a repository {@code default} method Spring turns it into {@code InvalidDataAccessApiUsageException}.
     * Like every JPA bulk statement, it does not change entities already loaded in the transaction.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java">MemberRepository</a>):
     * {@snippet class="org.lambdaql.spring.sample.MemberRepository" region="dml"}
     *
     * @return the start of the UPDATE
     */
    UpdateQuery<T> updateQuery();

    /**
     * Starts a DELETE on the repository's entity. Needs a transaction, like {@link #updateQuery()}.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java">MemberRepository</a>):
     * {@snippet class="org.lambdaql.spring.sample.MemberRepository" region="dml"}
     *
     * @return the start of the DELETE
     */
    DeleteQuery<T> deleteQuery();

    /**
     * Starts an INSERT on the repository's entity. Needs a transaction, like {@link #updateQuery()}.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java">MemberRepository</a>):
     * {@snippet class="org.lambdaql.spring.sample.MemberRepository" region="dml"}
     *
     * @return the start of the INSERT
     */
    InsertQuery<T> insertQuery();

    /**
     * Starts an upsert keyed by the {@code @Id} (ROADMAP D40). Needs a transaction, like {@link #updateQuery()}.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java">ExecutorTest</a>):
     * {@snippet class="org.lambdaql.spring.ExecutorTest" region="upsert"}
     *
     * @return the start of the upsert
     */
    UpsertQuery<T> upsertQuery();

    // ------------------------------------------------------------------ DML shortcuts (author decision 2026-11-16)
    // They need the caller's transaction (no @Transactional here, on purpose). Without one they fail before any SQL
    // is sent: InvalidDataAccessApiUsageException (from TransactionRequiredException).

    /**
     * Deletes every row that matches, with one SQL statement (bulk).
     * <p>
     * Not the same as Spring Data's {@code deleteAll()} / {@code deleteAll(Iterable)}: those load the entities and
     * remove them one by one (cascade, {@code @PreRemove}). This one does neither and does not change loaded entities.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java">ExecutorTest</a>):
     * {@snippet class="org.lambdaql.spring.ExecutorTest" region="dml-shortcuts"}
     *
     * @param where the condition
     * @return the number of deleted rows
     */
    int deleteAll(Where<T> where);

    /**
     * Deletes the only row that matches, with the "one row" protection: if two or more rows match,
     * nothing is deleted and {@code IncorrectResultSizeDataAccessException} is thrown.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java">ExecutorTest</a>):
     * {@snippet class="org.lambdaql.spring.ExecutorTest" region="dml-one"}
     *
     * @param where the condition, for example a unique key
     * @return 0 or 1
     */
    int deleteOne(Where<T> where);

    /**
     * Changes every row that matches (bulk). The condition comes first, the assignments second.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java">ExecutorTest</a>):
     * {@snippet class="org.lambdaql.spring.ExecutorTest" region="dml-shortcuts"}
     *
     * @param where the condition
     * @param set   the assignments, for example {@code m -> m.setGrade(7)}
     * @return the number of changed rows
     */
    int updateAll(Where<T> where, Assign<T> set);

    /**
     * Changes the only row that matches, with the "one row" protection (see {@link #deleteOne}).
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java">ExecutorTest</a>):
     * {@snippet class="org.lambdaql.spring.ExecutorTest" region="dml-one"}
     *
     * @param where the condition
     * @param set   the assignments
     * @return 0 or 1
     */
    int updateOne(Where<T> where, Assign<T> set);

    /**
     * Inserts one row.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java">ExecutorTest</a>):
     * {@snippet class="org.lambdaql.spring.ExecutorTest" region="dml-shortcuts"}
     *
     * @param values the assignments of the new row
     * @return 1
     */
    int insertOne(Assign<T> values);

    /**
     * Inserts one row per element (statements of at most about 500 values).
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java">ExecutorTest</a>):
     * {@snippet class="org.lambdaql.spring.ExecutorTest" region="dml-shortcuts"}
     *
     * @param rows   the elements
     * @param values the assignments for one element
     * @param <L>    the element type
     * @return the number of inserted rows
     */
    <L> int insertAll(Collection<L> rows, AssignEach<T, L> values);

    /**
     * Inserts one row, or updates it if a row with the same {@code @Id} exists (ROADMAP D40).
     * MariaDB counts an updated row as 2.
     *
     * @param values the assignments; the id must be assigned
     * @return the row count from the database
     */
    int upsertOne(Assign<T> values);

    /**
     * Upserts one row per element (one statement per row).
     *
     * @param rows   the elements
     * @param values the assignments for one element; the id must be assigned
     * @param <L>    the element type
     * @return the sum of the row counts from the database
     */
    <L> int upsertAll(Collection<L> rows, AssignEach<T, L> values);
}
