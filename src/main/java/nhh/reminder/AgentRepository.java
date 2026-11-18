package nhh.reminder;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentRepository extends JpaRepository<AgentEntity, Long> {

    List<AgentEntity> findAllByReminding_IdOrderById(long remindingId);

    @Query("""
            select agent.id as id, agent.status as status, agent.errorMessage as errorMessage
            from AgentEntity agent
            where agent.reminding.id = :remindingId
            order by agent.id
            """)
    List<AgentDeliveryStatus> findDeliveryStatusesByRemindingId(@Param("remindingId") long remindingId);

    List<AgentEntity> findAllByReminding_IdAndIdIn(long remindingId, List<Long> ids);

    java.util.Optional<AgentEntity> findByIdAndReminding_Id(long id, long remindingId);

    long countByReminding_Id(long remindingId);

    long countByReminding_IdAndStatus(long remindingId, String status);

    long countByReminding_IdAndStatusIn(long remindingId, List<String> statuses);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AgentEntity agent
            set agent.status = 'sending', agent.errorMessage = null
            where agent.id = :id and agent.status in ('pending', 'failed')
            """)
    int markSending(@Param("id") long id);

    interface AgentDeliveryStatus {
        long getId();

        String getStatus();

        String getErrorMessage();
    }
}
