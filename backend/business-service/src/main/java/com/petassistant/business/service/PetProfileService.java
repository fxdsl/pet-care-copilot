package com.petassistant.business.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.petassistant.business.data.dto.request.CreatePetProfileRequest;
import com.petassistant.business.data.dto.request.UpdatePetProfileRequest;
import com.petassistant.business.data.dto.response.PetProfileResponse;
import com.petassistant.business.data.entity.PetProfileEntity;
import com.petassistant.business.data.mapper.PetProfileMapper;
import com.petassistant.business.exception.PetProfileNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 宠物档案业务服务，统一处理字段标准化和实体转换。
 */
@Service
public class PetProfileService {

    private final PetProfileMapper mapper;

    /** 注入宠物档案 Mapper。 */
    public PetProfileService(PetProfileMapper mapper) {
        this.mapper = mapper;
    }

    /** 创建一份可供问答选择的宠物档案。 */
    @Transactional
    public PetProfileResponse create(String userId, CreatePetProfileRequest request) {
        Instant now = Instant.now();
        PetProfileEntity entity = new PetProfileEntity(
                UUID.randomUUID().toString(), userId, request.name().trim(), request.petType().trim().toUpperCase(),
                blankToNull(request.breed()), request.ageMonths(), request.weightKg(),
                blankToNull(request.notes()), now, now
        );
        mapper.insert(entity);
        return toResponse(entity);
    }

    /** 查询最近档案，限制一次最多返回 100 条。 */
    @Transactional(readOnly = true)
    public List<PetProfileResponse> list(String userId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        return mapper.findRecentByUser(userId, safeLimit).stream()
                .map(PetProfileService::toResponse).toList();
    }

    /** 返回指定档案实体，供问答服务构造过滤条件与模型上下文。 */
    @Transactional(readOnly = true)
    public PetProfileEntity requireEntity(String userId, String id) {
        PetProfileEntity entity = mapper.findByIdAndUser(id, userId);
        if (entity == null) {
            throw new PetProfileNotFoundException(id);
        }
        return entity;
    }

    /** 使用 ID + user_id 条件修改，不能通过伪造档案 ID 越权。 */
    @Transactional
    public PetProfileResponse update(String userId, String id, UpdatePetProfileRequest request) {
        PetProfileEntity existing = requireEntity(userId, id);
        PetProfileEntity updated = new PetProfileEntity(
                existing.id(), userId, request.name().trim(), request.petType().trim().toUpperCase(),
                blankToNull(request.breed()), request.ageMonths(), request.weightKg(),
                blankToNull(request.notes()), existing.createdAt(), Instant.now()
        );
        mapper.update(updated);
        return toResponse(updated);
    }

    /** 删除操作同样带所有者条件；不存在和越权统一表现为 404。 */
    @Transactional
    public void delete(String userId, String id) {
        requireEntity(userId, id);
        mapper.deleteByIdAndUser(id, userId);
    }

    /** 将数据库实体转换为稳定的接口响应。 */
    private static PetProfileResponse toResponse(PetProfileEntity entity) {
        return new PetProfileResponse(
                entity.id(), entity.userId(), entity.name(), entity.petType(), entity.breed(), entity.ageMonths(),
                entity.weightKg(), entity.notes(), entity.createdAt(), entity.updatedAt()
        );
    }

    /** 把空白可选字段保存为 SQL NULL。 */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
