package org.lambdaql.i18n;

import org.lambdaql.LambdaQLException;

import java.util.Locale;

/**
 * Every message of lambdaQL: a stable code {@code [LQL-xxxx]} and the number of arguments (ROADMAP 3.10, D33).
 * The text is in the bundles {@code org/lambdaql/i18n/LqlMessages*.properties} in English (root), Japanese,
 * Chinese (Simplified) and Korean. The language is chosen by {@link LqlMessages#locale()}.
 * <p>
 * Code ranges: 1xxx input and lambda loading, 2xxx bytecode decoding, 3xxx meaning and values computed in Java,
 * 4xxx rendering and running, 5xxx warnings. A code is never reused after a release.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java">LqlMessagesTest</a>):
 * {@snippet class="org.lambdaql.i18n.LqlMessagesTest" region="format"}
 */
public enum LqlMessage {

    /** LQL-1001: The lambda is null. */
    LAMBDA_NULL("LQL-1001", 0),

    /** LQL-1002: Only Serializable lambdas can be analyzed. Use a lambdaQL lambda type, not java.util.function: &#123;0&#125; */
    LAMBDA_NOT_SERIALIZABLE("LQL-1002", 1),

    /** LQL-1003: Could not get the SerializedLambda of &#123;0&#125;. */
    SERIALIZED_LAMBDA_FAILED("LQL-1003", 1),

    /** LQL-1004: The argument is a Serializable object but not a lambda (an anonymous class or a Kotlin object). Write a lambda: &#123;0&#125; */
    NOT_A_LAMBDA("LQL-1004", 1),

    /** LQL-1005: The class file was not found: &#123;0&#125; */
    CLASS_FILE_NOT_FOUND("LQL-1005", 1),

    /** LQL-1006: The method was not found in the class file: &#123;0&#125; */
    METHOD_NOT_FOUND("LQL-1006", 1),

    /** LQL-1007: The class file could not be read: &#123;0&#125; */
    CLASS_FILE_UNREADABLE("LQL-1007", 1),

    /** LQL-1008: The class was not found: &#123;0&#125; */
    CLASS_NOT_FOUND("LQL-1008", 1),

    /** LQL-1009: A method reference cannot be used as a condition yet. Write a lambda. */
    METHOD_REFERENCE_IN_WHERE("LQL-1009", 0),

    /** LQL-1010: An assignment needs a lambda, not a method reference. */
    METHOD_REFERENCE_IN_ASSIGNMENT("LQL-1010", 0),

    /** LQL-1011: A sort builder needs a lambda, not a method reference (S13). */
    METHOD_REFERENCE_IN_SORT_BUILDER("LQL-1011", 0),

    /** LQL-1012: A method reference must be a getter of the entity, for example Order::getPrice (S13). */
    METHOD_REFERENCE_NOT_GETTER("LQL-1012", 0),

    /** LQL-1013: The &#123;0&#125; of a subquery needs a lambda written in place, not a lambda stored in a variable. */
    INNER_LAMBDA_NOT_INLINE("LQL-1013", 1),

    /** LQL-2001: The decoder went past the end of the method. */
    END_OF_METHOD("LQL-2001", 0),

    /** LQL-2002: A local variable is read before it has a value: &#123;0&#125; */
    UNINITIALIZED_LOCAL("LQL-2002", 1),

    /** LQL-2003: An assignment lambda cannot use local variables (U1). Write the value in the assignment itself. */
    LOCAL_IN_ASSIGNMENT("LQL-2003", 0),

    /** LQL-2004: Loops are not supported in a lambda. Build the list outside the lambda and capture it. */
    LOOP_NOT_SUPPORTED("LQL-2004", 0),

    /** LQL-2005: The size of an array in a lambda must be a constant. */
    ARRAY_SIZE_NOT_CONSTANT("LQL-2005", 0),

    /** LQL-2006: A value can be stored in an array only at a constant index. */
    ARRAY_INDEX_NOT_CONSTANT("LQL-2006", 0),

    /** LQL-2007: String concatenation (+) is not supported yet. Build the string outside the lambda and capture it. */
    STRING_CONCAT_NOT_SUPPORTED("LQL-2007", 0),

    /** LQL-2008: This invokedynamic is not supported: &#123;0&#125; */
    INVOKEDYNAMIC_NOT_SUPPORTED("LQL-2008", 1),

    /** LQL-2009: An assignment lambda cannot have conditions (if, ?:) (U1). Put the condition in where. */
    CONDITION_IN_ASSIGNMENT("LQL-2009", 0),

    /** LQL-2010: switch (and Kotlin when) is not supported yet. */
    SWITCH_NOT_SUPPORTED("LQL-2010", 0),

    /** LQL-2011: A path that throws an exception is not supported in a lambda. */
    THROW_NOT_SUPPORTED("LQL-2011", 0),

    /** LQL-2012: The lambda does not return a value, so it is not a condition or value lambda. */
    NO_RETURN_VALUE("LQL-2012", 0),

    /** LQL-2013: This bytecode instruction is not supported: &#123;0&#125; */
    UNSUPPORTED_INSTRUCTION("LQL-2013", 1),

    /** LQL-2014: A compare result (-1, 0, 1) cannot be returned as it is. Compare it, for example a.compareTo(b) &gt; 0. */
    COMPARE_RESULT_RETURNED("LQL-2014", 0),

    /** LQL-2015: The constructor call does not belong to a NEW instruction. */
    CONSTRUCTOR_TARGET_NOT_NEW("LQL-2015", 0),

    /** LQL-2016: A call that returns nothing cannot be used in a condition lambda: &#123;0&#125; */
    VOID_CALL_IN_CONDITION("LQL-2016", 1),

    /** LQL-2017: This constant is not supported: &#123;0&#125; */
    UNSUPPORTED_CONSTANT("LQL-2017", 1),

    /** LQL-2018: The operand stack is empty. */
    STACK_EMPTY("LQL-2018", 0),

    /** LQL-2019: An object is used before its constructor runs. */
    UNINITIALIZED_OBJECT("LQL-2019", 0),

    /** LQL-2020: The condition lambda returns a value that is not a boolean. */
    NOT_BOOLEAN_CONDITION("LQL-2020", 0),

    /** LQL-2021: The condition could not be rebuilt as a logical expression. Forms that need the same condition more than once (such as a value ternary) are not supported. */
    CONDITION_NOT_RESTORED("LQL-2021", 0),

    /** LQL-2022: A select or groupBy lambda cannot have conditions (if, ?:) (J1). */
    CONDITION_IN_SELECT("LQL-2022", 0),

    /** LQL-2023: A sort lambda cannot have conditions (if, ?:). Chain by calls instead (S4). */
    CONDITION_IN_SORT("LQL-2023", 0),

    /** LQL-2024: A subquery value lambda cannot have conditions. */
    CONDITION_IN_SUBQUERY_VALUE("LQL-2024", 0),

    /** LQL-2025: An array element has no value. */
    EMPTY_ARRAY_ELEMENT("LQL-2025", 0),

    /** LQL-3001: null can only be compared with == and !=. */
    NULL_COMPARE_OPERATOR("LQL-3001", 0),

    /** LQL-3002: This lambda parameter is not an entity: &#123;0&#125; */
    PARAM_NOT_ENTITY("LQL-3002", 1),

    /** LQL-3003: An entity field cannot be read directly. Use the getter: &#123;0&#125; */
    FIELD_ACCESS("LQL-3003", 1),

    /** LQL-3004: This expression has no SQL form: &#123;0&#125; */
    EXPRESSION_NOT_SQL("LQL-3004", 1),

    /** LQL-3005: Aggregate functions cannot be used in where. Use having (J3): Agg.&#123;0&#125; */
    AGG_IN_WHERE("LQL-3005", 1),

    /** LQL-3006: This aggregate function is not supported: Agg.&#123;0&#125; */
    AGG_UNSUPPORTED("LQL-3006", 1),

    /** LQL-3007: The argument of an aggregate function must use an entity value: Agg.&#123;0&#125; */
    AGG_ARG_NO_ENTITY("LQL-3007", 1),

    /** LQL-3008: This method call on an entity value is not supported: &#123;0&#125; */
    METHOD_NOT_SUPPORTED("LQL-3008", 1),

    /** LQL-3009: The entity property was not found: &#123;0&#125; */
    PROPERTY_NOT_FOUND("LQL-3009", 1),

    /** LQL-3010: A collection property cannot be used in a path. Use a subquery: &#123;0&#125; */
    COLLECTION_PATH("LQL-3010", 1),

    /** LQL-3011: Entities cannot be compared with each other yet. Compare the ids: &#123;0&#125; */
    ENTITY_COMPARE("LQL-3011", 1),

    /** LQL-3012: A whole value object can only be compared for equality (equals, ==, !=) with a captured value object of the same type, another value of the same type, or null. Compare its fields: &#123;0&#125; */
    EMBEDDABLE_COMPARE("LQL-3012", 1),

    /** LQL-3013: A subquery must start with sub.from(...): &#123;0&#125; */
    SUBQUERY_START("LQL-3013", 1),

    /** LQL-3014: A subquery must be made with the sub parameter of this lambda. An inner lambda uses where((x, sub2) -&gt; ...). */
    SUBQUERY_WRONG_SUB("LQL-3014", 0),

    /** LQL-3015: The subquery target must be an entity: &#123;0&#125; */
    SUBQUERY_NOT_ENTITY("LQL-3015", 1),

    /** LQL-3016: This call is not supported in a subquery: &#123;0&#125; */
    SUBQUERY_UNSUPPORTED_CALL("LQL-3016", 1),

    /** LQL-3017: first() needs orderBy(...).limit(1) before it, so that one row is certain (Q3). */
    SUBQUERY_FIRST_NEEDS_LIMIT("LQL-3017", 0),

    /** LQL-3018: The subquery value must be chosen with select(x -&gt; ...). */
    SUBQUERY_NEEDS_SELECT("LQL-3018", 0),

    /** LQL-3019: This sort method is not supported: by.&#123;0&#125; */
    SORT_UNSUPPORTED_METHOD("LQL-3019", 1),

    /** LQL-3020: A sort lambda must be a chain of by calls, for example by.asc(...).desc(...) (S4): &#123;0&#125; */
    SORT_NOT_CHAIN("LQL-3020", 1),

    /** LQL-3021: An inner lambda cannot use the outer sub. Use where((x, sub2) -&gt; ...). */
    SUBQUERY_OUTER_SUB("LQL-3021", 0),

    /** LQL-3022: sub.from needs a class literal, for example Item.class. */
    SUBQUERY_FROM_CLASS_LITERAL("LQL-3022", 0),

    /** LQL-3023: A subquery limit must be a number constant. */
    SUBQUERY_LIMIT_CONSTANT("LQL-3023", 0),

    /** LQL-3024: A value computed in Java uses an entity: &#123;0&#125; */
    HOST_VALUE_HAS_ENTITY("LQL-3024", 1),

    /** LQL-3025: This expression cannot be computed in Java: &#123;0&#125; */
    HOST_NOT_EVALUABLE("LQL-3025", 1),

    /** LQL-3026: The field cannot be read: &#123;0&#125; */
    FIELD_UNREADABLE("LQL-3026", 1),

    /** LQL-3027: An exception was thrown while computing a value in Java: &#123;0&#125; */
    HOST_METHOD_FAILED("LQL-3027", 1),

    /** LQL-3028: The method cannot be called: &#123;0&#125; */
    HOST_METHOD_UNCALLABLE("LQL-3028", 1),

    /** LQL-3029: The object cannot be created: &#123;0&#125; */
    HOST_OBJECT_UNCREATABLE("LQL-3029", 1),

    /** LQL-3030: There is no sort key. Use by.asc(...) or by.desc(...). */
    SORT_NO_KEY("LQL-3030", 0),

    /** LQL-3031: A sort key must use an entity value. A constant or captured value cannot be sorted: &#123;0&#125; */
    SORT_KEY_NO_ENTITY("LQL-3031", 1),

    /** LQL-3032: An entity cannot be a sort key. Sort by a property such as the id: &#123;0&#125; */
    SORT_BY_ENTITY("LQL-3032", 1),

    /** LQL-3033: The sort direction and the NULL position cannot come from an entity value: &#123;0&#125; */
    SORT_DIRECTION_FROM_ENTITY("LQL-3033", 1),

    /** LQL-3034: A group key must use an entity value: &#123;0&#125; */
    GROUP_KEY_NO_ENTITY("LQL-3034", 1),

    /** LQL-3035: A value used with aggregates must be a groupBy key (J3): &#123;0&#125; */
    NOT_GROUP_KEY("LQL-3035", 1),

    /** LQL-3036: A value in having that is not an aggregate must be a groupBy key (J3): &#123;0&#125; */
    HAVING_NOT_GROUP_KEY("LQL-3036", 1),

    /** LQL-3037: An assignment lambda can only call setters or Dml.value(column, value) (U1): &#123;0&#125; */
    ASSIGN_ONLY_SETTERS("LQL-3037", 1),

    /** LQL-3038: The assignment lambda assigns nothing (U1). */
    ASSIGN_NONE("LQL-3038", 0),

    /** LQL-3039: The first argument of Dml.value must be a property of the target entity, written as a getter call: &#123;0&#125; */
    VALUE_TARGET("LQL-3039", 1),

    /** LQL-3041: The same column is assigned twice (U4): &#123;0&#125; */
    ASSIGN_TWICE("LQL-3041", 1),

    /** LQL-3042: A value reads a column that an earlier assignment wrote (U3). In SQL it is the value before the update, and databases differ: &#123;0&#125; */
    ASSIGN_READS_WRITTEN("LQL-3042", 1),

    /** LQL-3043: The assignment target must be the first lambda parameter (the target entity): &#123;0&#125; */
    ASSIGN_TARGET_NOT_FIRST("LQL-3043", 1),

    /** LQL-3044: null is assigned to a column that can never be NULL (U6): &#123;0&#125; */
    ASSIGN_NULL_NOT_NULL("LQL-3044", 1),

    /** LQL-3045: An insert value cannot read the new row: &#123;0&#125; */
    INSERT_READS_TARGET("LQL-3045", 1),

    /** LQL-3046: With distinct(), a sort key must be a selected value (J7): &#123;0&#125; */
    DISTINCT_SORT_NOT_SELECTED("LQL-3046", 1),

    /** LQL-3047: A subquery cannot use an association path of the outer entity. Compare the id (foreign key): &#123;0&#125; */
    SUBQUERY_OUTER_PATH("LQL-3047", 1),

    /** LQL-3048: The conflict key must be a column of the entity: &#123;0&#125; */
    CONFLICT_KEY_NOT_COLUMN("LQL-3048", 1),

    /** LQL-3049: A setter must be called on the entity, or on its value object or association (U1): &#123;0&#125; */
    SETTER_TARGET("LQL-3049", 1),

    /** LQL-3050: Only the &#64;Id (foreign key) of an associated entity can be changed (U6): &#123;0&#125; */
    ASSOCIATION_ONLY_ID("LQL-3050", 1),

    /** LQL-3051: A collection property cannot be assigned (U6): &#123;0&#125; */
    COLLECTION_ASSIGN("LQL-3051", 1),

    /** LQL-3052: A &#64;SqlFunction method must not compute a value in Java. Its body must only throw, for example throw new DbOnlyException(): &#123;0&#125; */
    FUNCTION_NOT_GUARD("LQL-3052", 1),

    /** LQL-3053: All overloads with the same name must map to the same DB function: &#123;0&#125; */
    FUNCTION_OVERLOAD_MISMATCH("LQL-3053", 1),

    /** LQL-3054: The DB function name &#123;0&#125; is a Hibernate built-in function name. Use a prefix or a schema-qualified name. */
    FUNCTION_BUILTIN_NAME("LQL-3054", 1),

    /** LQL-3055: The return type of the DB function &#123;0&#125; is not supported: &#123;1&#125;. Use Double, Integer, Long, BigDecimal, String, LocalDate, LocalDateTime or Boolean. */
    FUNCTION_RETURN_TYPE("LQL-3055", 2),

    /** LQL-3056: A DB function cannot have default arguments (Kotlin): &#123;0&#125; */
    FUNCTION_KOTLIN_DEFAULT("LQL-3056", 1),

    /** LQL-3057: A DB function cannot have a variable number of arguments (vararg): &#123;0&#125; */
    FUNCTION_VARARG("LQL-3057", 1),

    /** LQL-3058: The DB function &#123;0&#125; has its own SQL name, so it must be registered. Add its class to the setting lambdaql.functions.classes. */
    FUNCTION_NOT_REGISTERED("LQL-3058", 1),

    /** LQL-3059: A procedure lambda must be one call of a stub method, for example p -&gt; p.calc(id, rate). */
    PROC_LAMBDA_SHAPE("LQL-3059", 0),

    /** LQL-3060: The method is not a &#64;SqlProcedure: &#123;0&#125; */
    PROC_NOT_ANNOTATED("LQL-3060", 1),

    /** LQL-3061: A procedure argument must be a captured value, a constant or a calculation of them: &#123;0&#125; */
    PROC_ARG_NOT_VALUE("LQL-3061", 1),

    /** LQL-3062: A procedure stub cannot have default arguments (Kotlin): &#123;0&#125; */
    PROC_KOTLIN_DEFAULT("LQL-3062", 1),

    /** LQL-3063: A function call lambda must be one &#64;SqlFunction call, for example () -&gt; OrderFns.discount(price). */
    FUNCTION_CALL_SHAPE("LQL-3063", 0),

    /** LQL-3064: The row type of a derived table or CTE must be a Java record or a Kotlin data class (J5): &#123;0&#125; */
    DERIVED_ROW_TYPE("LQL-3064", 1),

    /** LQL-3065: Dml.value: the value type &#123;2&#125; does not fit the column &#123;0&#125; (&#123;1&#125;) (U20) */
    VALUE_TYPE("LQL-3065", 3),

    /** LQL-3066: The condition cannot use an index, and the SARGable mode is STRICT: &#123;0&#125; */
    NON_SARGABLE("LQL-3066", 1),

    /** LQL-3067: A row of a derived table or CTE has no id, so it cannot be compared with null. Compare one of its columns, for example x.customerId() == null: &#123;0&#125; */
    DERIVED_ROW_NULL("LQL-3067", 1),

    /** LQL-3068: The &#64;Version column cannot be assigned; Hibernate increases it in every update (U15): &#123;0&#125; */
    VERSION_ASSIGN("LQL-3068", 1),

    /** LQL-4001: limit and the one-row protection are not supported yet for an entity with a composite key: &#123;0&#125; */
    COMPOSITE_KEY_LIMIT("LQL-4001", 1),

    /** LQL-4002: A value must be assigned to the conflict key &#123;0&#125;. */
    CONFLICT_KEY_MISSING("LQL-4002", 1),

    /** LQL-4003: An association path with a composite key is not supported in DML yet. */
    COMPOSITE_KEY_DML_PATH("LQL-4003", 0),

    /** LQL-4004: The sort direction (Direction) is null: &#123;0&#125; */
    SORT_DIRECTION_NULL("LQL-4004", 1),

    /** LQL-4005: The nullsFirst value is null: &#123;0&#125; */
    NULLS_FIRST_NULL("LQL-4005", 1),

    /** LQL-4006: A null Boolean is used as a condition (Java would throw NullPointerException): &#123;0&#125; */
    NULL_BOOLEAN_CONDITION("LQL-4006", 1),

    /** LQL-4007: The collection for IN is null (Java would throw NullPointerException). */
    IN_COLLECTION_NULL("LQL-4007", 0),

    /** LQL-4008: The collection for IN has a null element. */
    IN_COLLECTION_NULL_ELEMENT("LQL-4008", 0),

    /** LQL-4009: A null value cannot be compared with &lt;, &lt;=, &gt; or &gt;= (Java would throw NullPointerException): &#123;0&#125; */
    NULL_ORDER_COMPARE("LQL-4009", 1),

    /** LQL-4010: This value cannot be written in HQL: &#123;0&#125; */
    VALUE_NOT_RENDERABLE("LQL-4010", 1),

    /** LQL-4011: equals is called on a null value (Java would throw NullPointerException): &#123;0&#125; */
    NULL_EQUALS("LQL-4011", 1),

    /** LQL-4012: A method is called on a null value (Java would throw NullPointerException): &#123;0&#125; */
    NULL_METHOD_CALL("LQL-4012", 1),

    /** LQL-4013: A null value is used in arithmetic: &#123;0&#125; */
    NULL_ARITHMETIC("LQL-4013", 1),

    /** LQL-4014: limit must be 0 or more: &#123;0&#125; */
    LIMIT_NEGATIVE("LQL-4014", 1),

    /** LQL-4015: offset must be 0 or more: &#123;0&#125; */
    OFFSET_NEGATIVE("LQL-4015", 1),

    /** LQL-4016: executeOne() cannot be used with limit or orderBy. */
    EXECUTE_ONE_WITH_LIMIT("LQL-4016", 0),

    /** LQL-4017: orderBy() in update or delete needs limit(). */
    ORDER_BY_NEEDS_LIMIT("LQL-4017", 0),

    /** LQL-4018: upsert of an entity with a composite key needs onConflict(key): &#123;0&#125; */
    UPSERT_COMPOSITE_KEY("LQL-4018", 1),

    /** LQL-4019: toSet/findSet: &#123;0&#125; rows became &#123;1&#125; elements in the set. The entity equals/hashCode treats different rows as equal, or the &#64;Id is not unique. Use toList(). */
    SET_SIZE_MISMATCH("LQL-4019", 2),

    /** LQL-4020: The result constructor was not found: &#123;0&#125; */
    RESULT_CONSTRUCTOR_NOT_FOUND("LQL-4020", 1),

    /** LQL-4021: A NULL value cannot go into a primitive constructor argument (J4). Change argument &#123;0&#125; to a wrapper type (Long, Double, ...): &#123;1&#125; */
    NULL_INTO_PRIMITIVE("LQL-4021", 2),

    /** LQL-4022: The result object could not be created: &#123;0&#125; */
    RESULT_OBJECT_FAILED("LQL-4022", 1),

    /** LQL-4023: Two or more rows match the condition, so nothing was changed (one-row protection): &#123;0&#125; */
    MORE_THAN_ONE_ROW_CHANGED("LQL-4023", 1),

    /** LQL-4024: UPDATE, INSERT and DELETE need a transaction. Run them inside &#64;Transactional or a transaction you started. */
    TRANSACTION_REQUIRED("LQL-4024", 0),

    /** LQL-4025: Two or more rows match one(). Narrow the condition or use first(): &#123;0&#125; */
    MORE_THAN_ONE_ROW("LQL-4025", 1),

    /** LQL-4026: The SortSpec is for &#123;0&#125;, not for &#123;1&#125;. */
    SORT_SPEC_TYPE("LQL-4026", 2),

    /** LQL-4027: The sort path is empty. */
    SORT_PATH_EMPTY("LQL-4027", 0),

    /** LQL-4028: The sort path has an unknown property: &#123;0&#125; (path &#123;1&#125;) */
    SORT_PATH_UNKNOWN("LQL-4028", 2),

    /** LQL-4029: A sort path must end with a value property: &#123;0&#125; */
    SORT_PATH_NOT_VALUE("LQL-4029", 1),

    /** LQL-4030: The middle of a sort path must be a to-one association or a value object: &#123;0&#125; (path &#123;1&#125;) */
    SORT_PATH_MIDDLE("LQL-4030", 2),

    /** LQL-4031: The direction is null. */
    DIRECTION_NULL("LQL-4031", 0),

    /** LQL-4032: &#123;0&#125; only works inside a lambdaQL lambda. It is a placeholder and is never run. */
    PLACEHOLDER_CALLED("LQL-4032", 1),

    /** LQL-4033: An ignoreCase sort is not supported yet: &#123;0&#125; */
    IGNORE_CASE_NOT_SUPPORTED("LQL-4033", 1),

    /** LQL-4034: &#123;0&#125; is a database function. It works only inside a lambdaQL lambda and is never run in Java. */
    DB_ONLY_CALLED("LQL-4034", 1),

    /** LQL-4035: The procedure was not found in the database: &#123;0&#125; */
    PROC_NOT_FOUND("LQL-4035", 1),

    /** LQL-4036: The stub does not match the database procedure &#123;0&#125;: &#123;1&#125; */
    PROC_SIGNATURE_MISMATCH("LQL-4036", 2),

    /** LQL-4037: exec cannot call a procedure with OUT parameters, because their values would be lost. Use call: &#123;0&#125; */
    PROC_EXEC_HAS_OUT("LQL-4037", 1),

    /** LQL-4038: The return type of &#123;0&#125; does not match the procedure results: &#123;1&#125; */
    PROC_RESULT_SHAPE("LQL-4038", 2),

    /** LQL-4039: The procedure returned NULL, but the result type cannot hold NULL. Use a nullable type (Double, Integer, Kotlin Double? ...): &#123;0&#125; */
    PROC_NULL_INTO_PRIMITIVE("LQL-4039", 1),

    /** LQL-4040: Sorting by this name is not allowed for &#123;0&#125;: &#123;1&#125;. Allow it with SortRules (allow or alias). */
    SORT_NOT_ALLOWED("LQL-4040", 2),

    /** LQL-4041: A sort rule must be a column of the entity, for example o -&gt; o.getPrice(): &#123;0&#125; */
    SORT_RULE_NOT_PATH("LQL-4041", 1),

    /** LQL-4042: lambdaql.sort: no entity has this JPA entity name (by default the simple class name, or &#64;Entity(name = ...)): &#123;0&#125; */
    SORT_UNKNOWN_ENTITY("LQL-4042", 1),

    /** LQL-4043: The stub arguments of &#123;0&#125; have the database parameter names in another order. Stub: &#123;1&#125;, database: &#123;2&#125; */
    PROC_ARG_ORDER("LQL-4043", 3),

    /** LQL-4044: The Oracle package has several procedures with this name (overloads); lambdaQL cannot tell which one to call: &#123;0&#125; */
    PROC_OVERLOADED("LQL-4044", 1),

    /** LQL-4045: A captured value is null in &#123;0&#125;. In SQL, = NULL and &lt;&gt; NULL match no row; use x == null in the lambda, or check the value before the query. */
    NULL_CAPTURED_COMPARE("LQL-4045", 1),

    /** LQL-4999: Internal error in lambdaQL. Please report it: &#123;0&#125; */
    INTERNAL_ERROR("LQL-4999", 1),

    /** LQL-5002: A subquery sort without limit does not change the result, so it was removed (Q4): &#123;0&#125; */
    SUBQUERY_SORT_REMOVED("LQL-5002", 1),

    /** LQL-5003: An outside sort is not in the sort rules of &#123;0&#125;: &#123;1&#125;. It runs now; with SortMode.STRICT it would be rejected. */
    SORT_NOT_ALLOWED_WARN("LQL-5003", 2),

    /** LQL-5004: The stub argument names are not in the class file, so the argument order of &#123;0&#125; is not checked. Compile with -parameters (Kotlin: -java-parameters) or use &#64;DbParam. */
    PROC_NAMES_UNAVAILABLE("LQL-5004", 1),

    /** LQL-5005: The parameters of &#123;0&#125; could not be read from the database metadata (&#123;1&#125;). The stub is not checked; IN and INOUT parameters are taken in stub order, then the OUT parameters. */
    PROC_METADATA_UNAVAILABLE("LQL-5005", 2),

    /** LQL-5006: The overloads of &#123;0&#125; could not be checked (&#123;1&#125;); the call goes on without the check. */
    PROC_OVERLOAD_CHECK_SKIPPED("LQL-5006", 2),

    /** LQL-5001: The function &#123;0&#125; is applied to the column &#123;1&#125;, so a normal index on the column cannot be used and the query may scan the whole table. Compare the column with a value instead, or (Oracle, PostgreSQL) create a function-based index on the same expression: &#123;2&#125; */
    SARGABLE_COLUMN_FUNCTION("LQL-5001", 3),

    /** LQL-5007: The function &#123;0&#125; is NOT DETERMINISTIC (MariaDB, MySQL) or VOLATILE (PostgreSQL), so it is computed for every row and an index on the compared column is not used. If its result depends only on its arguments, declare it DETERMINISTIC (MariaDB, MySQL) or STABLE or IMMUTABLE (PostgreSQL): &#123;1&#125; */
    SARGABLE_NONDETERMINISTIC("LQL-5007", 2),

    /** LQL-5008: The index or function metadata for &#123;0&#125; could not be read (&#123;1&#125;); the SARGable check goes on without it. */
    SARGABLE_CHECK_SKIPPED("LQL-5008", 2),

    /** LQL-5009: SQL meaning: in &#123;0&#125;, a row whose value is NULL does not match (Java would treat null differently). Add a null check such as x == null || ... to include it. */
    SQL_NULL_MEANING("LQL-5009", 1),

    /** LQL-5010: The column &#123;0&#125; is a &#64;Lob column; some databases (for example Oracle) cannot use a LOB in &#123;1&#125;. */
    LOB_IN_SORT_OR_KEY("LQL-5010", 2),

    /** LQL-5011: The enum column &#123;0&#125; is stored as text (&#64;Enumerated(STRING)), so the database sorts it by name, not by the declaration order of the enum. */
    ENUM_STRING_SORT("LQL-5011", 1),

    /** LQL-5012: Arithmetic on the column &#123;0&#125; keeps the condition from using an index. Move it to the value side with the same meaning: divide by a decimal (o.getQty() &gt; n / 2.0 for o.getQty() * 2 &gt; n; n / 2 would be integer division), and reverse the comparison for a negative factor: &#123;1&#125; */
    SARGABLE_COLUMN_ARITHMETIC("LQL-5012", 2),

    /** LQL-5013: The condition computes on the columns &#123;0&#125;, so it cannot use an index on them: &#123;1&#125; */
    SARGABLE_COLUMN_EXPRESSION("LQL-5013", 2),

    /** LQL-5014: Kotlin: the association &#123;0&#125; is read with ?. in one place and with !! (or without a mark) in another, so it is an inner join and rows without it are left out. Write ?. everywhere to keep those rows, or !! everywhere: &#123;1&#125; */
    SAFE_CALL_MIXED("LQL-5014", 2);

    private final String code;
    private final int argCount;

    LqlMessage(String code, int argCount) {
        this.code = code;
        this.argCount = argCount;
    }

    /**
     * Returns the stable code of this message.
     *
     * @return the code, for example {@code "LQL-2004"}
     */
    public String code() {
        return code;
    }

    /**
     * Returns how many arguments ({@code {0}}, {@code {1}} ...) the message text has.
     *
     * @return the number of arguments
     */
    public int argCount() {
        return argCount;
    }

    /**
     * Makes the message in the current lambdaQL language ({@link LqlMessages#locale()}).
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/i18n/LqlMessagesTest.java">LqlMessagesTest</a>):
     * {@snippet class="org.lambdaql.i18n.LqlMessagesTest" region="format"}
     *
     * @param args the values for {@code {0}}, {@code {1}} ...
     * @return the message, for example {@code "[LQL-4014] limit must be 0 or more: -1"}
     */
    public String format(Object... args) {
        return LqlMessages.format(this, LqlMessages.locale(), args);
    }

    /**
     * Makes the message in the language of {@code locale}.
     *
     * @param locale the language ({@code ja}, {@code zh}, {@code ko}, anything else is English)
     * @param args   the values for {@code {0}}, {@code {1}} ...
     * @return the message with its code
     */
    public String format(Locale locale, Object... args) {
        return LqlMessages.format(this, locale, args);
    }

    /**
     * Makes the exception for this message. The exception class follows the code range: 1xxx
     * {@link org.lambdaql.LambdaAnalysisException}, 2xxx {@link org.lambdaql.UnsupportedLambdaShapeException},
     * 3xxx {@link org.lambdaql.TranslationException}, others {@link LambdaQLException}.
     *
     * @param args the message arguments
     * @return the exception (the caller throws it)
     */
    public LambdaQLException exception(Object... args) {
        return LambdaQLException.of(this, null, null, null, args);
    }

    /**
     * Makes the exception for this message with the place of the lambda.
     *
     * @param where the lambda body method (and line), added at the end of the message; may be null
     * @param args  the message arguments
     * @return the exception (the caller throws it)
     */
    public LambdaQLException exceptionAt(String where, Object... args) {
        return LambdaQLException.of(this, where, null, null, args);
    }

    /**
     * Makes the exception for this message with the place of the lambda and the cause.
     *
     * @param where the lambda body method (and line), added at the end of the message; may be null
     * @param cause the original exception
     * @param args  the message arguments
     * @return the exception (the caller throws it)
     */
    public LambdaQLException exceptionCausedBy(String where, Throwable cause, Object... args) {
        return LambdaQLException.of(this, where, null, cause, args);
    }
}
