package com.sunasterisk.socialanalytics.repository;

import com.sunasterisk.socialanalytics.entity.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
}
