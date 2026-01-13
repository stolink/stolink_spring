package com.stolink.backend.global.common.exception;

/**
 * 중복 관계 생성 시도 시 발생하는 예외
 */
public class DuplicateRelationshipException extends RuntimeException {

    public DuplicateRelationshipException(String sourceId, String targetId) {
        super(String.format("Relationship already exists between %s and %s", sourceId, targetId));
    }

    public DuplicateRelationshipException(String message) {
        super(message);
    }
}
