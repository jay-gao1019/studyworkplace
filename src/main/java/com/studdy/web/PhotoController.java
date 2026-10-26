package com.studdy.web;

import com.studdy.service.AdminService;
import com.studdy.service.ApiException;
import com.studdy.service.PhotoService;
import com.studdy.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 佐证照片 API（docs/02 v2.4，FR-32/M13）：
 * POST /api/photo/evidence（multipart：file, kind, origName?, note?）→ {photoId,url,takenAt,note}
 * GET  /api/photo/{id} 图片伺服（服务端已烧录时间水印）。
 */
@RestController
@RequestMapping("/api/photo")
public class PhotoController {

    private final PhotoService photos;
    private final SessionService session;
    private final AdminService admin;

    public PhotoController(PhotoService photos, SessionService session, AdminService admin) {
        this.photos = photos;
        this.session = session;
        this.admin = admin;
    }

    @PostMapping(value = "/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam("kind") String kind,
                                      @RequestParam(value = "profileId", required = false) String profileId,
                                      @RequestParam(value = "origName", required = false) String origName,
                                      @RequestParam(value = "note", required = false) String note,
                                      HttpServletRequest req) {
        // 家长带 X-Admin-Token 时可代孩子上传（指定 profileId）；否则一律取会话档案（M9.5：孩子不能替别人传）
        String pid = profileId;
        if (pid != null && !pid.isBlank()) {
            if (!admin.validToken(req.getHeader("X-Admin-Token"))) {
                throw new ApiException(401, "需要家长身份验证");
            }
        } else {
            pid = session.currentProfileId();
        }
        if (pid == null) {
            throw new ApiException(400, "请先创建孩子档案");
        }
        return photos.upload(file, pid, kind, origName, note);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> serve(@PathVariable String id) {
        Resource r = photos.open(id);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(r);
    }
}
