package org.lambdaql.query;

import org.lambdaql.engine.ir.Expr;
import org.lambdaql.engine.sem.EntityModel;
import org.lambdaql.i18n.LqlMessage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tells (DEBUG, LQL-5010) when a {@code @Lob} column is used as a sort key, a distinct value or a group key
 * (ROADMAP D48, B3): H2 and MariaDB accept it, Oracle rejects it (ORA-22848, ORA-22849, measured). lambdaQL does not
 * change the query; the message helps to find the cause of such a database error. Not public API.
 */
final class LobCheck {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(LobCheck.class);
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    private LobCheck() {
    }

    /**
     * Checks values used in one place.
     *
     * @param model  the entity model
     * @param roots  the entity class of each root
     * @param values the values (only columns are checked)
     * @param usage  where they are used, for the message (for example {@code "order by"})
     */
    static void check(EntityModel model, List<Class<?>> roots, List<Expr> values, String usage) {
        if (!LOG.isDebugEnabled()) return;
        for (Expr e : values) {
            if (!(e instanceof Expr.Column c) || c.root() < 0 || c.root() >= roots.size()) continue;
            Class<?> owner = roots.get(c.root());
            List<String> path = c.path();
            for (int i = 0; i < path.size() - 1 && owner != null; i++) {
                owner = model.property(owner, path.get(i)).map(EntityModel.Property::javaType).orElse(null);
            }
            if (owner != null && isLob(owner, path.get(path.size() - 1))) {
                String column = String.join(".", path);
                if (LOGGED.add(column + "|" + usage)) LOG.debug("{}", LqlMessage.LOB_IN_SORT_OR_KEY.format(column, usage));
            }
        }
    }

    private static boolean isLob(Class<?> owner, String name) {
        for (Class<?> c = owner; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equals(name) && f.isAnnotationPresent(jakarta.persistence.Lob.class)) return true;
            }
            String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            for (Method m : c.getDeclaredMethods()) {
                if ((m.getName().equals("get" + cap) || m.getName().equals("is" + cap)) && m.isAnnotationPresent(jakarta.persistence.Lob.class)) {
                    return true;
                }
            }
        }
        return false;
    }
}
