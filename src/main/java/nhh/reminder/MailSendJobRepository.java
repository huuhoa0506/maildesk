package nhh.reminder;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MailSendJobRepository extends JpaRepository<MailSendJobEntity, Long> {

    boolean existsByRemindingIdAndStatusIn(long remindingId, List<String> statuses);

    Optional<MailSendJobEntity> findFirstByRemindingIdOrderByIdDesc(long remindingId);
}
