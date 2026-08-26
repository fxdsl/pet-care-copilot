package com.petassistant.business.data.mapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.petassistant.business.data.dto.internal.CommunityCommentView;
import com.petassistant.business.data.dto.internal.CommunityReportView;
import com.petassistant.business.data.dto.internal.PublicUserSummaryView;
import com.petassistant.business.data.entity.CommunityCommentEntity;
import com.petassistant.business.data.entity.CommunityReportEntity;
import org.apache.ibatis.annotations.Param;

/** 第九周社区互动 Mapper；最终关系全部落 MySQL，Redis 只保存可重建副本。 */
public interface CommunitySocialMapper {

    boolean existsPublicPost(@Param("postId") String postId);

    int insertLike(@Param("postId") String postId, @Param("userId") String userId, @Param("createdAt") Instant createdAt);

    int deleteLike(@Param("postId") String postId, @Param("userId") String userId);

    boolean existsLike(@Param("postId") String postId, @Param("userId") String userId);

    long countLikes(@Param("postId") String postId);

    int synchronizeLikeCount(@Param("postId") String postId);

    int insertFavorite(@Param("postId") String postId, @Param("userId") String userId, @Param("createdAt") Instant createdAt);

    int deleteFavorite(@Param("postId") String postId, @Param("userId") String userId);

    boolean existsFavorite(@Param("postId") String postId, @Param("userId") String userId);

    long countFavorites(@Param("postId") String postId);

    int synchronizeFavoriteCount(@Param("postId") String postId);

    int insertFollow(@Param("followerId") String followerId, @Param("followedId") String followedId, @Param("createdAt") Instant createdAt);

    int deleteFollow(@Param("followerId") String followerId, @Param("followedId") String followedId);

    boolean existsFollow(@Param("followerId") String followerId, @Param("followedId") String followedId);

    long countFollowers(@Param("followedId") String followedId);

    long countFollowing(@Param("followerId") String followerId);

    List<PublicUserSummaryView> findFollowers(
            @Param("userId") String userId,
            @Param("viewerId") String viewerId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    List<PublicUserSummaryView> findFollowing(
            @Param("userId") String userId,
            @Param("viewerId") String viewerId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    List<String> findLikedPostIds(@Param("userId") String userId, @Param("postIds") List<String> postIds);

    List<String> findFavoritePostIds(@Param("userId") String userId, @Param("postIds") List<String> postIds);

    List<String> findFollowedUserIds(@Param("userId") String userId, @Param("authorIds") List<String> authorIds);

    int insertComment(CommunityCommentEntity comment);

    CommunityCommentView findComment(@Param("commentId") String commentId);

    List<CommunityCommentView> findComments(
            @Param("postId") String postId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countComments(@Param("postId") String postId);

    int softDeleteComment(
            @Param("commentId") String commentId,
            @Param("authorId") String authorId,
            @Param("updatedAt") Instant updatedAt
    );

    int synchronizeCommentCount(@Param("postId") String postId);

    int insertReport(CommunityReportEntity report);

    CommunityReportView findReport(@Param("reportId") String reportId);

    List<CommunityReportView> findReportPage(
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countReports(@Param("status") String status);

    int moderateReport(
            @Param("reportId") String reportId,
            @Param("moderatorId") String moderatorId,
            @Param("resolution") String resolution,
            @Param("moderatorNote") String moderatorNote,
            @Param("expectedVersion") int expectedVersion,
            @Param("resolvedAt") Instant resolvedAt
    );

    int hidePost(@Param("postId") String postId, @Param("updatedAt") Instant updatedAt);

    int hideComment(@Param("commentId") String commentId, @Param("updatedAt") Instant updatedAt);

    int insertCheckIn(@Param("userId") String userId, @Param("date") LocalDate date, @Param("createdAt") Instant createdAt);

    List<LocalDate> findCheckInDates(
            @Param("userId") String userId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    long countPendingReports();
}
