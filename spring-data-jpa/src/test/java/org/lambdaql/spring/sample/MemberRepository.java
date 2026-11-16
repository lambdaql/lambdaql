package org.lambdaql.spring.sample;

import org.lambdaql.spring.LambdaQlExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

// @start region="repository"
/** No Impl class: Spring Data finds the LambdaQlExecutor implementation by itself. */
public interface MemberRepository extends JpaRepository<Member, Long>, LambdaQlExecutor<Member>,
        JpaSpecificationExecutor<Member>, MemberRepositoryCustom {

    /** @return the active members */
    default List<Member> activeMembers() {
        return findAll(m -> m.isActive());
    }

    /**
     * @param name a name
     * @return the member with that name, or empty
     */
    default Optional<Member> byName(String name) {
        return findOne(m -> name.equals(m.getName()));
    }

    // @start region="query"
    /**
     * @param n how many
     * @return the active members with the highest grades
     */
    default List<Member> topGraded(int n) {
        return query()
                .where(m -> m.isActive())
                .orderBy((m, by) -> by.desc(m.getGrade()).asc(m.getId()))
                .limit(n)
                .toList();
    }
    // @end region="query"

    // @start region="dml"
    /**
     * @param grade the new grade
     * @return how many inactive members changed (call inside a transaction)
     */
    default int gradeInactive(int grade) {
        return updateQuery().set(m -> m.setGrade(grade)).where(m -> !m.isActive()).execute();
    }

    /** @return how many members without a team were deleted (call inside a transaction) */
    default int deleteWithoutTeam() {
        return deleteQuery().where(m -> m.getTeam() == null).execute();
    }

    /**
     * @param name a name
     * @return 1 (call inside a transaction)
     */
    default int addGuest(String name) {
        return insertQuery().values(m -> { m.setName(name); m.setActive(false); }).execute();
    }
    // @end region="dml"
}
// @end region="repository"
