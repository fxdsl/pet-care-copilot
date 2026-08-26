package com.petassistant.business.data.mapper;

import java.util.List;

import com.petassistant.business.data.dto.response.AdminAuditResponse;
import com.petassistant.business.data.entity.AdminAuditEntity;
import org.apache.ibatis.annotations.Param;

/** 管理操作审计 MyBatis Mapper。 */
public interface AdminAuditMapper {

    int insert(AdminAuditEntity audit);

    List<AdminAuditResponse> findRecent(@Param("offset") int offset, @Param("limit") int limit);

    long countAll();
}
