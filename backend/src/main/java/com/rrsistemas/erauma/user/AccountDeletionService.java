package com.rrsistemas.erauma.user;

import com.rrsistemas.erauma.shared.BusinessException;
import com.rrsistemas.erauma.storage.FileDeletionQueueService;
import com.rrsistemas.erauma.storage.FileDeletionType;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AccountDeletionService {
    private final AppUserRepository users;
    private final AccountDeletionRepository deletionRepository;
    private final FileDeletionQueueService fileQueue;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactions;

    public AccountDeletionService(AppUserRepository users, AccountDeletionRepository deletionRepository,
            FileDeletionQueueService fileQueue, PasswordEncoder passwordEncoder,
            PlatformTransactionManager transactionManager) {
        this.users = users;
        this.deletionRepository = deletionRepository;
        this.fileQueue = fileQueue;
        this.passwordEncoder = passwordEncoder;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public void delete(UUID authenticatedUserId, String currentPassword) {
        prepare(authenticatedUserId, currentPassword);
        CompletionResult result = completePendingDeletion(authenticatedUserId);
        if (result == CompletionResult.BLOCKED_SHARED_FAMILY) {
            throw sharedFamilyConflict();
        }
    }

    public CompletionResult completePendingDeletion(UUID userId) {
        return transactions.execute(status -> users.findDeletionRequestedForUpdate(userId)
                .map(user -> completeLocked(userId))
                .orElse(CompletionResult.NOT_FOUND));
    }

    private void prepare(UUID userId, String currentPassword) {
        transactions.executeWithoutResult(status -> {
            AppUser user = users.findForUpdate(userId)
                    .orElseThrow(() -> new BusinessException("AUTHENTICATION_INVALID", "Autenticação inválida", HttpStatus.UNAUTHORIZED));
            if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
                throw new BusinessException("CURRENT_PASSWORD_INVALID", "Senha atual incorreta.", HttpStatus.BAD_REQUEST);
            }
            if (deletionRepository.hasSharedFamily(userId)) {
                throw sharedFamilyConflict();
            }
            user.requestDeletion();
        });
    }

    private CompletionResult completeLocked(UUID userId) {
        deletionRepository.lockFamilyScope(userId);
        if (deletionRepository.hasSharedFamily(userId)) {
            return CompletionResult.BLOCKED_SHARED_FAMILY;
        }
        deletionRepository.momentPhotoStorageKeys(userId)
                .forEach(key -> fileQueue.enqueue(FileDeletionType.MOMENT_PHOTO, key));
        deletionRepository.storyIds(userId)
                .forEach(id -> fileQueue.enqueue(FileDeletionType.STORY_DIRECTORY, id.toString()));
        deletionRepository.deleteAccountGraph(userId);
        return CompletionResult.COMPLETED;
    }

    private BusinessException sharedFamilyConflict() {
        return new BusinessException("ACCOUNT_DELETION_SHARED_FAMILY",
                "Não é possível excluir a conta enquanto houver outro membro ativo na família.",
                HttpStatus.CONFLICT);
    }

    public enum CompletionResult {
        COMPLETED,
        NOT_FOUND,
        BLOCKED_SHARED_FAMILY
    }
}
