package com.stolink.backend.support;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class DatabaseCleaner implements InitializingBean {

    @PersistenceContext
    private EntityManager entityManager;

    private List<EntityType<?>> entities;

    @Override
    public void afterPropertiesSet() {
        entities = entityManager.getMetamodel().getEntities().stream()
                .filter(e -> e.getJavaType().getAnnotation(Entity.class) != null)
                .collect(Collectors.toList());
    }

    private String getTableName(EntityType<?> e) {
        Table tableAnnotation = e.getJavaType().getAnnotation(Table.class);
        if (tableAnnotation != null && !tableAnnotation.name().isEmpty()) {
            return tableAnnotation.name();
        }
        // 기본값은 엔티티 이름을 스네이크 케이스로 변환하거나 단순히 소문자화 (H2 기준)
        return e.getName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    private boolean isNumericId(EntityType<?> entity) {
        try {
            Class<?> idType = entity.getIdType().getJavaType();
            return Number.class.isAssignableFrom(idType) ||
                    long.class.equals(idType) ||
                    int.class.equals(idType) ||
                    short.class.equals(idType);
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional
    public void execute() {
        entityManager.flush();
        entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate();

        for (EntityType<?> entity : entities) {
            String tableName = getTableName(entity);
            entityManager.createNativeQuery("TRUNCATE TABLE \"" + tableName + "\"").executeUpdate();

            // ID 시퀀스 초기화 (숫자형 ID인 경우에만)
            if (isNumericId(entity)) {
                try {
                    entityManager.createNativeQuery("ALTER TABLE \"" + tableName + "\" ALTER COLUMN id RESTART WITH 1")
                            .executeUpdate();
                } catch (Exception e) {
                    // ID 컬럼이 없거나 이름이 다르거나 시퀀스가 아닐 경우 무시
                }
            }
        }

        entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY TRUE").executeUpdate();
    }
}
