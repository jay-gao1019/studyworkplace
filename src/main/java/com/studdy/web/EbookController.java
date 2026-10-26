package com.studdy.web;

import com.studdy.service.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 人教电子书伺服（docs/03 M4.7 / FR-24，v2.2）。
 * 书目为家长 drop-in 的 PDF：data/ebooks/<subject>-<年级><上|下>.pdf；文件不进库、不参与备份。
 */
@RestController
@RequestMapping("/api/ebook")
public class EbookController {

    private static final List<String> SUBJECTS = List.of("cn", "math", "en");
    private static final List<String> TERMS = List.of("上", "下");

    private final Path base;

    public EbookController(@Value("${studdy.ebooks-dir:data/ebooks}") String dir) {
        this.base = Paths.get(dir).toAbsolutePath().normalize();
    }

    /** 就绪索引：{"cn-4上":true,…,"dir":"绝对路径"}。 */
    @GetMapping("/index")
    public Map<String, Object> index() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String s : SUBJECTS) {
            for (int g = 1; g <= 6; g++) {
                for (String t : TERMS) {
                    out.put(s + "-" + g + t, Files.exists(base.resolve(fileName(s, g, t))));
                }
            }
        }
        out.put("dir", base.toString());
        return out;
    }

    @GetMapping(value = "/{subject}/{grade}/{term}/file", produces = MediaType.APPLICATION_PDF_VALUE)
    public Resource file(@PathVariable String subject, @PathVariable int grade,
                         @PathVariable String term) throws IOException {
        if (!SUBJECTS.contains(subject) || grade < 1 || grade > 6 || !TERMS.contains(term)) {
            throw new ApiException(400, "非法的电子书参数");
        }
        Path p = base.resolve(fileName(subject, grade, term)).normalize();
        if (!p.startsWith(base)) {
            throw new ApiException(400, "非法路径");
        }
        if (!Files.isRegularFile(p)) {
            throw new ApiException(404, "电子书还没就位：请把人教版 PDF 命名为 " + p.getFileName()
                    + " 放到 " + base + " 目录");
        }
        return new FileSystemResource(p);
    }

    private static String fileName(String subject, int grade, String term) {
        return subject + "-" + grade + term + ".pdf";
    }
}
