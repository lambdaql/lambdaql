package org.lambdaql.spring.sample;

/** A hand-written fragment, to check that it works together with LambdaQlExecutor. */
public interface MemberRepositoryCustom {
    /**
     * Counts all members with plain JPQL.
     *
     * @return the number of members
     */
    long countByHand();
}
