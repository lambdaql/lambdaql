package org.lambdaql.spring.sample;

import jakarta.persistence.EntityManager;

/** The hand-written fragment implementation (the classic Spring Data "Impl" class). */
public class MemberRepositoryImpl implements MemberRepositoryCustom {

    private final EntityManager em;

    /**
     * Makes the fragment.
     *
     * @param em the shared entity manager
     */
    public MemberRepositoryImpl(EntityManager em) {
        this.em = em;
    }

    /** {@inheritDoc} */
    @Override
    public long countByHand() {
        return em.createQuery("select count(m) from Member m", Long.class).getSingleResult();
    }
}
