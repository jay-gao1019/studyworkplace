package com.studdy.web;

import com.studdy.service.AdminService;
import com.studdy.service.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 电子书家长上传通道（docs/03 M14，v2.4/FR-33）。
 * 本机无外网代下载 → 家长在手机/电脑浏览器完成上传（国家中小学智慧教育平台获取 PDF），
 * 同名覆盖即时生效（/api/ebook/index 按文件存在性推导，无需重启）。
 */
@RestController
@RequestMapping("/api/admin/ebook")
public class AdminEbookController {

    private static final List<String> SUBJECTS = List.of("cn", "math", "en");
    private static final List<String> TERMS = List.of("上", "下");
    private static final long MAX_BYTES = 80L * 1024 * 1024;

    private final AdminService admin;
    private final Path base;

    public AdminEbookController(AdminService admin,
                                @Value("${studdy.ebooks-dir:data/ebooks}") String dir) {
        this.admin = admin;
        this.base = Paths.get(dir).toAbsolutePath().normalize();
    }

    private void gate(HttpServletRequest req) {
        if (!admin.validToken(req.getHeader("X-Admin-Token"))) {
            throw new ApiException(401, "需要家长身份验证");
        }
    }

    /** 就绪网格：36 组合（科目×年级×学期）的文件名/大小/日期 + 目录位置（教材资源页水合）。 */
    @GetMapping("/presets")
    public Map<String, Object> presets(HttpServletRequest req) throws IOException {
        gate(req);
        List<Map<String, Object>> grid = new ArrayList<>();
        for (String s : SUBJECTS) {
            for (int g = 1; g <= 6; g++) {
                for (String t : TERMS) {
                    grid.add(cell(s, g, t));
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("grid", grid);
        out.put("dir", base.toString());
        return out;
    }

    /** 上传（multipart：subject/grade/term/file）：同名覆盖=替换版本，返回该格就绪信息。 */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestParam("subject") String subject,
                                      @RequestParam("grade") int grade,
                                      @RequestParam("term") String term,
                                      @RequestParam("file") MultipartFile file,
                                      HttpServletRequest req) throws IOException {
        gate(req);
        validateKey(subject, grade, term);
        if (file == null || file.isEmpty()) {
            throw new ApiException(400, "请选择 PDF 文件");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(400, "电子书 PDF 不能超过 80MB");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!name.toLowerCase().endsWith(".pdf")) {
            throw new ApiException(400, "仅支持 PDF 电子书（从教材平台下载后上传即可）");
        }
        byte[] head = new byte[5];
        try (InputStream in = file.getInputStream()) {
            int read = in.read(head);
            if (read < 5 || !new String(head, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")) {
                throw new ApiException(400, "文件不是有效的 PDF");
            }
        }
        Files.createDirectories(base);
        Path target = base.resolve(fileName(subject, grade, term)).normalize();
        if (!target.startsWith(base)) {
            throw new ApiException(400, "非法路径");
        }
        Path tmp = base.resolve(fileName(subject, grade, term) + ".part");
        file.transferTo(tmp.toFile());
        if (Files.exists(target)) {
            Files.delete(target);
        }
        Files.move(tmp, target);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("saved", cell(subject, grade, term));
        out.put("index", refIndex(subject));
        return out;
    }

    /** 删除对应 PDF：孩子端入口即时变灰。 */
    @DeleteMapping("/{subject}/{grade}/{term}")
    public Map<String, Object> delete(@PathVariable String subject, @PathVariable int grade,
                                      @PathVariable String term, HttpServletRequest req) throws IOException {
        gate(req);
        validateKey(subject, grade, term);
        Path p = base.resolve(fileName(subject, grade, term)).normalize();
        boolean removed = Files.isRegularFile(p) && Files.deleteIfExists(p);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("removed", removed);
        out.put("key", subject + "-" + grade + term);
        return out;
    }

    private Map<String, Object> cell(String subject, int grade, String term) throws IOException {
        Path p = base.resolve(fileName(subject, grade, term));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", subject + "-" + grade + term);
        m.put("subject", subject);
        m.put("grade", grade);
        m.put("term", term);
        boolean ready = Files.isRegularFile(p);
        m.put("ready", ready);
        m.put("fileName", fileName(subject, grade, term));
        if (ready) {
            var attrs = Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class);
            m.put("sizeBytes", attrs.size());
            m.put("updatedAt", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .format(attrs.lastModifiedTime().toInstant().atZone(ZoneId.systemDefault())));
        }
        return m;
    }

    /** 该学科 12 格就绪布尔（上传响应里带回，前端即时刷新网格）。 */
    private Map<String, Object> refIndex(String subject) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int g = 1; g <= 6; g++) {
            for (String t : TERMS) {
                out.put(subject + "-" + g + t, Files.exists(base.resolve(fileName(subject, g, t))));
            }
        }
        return out;
    }

    private static void validateKey(String subject, int grade, String term) {
        if (!SUBJECTS.contains(subject) || grade < 1 || grade > 6 || !TERMS.contains(term)) {
            throw new ApiException(400, "非法的电子书参数（科目限 cn/math/en，年级 1–6，学期 上/下）");
        }
    }

    private static String fileName(String subject, int grade, String term) {
        return subject + "-" + grade + term + ".pdf";
    }
}
