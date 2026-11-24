package org.lambdaql.spring

import org.lambdaql.query.By.desc

import jakarta.persistence.EntityManager
import org.lambdaql.query.QueryBuilder
import org.lambdaql.spring.sample.Member
import org.lambdaql.spring.sample.Team
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

// Kotlin repositories and a service for the manual (chapter 12, Kotlin track). They use the Java test entities
// Member and Team. Spring Data runs the bodies of the interface functions (JVM default methods since Kotlin 2.2).

// @start region="repository"
/** No Impl class: Spring Data finds the LambdaQlExecutor implementation by itself. */
interface KtMemberRepository : JpaRepository<Member, Long>, LambdaQlExecutor<Member> {

    fun activeMembers(): List<Member> =
        findAll { it.isActive }

    fun byName(name: String): Optional<Member> =
        findOne { it.name == name }

    fun topGraded(n: Int): List<Member> =
        query()
            .where { it.isActive }
            .orderBy { m -> desc(m.grade).asc(m.id) }
            .limit(n)
            .toList()

    // update, delete, insert: call inside a transaction
    fun gradeInactive(grade: Int): Int =
        updateQuery().set { it.grade = grade }.where { !it.isActive }.execute()

    fun deleteWithoutTeam(): Int =
        deleteQuery().where { it.team == null }.execute()

    fun addGuest(name: String): Int =
        insertQuery().values { it.name = name; it.isActive = false }.execute()
}
// @end

// @start region="shortcuts"
/** A list element for insertAll. */
data class NewMember(val name: String, val grade: Int)

/** The DML shortcuts: one call runs the statement (inside the caller's transaction). */
interface KtMemberShortcutRepository : JpaRepository<Member, Long>, LambdaQlExecutor<Member> {

    fun purgeWithoutTeam(): Int = deleteAll { it.team == null }

    fun deleteByName(name: String): Int = deleteOne { it.name == name }

    fun gradeInactive(grade: Int): Int = updateAll({ !it.isActive }) { it.grade = grade }

    fun rename(id: Long, name: String): Int = updateOne({ it.id == id }) { it.name = name }

    fun addGuest(name: String): Int = insertOne { it.name = name; it.isActive = false }

    fun addMembers(rows: List<NewMember>): Int =
        insertAll(rows) { m, r -> m.name = r.name; m.grade = r.grade; m.isActive = true }
}
// @end

/** Teams: upsert by @Id. */
interface KtTeamRepository : JpaRepository<Team, Long>, LambdaQlExecutor<Team>

// @start region="fragments"
/** LambdaQlExecutor together with JpaSpecificationExecutor and a hand-written fragment. */
interface KtMemberSpecRepository : JpaRepository<Member, Long>, LambdaQlExecutor<Member>,
    JpaSpecificationExecutor<Member>, KtMemberRepositoryCustom

/** A hand-written fragment. */
interface KtMemberRepositoryCustom {
    fun countByHand(): Long
}

/** Its implementation: Spring Data still finds it by the name (fragment interface name + Impl). */
class KtMemberRepositoryCustomImpl(private val em: EntityManager) : KtMemberRepositoryCustom {
    override fun countByHand(): Long =
        em.createQuery("select count(m) from Member m", java.lang.Long::class.java).singleResult.toLong()
}
// @end

// @start region="service"
/** open: without the kotlin-spring compiler plugin, Spring needs an open class for the @Transactional proxy. */
@Service
open class KtMemberService(private val qb: QueryBuilder) {

    @Transactional(readOnly = true)
    open fun countByName(name: String): Long =
        qb.from(Member::class.java).where { it.name == name }.count()
}
// @end
