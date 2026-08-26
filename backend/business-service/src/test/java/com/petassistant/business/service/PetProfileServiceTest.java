package com.petassistant.business.service;

import java.math.BigDecimal;
import java.time.Instant;

import com.petassistant.business.data.dto.request.UpdatePetProfileRequest;
import com.petassistant.business.data.entity.PetProfileEntity;
import com.petassistant.business.data.mapper.PetProfileMapper;
import com.petassistant.business.exception.PetProfileNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 宠物档案所有权测试。 */
@ExtendWith(MockitoExtension.class)
class PetProfileServiceTest {

    @Mock
    private PetProfileMapper mapper;

    @Test
    void shouldUpdateOnlyProfileOwnedByCurrentUser() {
        PetProfileService service = new PetProfileService(mapper);
        Instant now = Instant.now();
        when(mapper.findByIdAndUser("profile-1", "user-1")).thenReturn(new PetProfileEntity(
                "profile-1", "user-1", "团子", "CAT", null, 4,
                new BigDecimal("1.80"), null, now, now
        ));

        service.update("user-1", "profile-1", new UpdatePetProfileRequest(
                "团子", "CAT", "中华田园猫", 5, new BigDecimal("2.10"), "已绝育"
        ));

        verify(mapper).update(org.mockito.ArgumentMatchers.argThat(
                profile -> "user-1".equals(profile.userId()) && profile.ageMonths() == 5
        ));
    }

    @Test
    void shouldHideProfileOwnedByAnotherUser() {
        PetProfileService service = new PetProfileService(mapper);
        when(mapper.findByIdAndUser("profile-1", "attacker")).thenReturn(null);

        assertThatThrownBy(() -> service.delete("attacker", "profile-1"))
                .isInstanceOf(PetProfileNotFoundException.class);
        verify(mapper, never()).deleteByIdAndUser("profile-1", "attacker");
    }
}
