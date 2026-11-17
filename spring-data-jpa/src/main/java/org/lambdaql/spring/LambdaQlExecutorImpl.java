package org.lambdaql.spring;

import jakarta.persistence.EntityManager;
import org.lambdaql.query.Assign;
import org.lambdaql.query.AssignEach;
import org.lambdaql.query.DeleteQuery;
import org.lambdaql.query.InsertQuery;
import org.lambdaql.query.OrderedSelectQuery;
import org.lambdaql.query.QueryBuilder;
import org.lambdaql.query.SelectQuery;
import org.lambdaql.query.SelectQuery.Where;
import org.lambdaql.query.SortSpec;
import org.lambdaql.query.UpdateQuery;
import org.lambdaql.query.UpsertQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.core.RepositoryMethodContext;
import org.springframework.data.repository.core.support.RepositoryMetadataAccess;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.SequencedSet;

/**
 * The implementation of {@link LambdaQlExecutor}. You do not use this class directly:
 * Spring Data finds it through {@code META-INF/spring.factories} and uses it for every repository
 * that extends {@link LambdaQlExecutor} (ROADMAP SD2).
 * <p>
 * One object is shared by all repositories. The entity type of the current repository comes from
 * {@link RepositoryMethodContext}, which Spring Data fills because this class implements
 * {@link RepositoryMetadataAccess} (ROADMAP SD5).
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/sample/MemberRepository.java">MemberRepository</a>):
 * {@snippet class="org.lambdaql.spring.sample.MemberRepository" region="repository"}
 *
 * @param <T> the entity type
 */
public class LambdaQlExecutorImpl<T> implements LambdaQlExecutor<T>, RepositoryMetadataAccess {

    private final QueryBuilder qb;
    private final EntityManager entityManager;

    /**
     * Creates the executor. Spring Data calls this with its shared, transaction-bound {@link EntityManager}.
     * The class must have only one constructor (ROADMAP SD2).
     *
     * @param entityManager the shared entity manager
     */
    public LambdaQlExecutorImpl(EntityManager entityManager) {
        this.qb = new QueryBuilder(entityManager);
        this.entityManager = entityManager;
    }

    /** The outside sort policy of this persistence unit (ROADMAP D43). */
    private org.lambdaql.query.SortPolicy sortPolicy() {
        return org.lambdaql.query.SortPolicy.effective(entityManager.getEntityManagerFactory());
    }

    @SuppressWarnings("unchecked")
    private Class<T> domainType() {
        return (Class<T>) RepositoryMethodContext.getContext().getMetadata().getDomainType();
    }

    /** {@inheritDoc} */
    @Override
    public SelectQuery<T> query() {
        return qb.from(domainType());
    }

    /** {@inheritDoc} */
    @Override
    public <E> SelectQuery<E> from(Class<E> type) {
        return qb.from(type);
    }

    /** {@inheritDoc} */
    @Override
    public List<T> findAll(Where<T> where) {
        return query().where(where).toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<T> findAll(Where<T> where, SortSpec<T> sort) {
        return query().where(where).orderBy(sort).toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<T> findAll(Where<T> where, Sort sort) {
        Class<T> type = domainType();
        SelectQuery<T> q = qb.from(type).where(where);
        return sort.isSorted() ? q.orderBy(SpringSorts.toSpec(type, sortPolicy(), sort)).toList() : q.toList();
    }

    /** {@inheritDoc} */
    @Override
    public Page<T> findAll(Where<T> where, Pageable pageable) {
        Class<T> type = domainType();
        SelectQuery<T> base = qb.from(type).where(where);
        if (pageable.isUnpaged()) {
            List<T> all = pageable.getSort().isSorted()
                    ? base.orderBy(SpringSorts.toSpec(type, sortPolicy(), pageable.getSort())).toList()
                    : base.toList();
            return new PageImpl<>(all, pageable, all.size());
        }
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("page offset is too large: " + pageable.getOffset());
        }
        OrderedSelectQuery<T> page = pageable.getSort().isSorted()
                ? base.orderBy(SpringSorts.toSpec(type, sortPolicy(), pageable.getSort()))
                : base.limit(pageable.getPageSize());
        List<T> content = page.limit(pageable.getPageSize()).offset((int) pageable.getOffset()).toList();
        return PageableExecutionUtils.getPage(content, pageable, base::count);
    }

    /** {@inheritDoc} */
    @Override
    public SequencedSet<T> findSet(Where<T> where) {
        return query().where(where).toSet();
    }

    /** {@inheritDoc} */
    @Override
    public SequencedSet<T> findSet(Where<T> where, SortSpec<T> sort) {
        return query().where(where).orderBy(sort).toSet();
    }

    /** {@inheritDoc} */
    @Override
    public SequencedSet<T> findSet(Where<T> where, Sort sort) {
        Class<T> type = domainType();
        SelectQuery<T> q = qb.from(type).where(where);
        return sort.isSorted() ? q.orderBy(SpringSorts.toSpec(type, sortPolicy(), sort)).toSet() : q.toSet();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<T> findFirst(Where<T> where) {
        return query().where(where).first();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<T> findFirst(Where<T> where, SortSpec<T> sort) {
        return query().where(where).orderBy(sort).first();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<T> findFirst(Where<T> where, Sort sort) {
        Class<T> type = domainType();
        SelectQuery<T> q = qb.from(type).where(where);
        return sort.isSorted() ? q.orderBy(SpringSorts.toSpec(type, sortPolicy(), sort)).first() : q.first();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<T> findOne(Where<T> where) {
        return query().where(where).one();
    }

    /** {@inheritDoc} */
    @Override
    public long count(Where<T> where) {
        return query().where(where).count();
    }

    /** {@inheritDoc} */
    @Override
    public boolean exists(Where<T> where) {
        return query().where(where).exists();
    }

    /** {@inheritDoc} */
    @Override
    public UpdateQuery<T> updateQuery() {
        return qb.update(domainType());
    }

    /** {@inheritDoc} */
    @Override
    public DeleteQuery<T> deleteQuery() {
        return qb.delete(domainType());
    }

    /** {@inheritDoc} */
    @Override
    public InsertQuery<T> insertQuery() {
        return qb.insert(domainType());
    }

    /** {@inheritDoc} */
    @Override
    public UpsertQuery<T> upsertQuery() {
        return qb.upsert(domainType());
    }

    /** {@inheritDoc} */
    @Override
    public int deleteAll(Where<T> where) {
        return deleteQuery().where(where).execute();
    }

    /** {@inheritDoc} */
    @Override
    public int deleteOne(Where<T> where) {
        return deleteQuery().where(where).executeOne();
    }

    /** {@inheritDoc} */
    @Override
    public int updateAll(Where<T> where, Assign<T> set) {
        return updateQuery().set(set).where(where).execute();
    }

    /** {@inheritDoc} */
    @Override
    public int updateOne(Where<T> where, Assign<T> set) {
        return updateQuery().set(set).where(where).executeOne();
    }

    /** {@inheritDoc} */
    @Override
    public int insertOne(Assign<T> values) {
        return insertQuery().values(values).execute();
    }

    /** {@inheritDoc} */
    @Override
    public <L> int insertAll(Collection<L> rows, AssignEach<T, L> values) {
        return insertQuery().values(rows, values).execute();
    }

    /** {@inheritDoc} */
    @Override
    public int upsertOne(Assign<T> values) {
        return upsertQuery().values(values).execute();
    }

    /** {@inheritDoc} */
    @Override
    public <L> int upsertAll(Collection<L> rows, AssignEach<T, L> values) {
        return upsertQuery().values(rows, values).execute();
    }
}
