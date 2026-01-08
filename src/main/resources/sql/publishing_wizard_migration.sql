-- 커뮤니티 배포 마법사 기능을 위한 스키마 확장
-- 실행 시점: 배포 전 수동 실행 또는 Flyway/Liquibase 마이그레이션

-- 1. documents 테이블에 게시 상태 필드 추가
ALTER TABLE documents 
ADD COLUMN IF NOT EXISTS is_published BOOLEAN NOT NULL DEFAULT FALSE;

-- 필터링 성능을 위한 인덱스
CREATE INDEX IF NOT EXISTS idx_documents_is_published ON documents(is_published);

-- 2. drafts 테이블에 다중 Document 지원 필드 추가
ALTER TABLE drafts
ADD COLUMN IF NOT EXISTS document_ids JSONB,
ADD COLUMN IF NOT EXISTS is_merged BOOLEAN DEFAULT FALSE;

-- 기존 document_id 컬럼은 하위 호환성을 위해 유지
-- 신규 Bulk API는 document_ids 사용, 기존 API는 document_id 사용

COMMENT ON COLUMN documents.is_published IS '커뮤니티(Storead) 게시 완료 여부';
COMMENT ON COLUMN drafts.document_ids IS '다중 Document ID 배열 (Bulk 배포용, JSONB 형식)';
COMMENT ON COLUMN drafts.is_merged IS '병합 배포 여부 (true: 여러 섹션을 하나의 에피소드로 병합)';
