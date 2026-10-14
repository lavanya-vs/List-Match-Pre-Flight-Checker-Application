package com.iqvia.preflight.repository;

import com.iqvia.preflight.entity.CheckJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckJobRepository extends JpaRepository<CheckJob, String> {
}
