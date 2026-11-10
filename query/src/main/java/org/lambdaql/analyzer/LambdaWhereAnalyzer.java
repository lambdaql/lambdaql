package org.lambdaql.analyzer;

import org.lambdaql.query.SelectQuery.Where;
import jakarta.persistence.EntityManager;
import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.List;

public class LambdaWhereAnalyzer {

    private final EntityManager entityManager;
    private final List<Class<?>> entityClass;

    public LambdaWhereAnalyzer(EntityManager entityManager, List<Class<?>> entityClass) {
        this.entityManager = entityManager;
        this.entityClass = entityClass;
    }

    public <T> ConditionExpression analyze(Where<T> whereClause) {
        Class<?> lambdaClass = whereClause.getClass();
        Method method;
        Object serialized;
        try {
            method = lambdaClass.getDeclaredMethod("writeReplace");
            method.setAccessible(true);
            serialized = method.invoke(whereClause);
        } catch (NoSuchMethodException e) {
            // 익명 클래스 등 Serializable 람다가 아닌 구현체
            throw new LambdaAnalysisException(
                    "Serializable 람다만 분석할 수 있습니다 (writeReplace 없음): " + lambdaClass.getName(), e);
        } catch (ReflectiveOperationException e) {
            throw new LambdaAnalysisException("SerializedLambda 획득 실패: " + lambdaClass.getName(), e);
        }

        if (!(serialized instanceof SerializedLambda sl)) {
            throw new LambdaAnalysisException(
                    "writeReplace 결과가 SerializedLambda가 아닙니다: " + lambdaClass.getName()
                            + " -> " + (serialized == null ? "null" : serialized.getClass().getName()));
        }

        String implTarget = sl.getImplClass() + "." + sl.getImplMethodName() + sl.getImplMethodSignature();
        try {
            ClassReader reader = new ClassReader(sl.getImplClass().replace('/', '.'));
            LambdaMethodVisitor visitor = new LambdaMethodVisitor(method, sl, entityClass, entityManager.getMetamodel());
            reader.accept(visitor, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
            return visitor.getConditionExpr();
        } catch (IOException e) {
            throw new LambdaAnalysisException("람다 구현 클래스 바이트코드를 읽을 수 없습니다: " + implTarget, e);
        } catch (RuntimeException e) {
            // 이전에는 fallback ClassReader 가 IOException("Class not found")을 던져 이 원인을 가렸음
            throw new LambdaAnalysisException("람다 바이트코드 분석 실패: " + implTarget, e);
        }
    }


}
