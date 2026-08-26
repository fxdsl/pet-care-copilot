package com.petassistant.business.controller;

import java.security.Principal;

import com.petassistant.business.data.dto.request.CreateMediaUploadRequest;
import com.petassistant.business.data.dto.response.CommunityMediaResponse;
import com.petassistant.business.data.dto.response.MediaDownloadResponse;
import com.petassistant.business.data.dto.response.MediaUploadResponse;
import com.petassistant.business.service.CommunityMediaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 社区媒体预签名上传、确认与短期下载地址入口。 */
@RestController
@RequestMapping("/api/v1/community/media")
public class CommunityMediaController {

    private final CommunityMediaService service;

    public CommunityMediaController(CommunityMediaService service) { this.service = service; }

    @PostMapping("/upload-url")
    @ResponseStatus(HttpStatus.CREATED)
    public MediaUploadResponse uploadUrl(
            Principal principal, @Valid @RequestBody CreateMediaUploadRequest request
    ) { return service.createUpload(principal.getName(), request); }

    @PostMapping("/{mediaId}/confirm")
    public CommunityMediaResponse confirm(Principal principal, @PathVariable String mediaId) {
        return service.confirm(principal.getName(), mediaId);
    }

    @GetMapping("/{mediaId}/download-url")
    public MediaDownloadResponse download(Principal principal, @PathVariable String mediaId) {
        return service.download(principal.getName(), mediaId);
    }
}
