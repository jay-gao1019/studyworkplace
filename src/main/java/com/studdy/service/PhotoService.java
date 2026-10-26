package com.studdy.service;

import com.studdy.domain.photo.WatermarkRule;
import com.studdy.entity.PhotoEvidenceEntity;
import com.studdy.repo.PhotoEvidenceRepository;
import com.studdy.util.Ids;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 佐证照片服务（docs/03 M13，v2.4/FR-32）：multipart 上传 → 降采样 → Java2D 时间水印烧录
 * → data/evidence/<profileId>/<id>.jpg；文件不进库、不进备份 payload，仅元数据行入库。
 */
@Service
public class PhotoService {

    private static final List<String> ALLOWED_EXT = List.of("jpg", "jpeg", "png");
    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private static final int MAX_EDGE = 1600;

    private final PhotoEvidenceRepository photos;
    private final Path base;

    public PhotoService(PhotoEvidenceRepository photos,
                        @Value("${studdy.evidence-dir:data/evidence}") String dir) {
        this.photos = photos;
        this.base = Paths.get(dir).toAbsolutePath().normalize();
    }

    @Transactional
    public Map<String, Object> upload(MultipartFile file, String profileId, String kind,
                                      String origName, String note) {
        if (profileId == null || !profileId.matches("[A-Za-z0-9_]{1,40}")) {
            throw new ApiException(400, "档案标识不合法");
        }
        if (kind == null || !kind.matches("[a-z_]{1,24}")) {
            throw new ApiException(400, "佐证场景标识不合法");
        }
        if (file == null || file.isEmpty()) {
            throw new ApiException(400, "请选择一张照片");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(400, "单张照片不能超过 8MB");
        }
        String ext = extOf(origName == null ? file.getOriginalFilename() : origName);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new ApiException(400, "仅支持 JPEG/PNG 照片（WebP 请在相机设置里改为 JPEG 或截图后上传）");
        }
        BufferedImage img = read(file);
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        String id = Ids.next("pe");
        List<String> lines = WatermarkRule.lines(now, sceneLabel(kind), clip(note, 120));
        BufferedImage stamped = stamp(img, lines);
        try {
            Path dir = base.resolve(profileId);
            Files.createDirectories(dir);
            Path out = dir.resolve(id + ".jpg");
            writeJpeg(stamped, out.toFile());
            PhotoEvidenceEntity e = new PhotoEvidenceEntity(id, profileId, kind,
                    out.toString(), clip(origName, 120), clip(note, 120),
                    now.atZone(ZoneId.systemDefault()).toInstant());
            photos.save(e);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("photoId", id);
            m.put("url", "/api/photo/" + id);
            m.put("takenAt", e.getTakenAt().toString());
            m.put("note", e.getNote());
            return m;
        } catch (java.io.IOException e) {
            throw new ApiException(500, "照片保存失败，请重试");
        }
    }

    public Resource open(String photoId) {
        PhotoEvidenceEntity e = photos.findById(sanitizeId(photoId))
                .orElseThrow(() -> new ApiException(404, "照片不存在或已被清理"));
        Path p = Paths.get(e.getFilePath()).toAbsolutePath().normalize();
        if (!p.startsWith(base) || !Files.isRegularFile(p)) {
            throw new ApiException(404, "照片文件不在书架上（可能被手动清理），请联系家长重新上传");
        }
        return new FileSystemResource(p);
    }

    /** 审批中心/撤回清理工用元数据。 */
    public List<Map<String, Object>> viewsByIds(List<String> ids) {
        return photos.findAllById(ids).stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("url", "/api/photo/" + e.getId());
            m.put("kind", e.getKind());
            m.put("note", e.getNote());
            m.put("takenAt", e.getTakenAt().toString());
            return m;
        }).toList();
    }

    /** M13.6：档案重置/删除连带物理删除照片目录与元数据行。 */
    @Transactional
    public int purgeProfile(String profileId) {
        int n = photos.findByProfileIdOrderByCreatedAtDesc(profileId).size();
        photos.deleteAll(photos.findByProfileIdOrderByCreatedAtDesc(profileId));
        try {
            Path dir = base.resolve(profileId);
            if (Files.isDirectory(dir)) {
                try (Stream<Path> s = Files.walk(dir)) {
                    s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                }
            }
        } catch (java.io.IOException ignored) {
        }
        return n;
    }

    private static String sanitizeId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_]{1,40}")) {
            throw new ApiException(400, "照片标识不合法");
        }
        return id;
    }

    private BufferedImage read(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) {
                throw new ApiException(400, "文件不是有效图片");
            }
            return downscale(img);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(400, "图片解析失败，请换一张试试");
        }
    }

    private BufferedImage downscale(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        int longEdge = Math.max(w, h);
        if (longEdge <= MAX_EDGE) {
            return src;
        }
        float r = (float) MAX_EDGE / longEdge;
        int nw = Math.round(w * r), nh = Math.round(h * r);
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    private BufferedImage stamp(BufferedImage img, List<String> lines) {
        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(img, 0, 0, null);
        WatermarkRule.Layout ly = WatermarkRule.layout(out.getWidth(), out.getHeight(), lines);
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, ly.fontSize());
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int tw = 0;
        for (String l : lines) {
            tw = Math.max(tw, fm.stringWidth(l));
        }
        // 按真实字形宽度（中文≈1em，远大于布局的 0.62em 估算）重排右下角黑底框，保证不出界
        int boxW = Math.max(1, Math.min(tw + ly.pad() * 2, out.getWidth() - ly.pad() * 2));
        int boxX = Math.max(ly.pad(), out.getWidth() - ly.pad() - boxW);
        g.setColor(new Color(0, 0, 0, 130));
        g.fillRoundRect(boxX, ly.boxY(), boxW, ly.boxH(), 8, 8);
        g.setColor(Color.WHITE);
        int textX = Math.max(2, Math.min(boxX + ly.pad(), out.getWidth() - 2 - tw));
        int y = ly.textBaseY() + fm.getAscent() / 2;
        for (String l : lines) {
            g.drawString(l, textX, y);
            y += ly.lineH();
        }
        g.dispose();
        return out;
    }

    private void writeJpeg(BufferedImage img, java.io.File out) throws java.io.IOException {
        ImageWriter w = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam p = w.getDefaultWriteParam();
        p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        p.setCompressionQuality(0.85f);
        try (var fos = new FileImageOutputStream(out)) {
            w.setOutput(fos);
            w.write(null, new IIOImage(img, null, null), p);
        } finally {
            w.dispose();
        }
    }

    private static String sceneLabel(String kind) {
        return switch (kind) {
            case "task" -> "任务打卡";
            case "checkin" -> "每日签到";
            case "textbook" -> "教材打卡";
            case "manual" -> "家长记录";
            case "penalty" -> "行为扣分";
            case "life" -> "作息打卡";
            case "habit" -> "习惯里程碑";
            default -> "学习佐证";
        };
    }

    private static String extOf(String name) {
        int i = name == null ? -1 : name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase();
    }

    private static String clip(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max));
    }
}
