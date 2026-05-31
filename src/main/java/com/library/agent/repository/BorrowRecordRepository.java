package com.library.agent.repository;

import com.library.agent.entity.BorrowRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BorrowRecordRepository extends JpaRepository<BorrowRecord, Long> {
    Optional<BorrowRecord> findTopByBookIdAndReturnDateIsNullOrderByBorrowDateDesc(Long bookId);
}
