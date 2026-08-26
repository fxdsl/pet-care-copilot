package com.petassistant.business.data.mapper;

import java.util.List;

import com.petassistant.business.data.entity.PetProfileEntity;
import org.apache.ibatis.annotations.Param;

/**
 * 宠物档案 MyBatis Mapper；具体 SQL 统一放在 XML 中。
 */
public interface PetProfileMapper {

    /** 写入一份宠物档案。 */
    int insert(PetProfileEntity profile);

    /** 按编号查询档案，不存在时返回 null。 */
    PetProfileEntity findByIdAndUser(@Param("id") String id, @Param("userId") String userId);

    /** 查询最近创建的档案。 */
    List<PetProfileEntity> findRecentByUser(@Param("userId") String userId, @Param("limit") int limit);

    /** 仅允许所有者更新档案。 */
    int update(PetProfileEntity profile);

    /** 仅允许所有者删除档案。 */
    int deleteByIdAndUser(@Param("id") String id, @Param("userId") String userId);
}
