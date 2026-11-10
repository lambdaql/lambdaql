package org.lambdaql.analyzer;

/**
 * 람다 바이트코드 분석 실패.
 * 원인 예외를 cause 로 보존해 실제 실패 지점(visitor 내부 예외 등)이 가려지지 않게 한다.
 */
public class LambdaAnalysisException extends RuntimeException {

    public LambdaAnalysisException(String message) {
        super(message);
    }

    public LambdaAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
