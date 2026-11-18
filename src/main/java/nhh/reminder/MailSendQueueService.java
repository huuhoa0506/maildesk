package nhh.reminder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MailSendQueueService {

    private static final List<String> ACTIVE_STATUSES = List.of("queued", "running");
    private static final String INTERRUPTED_ERROR =
            "Ứng dụng đã dừng khi email đang được xử lý; hãy xác minh hộp thư trước khi gửi lại.";

    private final MailSendJobRepository jobRepository;
    private final MailSendJobItemRepository itemRepository;
    private final RemindingRepository remindingRepository;
    private final AgentRepository agentRepository;

    public MailSendQueueService(
            MailSendJobRepository jobRepository,
            MailSendJobItemRepository itemRepository,
            RemindingRepository remindingRepository,
            AgentRepository agentRepository) {
        this.jobRepository = jobRepository;
        this.itemRepository = itemRepository;
        this.remindingRepository = remindingRepository;
        this.agentRepository = agentRepository;
    }

    @Transactional
    public synchronized JobProgress enqueue(long remindingId, List<Long> agentIds) {
        if (!remindingRepository.existsById(remindingId)) {
            throw new IllegalArgumentException("Không tìm thấy Reminding.");
        }
        if (jobRepository.existsByRemindingIdAndStatusIn(remindingId, ACTIVE_STATUSES)) {
            throw new IllegalStateException("Reminding này đang có một lượt gửi chạy. Hãy đợi hoàn tất.");
        }

        Set<Long> requestedIds = Set.copyOf(agentIds);
        List<Long> eligibleIds = agentRepository.findAllByReminding_IdAndIdIn(remindingId, List.copyOf(requestedIds))
                .stream()
                .filter(agent -> "pending".equals(agent.getStatus()) || "failed".equals(agent.getStatus()))
                .map(AgentEntity::getId)
                .toList();
        if (eligibleIds.isEmpty()) {
            throw new IllegalArgumentException("Không có Agent đang chờ hoặc gửi lỗi nào được chọn.");
        }

        MailSendJobEntity job = jobRepository.saveAndFlush(new MailSendJobEntity(remindingId, eligibleIds.size()));
        itemRepository.saveAll(eligibleIds.stream()
                .map(agentId -> new MailSendJobItemEntity(job.getId(), agentId))
                .toList());
        return progress(job);
    }

    @Transactional
    public Optional<ClaimedItem> claimNext() {
        Optional<Long> nextId = itemRepository.findNextQueuedItemId();
        if (nextId.isEmpty() || itemRepository.claim(nextId.get()) != 1) {
            return Optional.empty();
        }
        long itemId = nextId.get();
        MailSendJobItemEntity item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy email trong hàng đợi."));
        long agentId = item.getAgentId();
        long jobId = item.getJobId();
        boolean agentClaimed = agentRepository.markSending(agentId) == 1;
        item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy email trong hàng đợi."));
        MailSendJobEntity job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy tác vụ gửi email."));
        if (!agentClaimed) {
            item.complete("failed", "Email không còn ở trạng thái có thể gửi.");
            completeJobIfDone(job);
            return Optional.empty();
        }
        job.start();
        return Optional.of(new ClaimedItem(itemId, jobId, job.getRemindingId(), agentId));
    }

    @Transactional
    public synchronized void finish(long itemId, boolean succeeded, String error) {
        MailSendJobItemEntity item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy email trong tác vụ gửi."));
        if (!"processing".equals(item.getStatus())) {
            return;
        }
        AgentEntity agent = agentRepository.findById(item.getAgentId())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy Agent cần cập nhật trạng thái."));
        String itemStatus = succeeded ? "succeeded" : "failed";
        String safeError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
        item.complete(itemStatus, safeError);
        if (succeeded) {
            agent.markSent();
        } else {
            agent.markFailed(safeError);
        }
        MailSendJobEntity job = jobRepository.findById(item.getJobId())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy tác vụ gửi email."));
        completeJobIfDone(job);
    }

    @Transactional(readOnly = true)
    public Optional<JobProgress> latestProgress(long remindingId) {
        return jobRepository.findFirstByRemindingIdOrderByIdDesc(remindingId).map(this::progress);
    }

    @Transactional(readOnly = true)
    public Optional<JobProgress> progress(long jobId) {
        return jobRepository.findById(jobId).map(this::progress);
    }

    @Transactional
    public void recoverInterruptedItems() {
        for (MailSendJobItemEntity item : itemRepository.findAllByStatus("processing")) {
            item.complete("failed", INTERRUPTED_ERROR);
            agentRepository.findById(item.getAgentId()).ifPresent(agent -> agent.markFailed(INTERRUPTED_ERROR));
            jobRepository.findById(item.getJobId()).ifPresent(this::completeJobIfDone);
        }
    }

    @Transactional
    public synchronized void releaseClaim(long itemId) {
        MailSendJobItemEntity item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy email cần trả lại hàng đợi."));
        if ("processing".equals(item.getStatus())) {
            item.complete("queued", null);
            agentRepository.findById(item.getAgentId()).ifPresent(AgentEntity::markPending);
        }
    }

    private void completeJobIfDone(MailSendJobEntity job) {
        if (itemRepository.countByJobIdAndStatusIn(job.getId(), List.of("queued", "processing")) == 0) {
            job.complete();
        }
    }

    private JobProgress progress(MailSendJobEntity job) {
        long processed = itemRepository.countProcessed(job.getId());
        return new JobProgress(job.getId(), job.getRemindingId(), job.getStatus(), job.getTotalCount(),
                Math.toIntExact(processed),
                Math.toIntExact(itemRepository.countByJobIdAndStatus(job.getId(), "succeeded")),
                Math.toIntExact(itemRepository.countByJobIdAndStatus(job.getId(), "failed")));
    }

    public record ClaimedItem(long itemId, long jobId, long remindingId, long agentId) {
    }

    public record JobProgress(
            long id, long remindingId, String status, int total, int processed, int succeeded, int failed) {
        public boolean active() {
            return "queued".equals(status) || "running".equals(status);
        }

        public int percentage() {
            return total == 0 ? 100 : processed * 100 / total;
        }
    }
}
