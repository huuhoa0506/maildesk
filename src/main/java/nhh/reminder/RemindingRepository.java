package nhh.reminder;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RemindingRepository extends JpaRepository<RemindingEntity, Long> {

    List<RemindingEntity> findAllBySchool_IdOrderByCreatedAtDesc(long schoolId);

    Optional<RemindingEntity> findByIdAndSchool_Id(long id, long schoolId);
}
