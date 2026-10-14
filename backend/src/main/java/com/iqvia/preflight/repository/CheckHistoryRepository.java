package com.iqvia.preflight.repository;

import com.iqvia.preflight.entity.CheckHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CheckHistoryRepository extends JpaRepository<CheckHistory, Long> {
    List<CheckHistory> findTop20ByOrderByCheckedAtDesc();
}
