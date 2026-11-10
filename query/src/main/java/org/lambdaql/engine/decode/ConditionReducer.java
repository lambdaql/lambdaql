package org.lambdaql.engine.decode;

import org.lambdaql.analyzer.LambdaAnalysisException;
import org.lambdaql.engine.ir.Pred;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns the decision graph back into a condition tree (structural reduction R1–R4, after Cifuentes).
 * <p>
 * Take a test node X and its child Y, where Y has only one parent (X). Then:
 * <pre>
 *   R1  X.t = Y, X.f = Y.f  →  X ∧ Y
 *   R2  X.t = Y, X.f = Y.t  →  ¬X ∨ Y
 *   R3  X.f = Y, X.t = Y.t  →  X ∨ Y
 *   R4  X.f = Y, X.t = Y.f  →  ¬X ∧ Y
 * </pre>
 * The joined node goes to Y.t when true and to Y.f when false. Each atom is used exactly once
 * (a "read-once" tree), so the SQL has the same logic as your Java/Kotlin code, also with NULL values.
 * If the graph cannot be reduced to one node (for example a value ternary {@code c ? x : y}, where one
 * atom is needed on two paths), an error is thrown.
 *
 * <p>Example:
 * <pre>{@code
 * // o -> (o.getStatus() == 1 || o.isActive()) && o.getPrice() > 10
 * Pred p = ConditionReducer.reduce(graph, "OrderRepo.lambda$find$0");
 * // ((status = 1 || truth(active)) && price > 10)
 * }</pre>
 */
public final class ConditionReducer {

    private ConditionReducer() {
    }

    /**
     * Reduces the graph to one condition.
     *
     * @param root  the graph from {@link Decoder#decodePredicate}
     * @param where a name for error messages (for example {@link org.lambdaql.engine.load.LambdaRef#describe()})
     * @return the condition; {@link Pred.Bool} when the result is constant
     * @throws org.lambdaql.analyzer.LambdaAnalysisException if the graph cannot be reduced, or if it returns a non-boolean value
     *
     * <p>Example: the graph of {@code o -> !(o.isActive() && o.getTex() > 1f)} reduces to
     * {@code !(truth(active) && tex > 1.0)}.
     */
    public static Pred reduce(Node root, String where) {
        Node r = root;
        while (true) {
            r = skipDegenerate(r);
            if (r instanceof Node.Leaf leaf) {
                return leaf.value ? Pred.Bool.TRUE : Pred.Bool.FALSE;
            }
            if (!(r instanceof Node.Test t)) {
                throw new LambdaAnalysisException("조건 람다가 boolean이 아닌 값을 반환합니다 — " + where);
            }
            if (t.onTrue instanceof Node.Leaf a && t.onFalse instanceof Node.Leaf b) {
                if (a.value == b.value) return a.value ? Pred.Bool.TRUE : Pred.Bool.FALSE;
                return NegationMinimizer.minimize(a.value ? t.atom : Pred.not(t.atom));
            }
            if (!step(r)) {
                throw new LambdaAnalysisException(
                        "조건을 논리식으로 복원하지 못했습니다(값 삼항 연산자 등 같은 조건이 여러 번 필요한 형태는 지원하지 않습니다) — "
                                + where + "\n" + dump(r));
            }
        }
    }

    /**
     * Tries one reduction step.
     *
     * @param root the graph root
     * @return {@code true} if two nodes were joined
     */
    private static boolean step(Node root) {
        List<Node.Test> order = new ArrayList<>();
        Map<Node, Integer> preds = new IdentityHashMap<>();
        collect(root, order, preds, new IdentityHashMap<>());
        boolean changed = false;
        for (Node.Test x : order) {
            x.onTrue = skipDegenerate(x.onTrue);
            x.onFalse = skipDegenerate(x.onFalse);
            if (x.onTrue instanceof Node.Test y && preds.getOrDefault(y, 0) == 1 && y != x) {
                if (x.onFalse == y.onFalse) {                       // R1
                    x.atom = Pred.and(x.atom, y.atom);
                    x.onTrue = y.onTrue;
                    return true;
                }
                if (x.onFalse == y.onTrue) {                        // R2
                    x.atom = Pred.or(Pred.not(x.atom), y.atom);
                    x.onTrue = y.onTrue;
                    x.onFalse = y.onFalse;
                    return true;
                }
            }
            if (x.onFalse instanceof Node.Test y && preds.getOrDefault(y, 0) == 1 && y != x) {
                if (x.onTrue == y.onTrue) {                         // R3
                    x.atom = Pred.or(x.atom, y.atom);
                    x.onFalse = y.onFalse;
                    return true;
                }
                if (x.onTrue == y.onFalse) {                        // R4
                    x.atom = Pred.and(Pred.not(x.atom), y.atom);
                    x.onTrue = y.onTrue;
                    x.onFalse = y.onFalse;
                    return true;
                }
            }
        }
        return changed;
    }

    /**
     * Skips a test whose two children are the same node (a jump that does not matter).
     *
     * @param n a node
     * @return the first node that is not such a test
     */
    private static Node skipDegenerate(Node n) {
        while (n instanceof Node.Test t && t.onTrue == t.onFalse) n = t.onTrue;
        return n;
    }

    /**
     * Collects test nodes children-first and counts the parents of each node.
     *
     * @param n     current node
     * @param order output: test nodes, children before parents
     * @param preds output: number of parents per node
     * @param seen  nodes already visited
     */
    private static void collect(Node n, List<Node.Test> order, Map<Node, Integer> preds, Map<Node, Boolean> seen) {
        if (!(n instanceof Node.Test t)) return;
        if (seen.put(n, Boolean.TRUE) != null) return;
        for (Node c : new Node[]{skipDegenerate(t.onTrue), skipDegenerate(t.onFalse)}) {
            preds.merge(c, 1, Integer::sum);
            collect(c, order, preds, seen);
        }
        order.add(t);
    }

    /**
     * Prints the graph for error messages.
     *
     * @param root the graph root
     * @return one line per test node, like {@code #1 [status = 1] T→T F→#2}
     */
    static String dump(Node root) {
        StringBuilder sb = new StringBuilder();
        Map<Node, Integer> ids = new IdentityHashMap<>();
        Set<Node> done = new LinkedHashSet<>();
        dump(root, sb, ids, done);
        return sb.toString();
    }

    private static void dump(Node n, StringBuilder sb, Map<Node, Integer> ids, Set<Node> done) {
        if (!(n instanceof Node.Test t) || !done.add(n)) return;
        sb.append("  #").append(id(n, ids)).append(" [").append(t.atom).append("] T→")
                .append(label(t.onTrue, ids)).append(" F→").append(label(t.onFalse, ids)).append('\n');
        dump(t.onTrue, sb, ids, done);
        dump(t.onFalse, sb, ids, done);
    }

    private static String label(Node n, Map<Node, Integer> ids) {
        return n instanceof Node.Test ? "#" + id(n, ids) : n.toString();
    }

    private static int id(Node n, Map<Node, Integer> ids) {
        return ids.computeIfAbsent(n, k -> ids.size() + 1);
    }
}
