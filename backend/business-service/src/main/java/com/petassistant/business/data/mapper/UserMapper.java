package com.petassistant.business.data.mapper;

import java.time.Instant;
import java.util.List;

import com.petassistant.business.data.entity.UserEntity;
import org.apache.ibatis.annotations.Param;

/** 用户 MyBatis Mapper；所有 SQL 统一维护在 UserMapper.xml。 */
public interface UserMapper {

    UserEntity findById(@Param("id") String id);

    UserEntity findByUsername(@Param("username") String username);

    int insert(UserEntity user);

    int updateLastLogin(@Param("id") String id, @Param("lastLoginAt") Instant lastLoginAt);

    int updateProfile(
            @Param("id") String id,
            @Param("displayName") String displayName,
            @Param("avatarUrl") String avatarUrl,
            @Param("bio") String bio,
            @Param("region") String region,
            @Param("updatedAt") Instant updatedAt
    );

    /** 管理端按条件分页读取用户；所有条件都使用绑定参数，禁止拼接 SQL。 */
    List<UserEntity> findPage(
            @Param("keyword") String keyword,
            @Param("role") String role,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countPage(
            @Param("keyword") String keyword,
            @Param("role") String role,
            @Param("status") String status
    );

    long countActiveAdmins();

    /** 角色或状态变化时同步递增安全版本，使旧 JWT 立即失效。 */
    int updateRole(
            @Param("id") String id,
            @Param("role") String role,
            @Param("updatedAt") Instant updatedAt
    );

    int updateStatus(
            @Param("id") String id,
            @Param("status") String status,
            @Param("updatedAt") Instant updatedAt
    );
}
