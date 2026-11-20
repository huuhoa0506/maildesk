package nhh.reminder;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Component
public class MailQueueWorker {

    private static final Logger logger = LoggerFactory.getLogger(MailQueueWorker.class);

    private final MailSendQueueService queue;
    private final WorkspaceService workspace;
    private final MailDeliveryService mailDeliveryService;
    private final ThreadPoolTaskExecutor executor;
    private final int workerCount;
    private final AtomicBoolean ready = new AtomicBoolean();

    public MailQueueWorker(
            MailSendQueueService queue,
            WorkspaceService workspace,
            MailDeliveryService mailDeliveryService,
            @Qualifier("mailQueueExecutor") ThreadPoolTaskExecutor executor,
            @Value("${app.mail.worker-count:3}") int workerCount) {
        this.queue = queue;
        this.workspace = workspace;
        this.mailDeliveryService = mailDeliveryService;
        this.executor = executor;
        this.workerCount = workerCount;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAndStart() {
        queue.recoverInterruptedItems();
        ready.set(true);
    }

    @Scheduled(fixedDelayString = "${app.mail.queue.poll-interval-ms:200}")
    public void dispatchQueuedItems() {
        if (!ready.get()) {
            return;
        }
        ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
        while (pool.getActiveCount() + pool.getQueue().size() < workerCount) {
            var claimed = queue.claimNext();
            if (claimed.isEmpty()) {
                return;
            }
            MailSendQueueService.ClaimedItem item = claimed.get();
            try {
                executor.execute(() -> process(item));
            } catch (TaskRejectedException exception) {
                queue.releaseClaim(item.itemId());
                logger.warn("Mail worker rejected queued item {}; item returned to queue.", item.itemId(), exception);
                return;
            }
        }
    }

    private void process(MailSendQueueService.ClaimedItem item) {
        boolean succeeded;
        String failure = null;
        try {
            WorkspaceService.Reminding reminding = workspace.reminding(item.remindingId())
                    .orElseThrow(() -> new IllegalStateException("Không tìm thấy Reminding."));
            WorkspaceService.Agent agent = workspace.agent(item.agentId(), item.remindingId())
                    .orElseThrow(() -> new IllegalStateException("Không tìm thấy Agent."));
            WorkspaceService.School school = workspace.school(reminding.schoolId())
                    .orElseThrow(() -> new IllegalStateException("Không tìm thấy trường."));
            JavaMailSender sender = mailDeliveryService.isLogMode() ? null : mailDeliveryService.prepare(school);
            mailDeliveryService.send(sender, school, reminding, agent, workspace.rowValues(agent));
            succeeded = true;
        } catch (Exception exception) {
            failure = exception.getMessage() == null || exception.getMessage().isBlank()
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            succeeded = false;
        }
        try {
            queue.finish(item.itemId(), succeeded, failure);
        } catch (RuntimeException updateException) {
            logger.error("Could not save result for queued email item {}; it will be marked uncertain on restart.",
                    item.itemId(), updateException);
            return;
        }
        if (!succeeded) {
            logger.warn("Queued email item {} failed: {}", item.itemId(), failure);
        }
    }
}
