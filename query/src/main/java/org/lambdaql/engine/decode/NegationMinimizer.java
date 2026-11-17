package org.lambdaql.engine.decode;

import org.lambdaql.engine.ir.Pred;

import java.util.ArrayList;
import java.util.List;

/**
 * Rewrites a condition into the same logic with the fewest NOTs.
 * <p>
 * javac compiles {@code a || b} as "if a jump to TRUE; if not b jump to FALSE", so the reduced tree often
 * looks like {@code !(a' && b')}, where {@code a'} is the opposite of {@code a}.
 * This class moves NOTs inside with De Morgan's laws when that removes NOTs:
 * <pre>{@code
 * !(status <> 1 && status <> 2)   ==>   status = 1 || status = 2
 * }</pre>
 * De Morgan's laws are also true in SQL three-valued logic (TRUE / FALSE / UNKNOWN),
 * and flipping a compare operator is free, so the SQL result does not change.
 * A NOT on a single condition is free too, because it has its own SQL form ({@code is not null}, {@code not exists},
 * {@code not in}, {@code = false}). So a NOT around a group is always moved inside:
 * {@code !(active && status > 1)} becomes {@code !active || status <= 1}. SQL then has no NOT around a group,
 * which keeps the conditions simple for the optimizer (SARGable rule, ROADMAP 3.9).
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/decode/NegationMinimizerTest.java">NegationMinimizerTest</a>):
 * {@snippet class="org.lambdaql.engine.decode.NegationMinimizerTest" region="de-morgan"}
 */
public final class NegationMinimizer {

    private NegationMinimizer() {
    }

    /**
     * A condition form and its NOT count.
     *
     * @param pred the condition
     * @param cost number of NOT nodes in it
     */
    private record Form(Pred pred, int cost) {
    }

    /**
     * The best form of a condition and the best form of its opposite.
     *
     * @param pos best form of {@code p}
     * @param neg best form of {@code NOT p}
     */
    private record Pair(Form pos, Form neg) {
    }

    /**
     * Returns the same logic with the fewest NOTs.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/decode/NegationMinimizerTest.java">NegationMinimizerTest</a>):
     * {@snippet class="org.lambdaql.engine.decode.NegationMinimizerTest" region="de-morgan"}
     * {@snippet class="org.lambdaql.engine.decode.NegationMinimizerTest" region="push-not"}
     *
     * @param p the condition
     * @return the rewritten condition
     */
    public static Pred minimize(Pred p) {
        return best(p).pos().pred();
    }

    private static Pair best(Pred p) {
        if (p instanceof Pred.Cmp c) {
            return new Pair(new Form(c, 0), new Form(Pred.not(c), 0));
        }
        if (p instanceof Pred.Bool b) {
            return new Pair(new Form(b, 0), new Form(Pred.not(b), 0));
        }
        if (p instanceof Pred.IsNull || p instanceof Pred.Truth || p instanceof Pred.In) {
            // a negated atom has its own form (is not null, not exists, not in, = false): no NOT around a group
            return new Pair(new Form(p, 0), new Form(new Pred.Not(p), 0));
        }
        if (p instanceof Pred.Not n) {
            Pair inner = best(n.operand());
            return new Pair(inner.neg(), inner.pos());
        }
        boolean isAnd = p instanceof Pred.And;
        List<Pred> items = isAnd ? ((Pred.And) p).items() : ((Pred.Or) p).items();
        List<Pred> pos = new ArrayList<>();
        List<Pred> neg = new ArrayList<>();
        int posCost = 0, negCost = 0;
        for (Pred item : items) {
            Pair b = best(item);
            pos.add(b.pos().pred());
            posCost += b.pos().cost();
            neg.add(b.neg().pred());
            negCost += b.neg().cost();
        }
        Pred posPred = join(isAnd, pos);
        Form posForm = new Form(posPred, posCost);
        Form outerNot = new Form(new Pred.Not(posPred), posCost + 1);
        Form deMorgan = new Form(join(!isAnd, neg), negCost);
        Form negForm = deMorgan.cost() < outerNot.cost() ? deMorgan : outerNot;
        return new Pair(posForm, negForm);
    }

    private static Pred join(boolean and, List<Pred> items) {
        Pred out = null;
        for (Pred item : items) {
            out = out == null ? item : and ? Pred.and(out, item) : Pred.or(out, item);
        }
        return out;
    }
}
