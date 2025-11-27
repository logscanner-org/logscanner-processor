package com.star.logscanner.repository;

import com.star.logscanner.entity.LogEntry;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LogEntryRepository extends ElasticsearchRepository<LogEntry, String> {
    long countByJobIdAndLevel(String jobId, String level);
    long countByJobId(String jobId);
    long countByJobIdAndHasErrorTrue(String jobId);
}
