package org.lambdaql.engine.ir;

import org.objectweb.asm.Type;

/**
 * A reference to a method, as written in the bytecode.
 * lambdaQL does not load the class here; it only keeps the names.
 *
 * @param owner       internal name of the class (for example {@code java/lang/String})
 * @param name        method name (for example {@code equals})
 * @param descriptor  JVM method descriptor (for example {@code (Ljava/lang/Object;)Z})
 * @param isStatic    {@code true} for {@code INVOKESTATIC}
 * @param isInterface {@code true} if the owner is an interface
 *
 * <p>Example: {@code o.getName()} on a Lombok entity is
 * {@code MethodRef("com/acme/Order", "getName", "()Ljava/lang/String;", false, false)}.
 */
public record MethodRef(String owner, String name, String descriptor, boolean isStatic, boolean isInterface) {

    /**
     * Returns the return type.
     *
     * @return the JVM return type (for example {@code Type.BOOLEAN_TYPE})
     */
    public Type returnType() {
        return Type.getReturnType(descriptor);
    }

    /**
     * Returns the argument types.
     *
     * @return the JVM argument types, in order
     */
    public Type[] argumentTypes() {
        return Type.getArgumentTypes(descriptor);
    }

    /**
     * Returns the class name without the package.
     *
     * @return the simple name (for example {@code String} for {@code java/lang/String})
     */
    public String ownerSimpleName() {
        return owner.substring(owner.lastIndexOf('/') + 1);
    }

    /**
     * Checks the owner and name.
     *
     * @param owner internal class name
     * @param name  method name
     * @return {@code true} if both match
     *
     * <p>Example: {@code ref.is("java/util/Objects", "equals")}
     */
    public boolean is(String owner, String name) {
        return this.owner.equals(owner) && this.name.equals(name);
    }

    /**
     * Checks the owner, name and descriptor.
     *
     * @param owner      internal class name
     * @param name       method name
     * @param descriptor JVM method descriptor
     * @return {@code true} if all three match
     *
     * <p>Example: {@code ref.is("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;")}
     */
    public boolean is(String owner, String name, String descriptor) {
        return is(owner, name) && this.descriptor.equals(descriptor);
    }

    @Override
    public String toString() {
        return owner + "." + name + descriptor;
    }
}
