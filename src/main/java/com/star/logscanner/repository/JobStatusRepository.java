package com.star.logscanner.repository;

import com.star.logscanner.entity.JobStatus;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobStatusRepository extends CrudRepository<JobStatus, String> {
    
    Optional<JobStatus> findByJobId(String jobId);
    
    List<JobStatus> findByStatus(String status);
    
    void deleteByJobId(String jobId);
}
