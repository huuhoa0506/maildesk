package nhh.reminder;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MailSendJobItemRepository extends JpaRepository<MailSendJobItemEntity, Long> {

    @Query(value = """
            select item.id from mail_send_job_items item
            join mail_send_jobs job on job.id = item.job_id
            where item.status = 'queued' and job.status in ('queued', 'running')
            order by item.id
            limit 1
            """, nativeQuery = true)
    Optional<Long> findNextQueuedItemId();

    @Modifying
    @Query("update MailSendJobItemEntity item set item.status = 'processing' where item.id = :id and item.status = 'queued'")
    int claim(@Param("id") long id);

    List<MailSendJobItemEntity> findAllByStatus(String status);

    long countByJobIdAndStatus(long jobId, String status);

    long countByJobIdAndStatusIn(long jobId, List<String> statuses);

    long countByJobId(long jobId);

    @Query(value = """
            select count(*) from mail_send_job_items
            where job_id = :jobId and status in ('succeeded', 'failed')
            """, nativeQuery = true)
    long countProcessed(@Param("jobId") long jobId);
}
