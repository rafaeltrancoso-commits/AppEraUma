package com.rrsistemas.erauma.story;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.rrsistemas.erauma.family.FamilyService;
import com.rrsistemas.erauma.shared.BusinessException;
import com.rrsistemas.erauma.user.AppUser;
import org.junit.jupiter.api.Test;

class StoryAudioAuthorizationServiceTest {
    @Test
    void disabledConfigurationBlocksDirectRequestsBeforeMembershipChecks() {
        FamilyService families = mock(FamilyService.class);
        StoryAudioAuthorizationService service = new StoryAudioAuthorizationService(
                new StoryAudioProperties(false, 3, 4000), families);

        assertThatThrownBy(() -> service.requireStoryAccess(mock(Story.class), mock(AppUser.class)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getCode()).isEqualTo("AI_AUDIO_DISABLED"));
        verifyNoInteractions(families);
    }
}
