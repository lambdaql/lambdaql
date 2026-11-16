package org.lambdaql.spring.sample;

import org.lambdaql.spring.LambdaQlExecutor;
import org.springframework.data.jpa.repository.JpaRepository;

/** A second repository: the shared executor must use Team here, not Member. */
public interface TeamRepository extends JpaRepository<Team, Long>, LambdaQlExecutor<Team> {
}
