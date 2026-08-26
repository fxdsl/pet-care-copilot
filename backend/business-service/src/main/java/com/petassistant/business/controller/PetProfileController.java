package com.petassistant.business.controller;

import java.security.Principal;
import java.util.List;

import com.petassistant.business.data.dto.request.CreatePetProfileRequest;
import com.petassistant.business.data.dto.request.UpdatePetProfileRequest;
import com.petassistant.business.data.dto.response.PetProfileResponse;
import com.petassistant.business.service.PetProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 宠物档案 HTTP 控制器，只负责请求校验和响应状态码。
 */
@RestController
@RequestMapping("/api/v1/pet-profiles")
public class PetProfileController {

    private final PetProfileService service;

    /** 注入宠物档案服务。 */
    public PetProfileController(PetProfileService service) {
        this.service = service;
    }

    /** 创建宠物档案并返回完整记录。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PetProfileResponse create(Principal principal, @Valid @RequestBody CreatePetProfileRequest request) {
        return service.create(principal.getName(), request);
    }

    /** 查询最近创建的宠物档案。 */
    @GetMapping
    public List<PetProfileResponse> list(Principal principal, @RequestParam(defaultValue = "50") int limit) {
        return service.list(principal.getName(), limit);
    }

    /** 完整修改当前用户自己的宠物档案。 */
    @PutMapping("/{profileId}")
    public PetProfileResponse update(
            Principal principal,
            @PathVariable String profileId,
            @Valid @RequestBody UpdatePetProfileRequest request
    ) {
        return service.update(principal.getName(), profileId, request);
    }

    /** 删除当前用户自己的宠物档案。 */
    @DeleteMapping("/{profileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Principal principal, @PathVariable String profileId) {
        service.delete(principal.getName(), profileId);
    }
}
