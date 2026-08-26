param(
    [string]$BusinessUrl = "http://localhost:18080"
)

$ErrorActionPreference = "Stop"
$username = "week9_smoke_" + [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$password = "Week9Smoke!2026"
$headers = @{ "Content-Type" = "application/json" }

# Create an isolated user without relying on the developer's credentials.
$session = Invoke-RestMethod -Method Post -Uri "$BusinessUrl/api/v1/auth/register" -Headers $headers -Body (@{
    username = $username
    password = $password
    displayName = "Week 9 Smoke User"
} | ConvertTo-Json)
$authHeaders = @{
    "Content-Type" = "application/json"
    "Authorization" = "Bearer $($session.accessToken)"
}

# Publish a located post, then verify idempotent reactions, comment, check-in and report.
$draft = Invoke-RestMethod -Method Post -Uri "$BusinessUrl/api/v1/community/posts" -Headers $authHeaders -Body (@{
    title = "Week 9 smoke test"
    content = "Temporary post created by smoke-week9.ps1."
    region = "Hangzhou"
    latitude = 30.2741
    longitude = 120.1551
    mediaIds = @()
} | ConvertTo-Json)
$post = Invoke-RestMethod -Method Post -Uri "$BusinessUrl/api/v1/community/posts/$($draft.id)/publish" -Headers $authHeaders
$null = Invoke-RestMethod -Method Put -Uri "$BusinessUrl/api/v1/community/posts/$($post.id)/like" -Headers $authHeaders
$like = Invoke-RestMethod -Method Put -Uri "$BusinessUrl/api/v1/community/posts/$($post.id)/like" -Headers $authHeaders
$favorite = Invoke-RestMethod -Method Put -Uri "$BusinessUrl/api/v1/community/posts/$($post.id)/favorite" -Headers $authHeaders
$comment = Invoke-RestMethod -Method Post -Uri "$BusinessUrl/api/v1/community/posts/$($post.id)/comments" -Headers $authHeaders -Body (@{
    content = "The comment endpoint works."
} | ConvertTo-Json)
$checkIn = Invoke-RestMethod -Method Put -Uri "$BusinessUrl/api/v1/community/check-ins/today" -Headers $authHeaders
$report = Invoke-RestMethod -Method Post -Uri "$BusinessUrl/api/v1/community/reports" -Headers $authHeaders -Body (@{
    targetType = "POST"
    targetId = $post.id
    reasonType = "OTHER"
    description = "Temporary report created by the smoke test."
} | ConvertTo-Json)
$hotFeed = Invoke-RestMethod -Method Get -Uri "$BusinessUrl/api/v1/community/posts?feed=HOT&size=10" -Headers $authHeaders
$nearbyFeed = Invoke-RestMethod -Method Get -Uri "$BusinessUrl/api/v1/community/posts?feed=NEARBY&latitude=30.2741&longitude=120.1551&radiusKm=5&size=10" -Headers $authHeaders

[pscustomobject]@{
    username = $username
    userId = $session.user.id
    postId = $post.id
    commentId = $comment.id
    reportId = $report.id
    idempotentLikeCount = $like.count
    favoriteCount = $favorite.count
    checkedIn = $checkIn.checkedIn
    hotFeedContainsPost = [bool]($hotFeed.items.id -contains $post.id)
    nearbyFeedContainsPost = [bool]($nearbyFeed.items.id -contains $post.id)
} | ConvertTo-Json -Compress
