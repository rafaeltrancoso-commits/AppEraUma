package com.rrsistemas.erauma.user;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AccountDeletionRecoveryWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccountDeletionRecoveryWorker.class);
    private final AppUserRepository users;
    private final AccountDeletionService accountDeletionService;

    public AccountDeletionRecoveryWorker(AppUserRepository users, AccountDeletionService accountDeletionService) {
        this.users = users;
        this.accountDeletionService = accountDeletionService;
    }

    @Scheduled(
            fixedDelayString = "${app.account-deletion.recovery-delay-ms:300000}",
            initialDelayString = "${app.account-deletion.recovery-initial-delay-ms:60000}")
    public void recoverPendingDeletions() {
        users.findDeletionRequestedIds().forEach(this::recoverOne);
    }

    private void recoverOne(UUID userId) {
        LOGGER.info("account_deletion_recovery_started userId={}", userId);
        try {
            AccountDeletionService.CompletionResult result = accountDeletionService.completePendingDeletion(userId);
            switch (result) {
                case COMPLETED -> LOGGER.info("account_deletion_recovery_completed userId={}", userId);
                case NOT_FOUND -> LOGGER.info("account_deletion_recovery_already_completed userId={}", userId);
                case BLOCKED_SHARED_FAMILY -> LOGGER.warn(
                        "account_deletion_recovery_blocked userId={} reason=shared_family", userId);
            }
        } catch (RuntimeException exception) {
            LOGGER.error("account_deletion_recovery_failed userId={} reason={}", userId,
                    exception.getClass().getSimpleName());
        }
    }
}
