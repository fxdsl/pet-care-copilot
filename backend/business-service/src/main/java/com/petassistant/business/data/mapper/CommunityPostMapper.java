package com.petassistant.business.data.mapper;

import java.time.Instant;
import java.util.List;

import com.petassistant.business.data.dto.internal.CommunityPostView;
import com.petassistant.business.data.dto.response.CommunityTopicResponse;
import com.petassistant.business.data.entity.CommunityPostEntity;
import org.apache.ibatis.annotations.Param;

/** 社区帖子 MyBatis Mapper，动态筛选 SQL 统一保存在 XML。 */
public interface CommunityPostMapper {

    int insert(CommunityPostEntity post);

    CommunityPostEntity findOwnedEntity(@Param("id") String id, @Param("authorId") String authorId);

    CommunityPostView findOwnedView(@Param("id") String id, @Param("authorId") String authorId);

    CommunityPostView findPublicView(@Param("id") String id);

    List<CommunityPostView> findPublicPage(
            @Param("topicId") String topicId,
            @Param("authorId") String authorId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countPublic(@Param("topicId") String topicId, @Param("authorId") String authorId);

    List<CommunityPostView> findMinePage(
            @Param("authorId") String authorId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countMine(@Param("authorId") String authorId);

    /** 个人主页“赞过”和“收藏”分页直接回源 MySQL，Redis Set 只负责快速关系判断。 */
    List<CommunityPostView> findLikedByUser(
            @Param("userId") String userId, @Param("offset") int offset, @Param("limit") int limit
    );

    long countLikedByUser(@Param("userId") String userId);

    List<CommunityPostView> findFavoritedByUser(
            @Param("userId") String userId, @Param("offset") int offset, @Param("limit") int limit
    );

    long countFavoritedByUser(@Param("userId") String userId);

    int updateOwned(
            @Param("id") String id,
            @Param("authorId") String authorId,
            @Param("title") String title,
            @Param("content") String content,
            @Param("petProfileId") String petProfileId,
            @Param("topicId") String topicId,
            @Param("region") String region,
            @Param("latitude") Double latitude,
            @Param("longitude") Double longitude,
            @Param("expectedVersion") int expectedVersion,
            @Param("updatedAt") Instant updatedAt
    );

    int publishOwned(
            @Param("id") String id,
            @Param("authorId") String authorId,
            @Param("publishedAt") Instant publishedAt
    );

    int softDeleteOwned(
            @Param("id") String id,
            @Param("authorId") String authorId,
            @Param("updatedAt") Instant updatedAt
    );

    int addViews(@Param("id") String id, @Param("delta") long delta);

    List<String> findAllPublishedIds();

    /** 关注流由 MySQL 最终关系驱动，Redis 时间线缺失时仍可正确回源。 */
    List<CommunityPostView> findFollowingPage(
            @Param("userId") String userId,
            @Param("topicId") String topicId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countFollowing(@Param("userId") String userId, @Param("topicId") String topicId);

    /** 热榜缓存重建候选；最终排序分数在 Java 中计算并写入 Redis ZSet。 */
    List<CommunityPostView> findHotCandidates(@Param("limit") int limit);

    /** Redis 推荐结果回源 MySQL；Java 按 Redis 返回 ID 顺序重新排列。 */
    List<CommunityPostView> findPublicByIds(@Param("ids") List<String> ids);

    /** Redis GEO 不可用或为空时的 MySQL 距离回源。 */
    List<CommunityPostView> findNearby(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusKm") double radiusKm,
            @Param("topicId") String topicId,
            @Param("limit") int limit
    );

    List<CommunityPostView> findPublishedWithLocation();

    boolean existsActiveTopic(@Param("id") String id);

    List<CommunityTopicResponse> findActiveTopics();
}
