package cafe.snails.ecomagents.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 图片视觉分析服务 — 使用本地 codex exec CLI 进行多模态分析。
 * <p>临时方案：下载图片到临时文件，调用 codex exec --image 进行分析。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImageAnalysisService {

    private static final String CODEX_BIN = "codex";
    private static final int TIMEOUT_SECONDS = 300;

    private final ObjectMapper objectMapper;

    /**
     * 分析一张图片的表达结构（构图、场景、风格、文案结构）。
     *
     * @param imageUrl 图片 URL
     * @return 图片表达结构 JSON 字符串
     */
    public String analyzeImageExpression(String imageUrl) {
        Path tempDir = null;
        Path tempImage = null;
        Path outputFile = null;

        try {
            // Create temp directory for this analysis
            tempDir = Files.createTempDirectory("img-analysis-");
            tempImage = tempDir.resolve("image-" + UUID.randomUUID() + ".jpg");
            outputFile = tempDir.resolve("output.txt");

            // Download image
            log.info("[ImageAnalysis] Downloading image from {}", imageUrl);
            try (InputStream in = new URL(imageUrl).openStream()) {
                Files.copy(in, tempImage);
            }
            long fileSize = Files.size(tempImage);
            log.info("[ImageAnalysis] Downloaded {} bytes to {}", fileSize, tempImage);

            // Build prompt for codex exec
            String prompt = """
                    你是一位 Amazon US car stereo 类目图片提示词工程师，熟悉 gpt-image-2 的生图特点。

                    你的任务：
                    分析用户提供的参考图，提取它的视觉表达方法，并改写成一份可直接用于 gpt-image-2 的中文生图提示词模板。

                    重要原则：
                    1. 不复刻原图，不复制原图品牌、商标、具体文案、具体布局。
                    2. 只借鉴原图的表达逻辑、视觉层级、场景类型、卖点呈现方式。
                    3. 输出内容必须服务于 car stereo / Android car head unit / automotive electronics 类目。
                    4. 产品外观由后续上传的产品参考图提供，因此不要编造产品尺寸、型号、按钮数量、接口数量等细节。
                    5. 输出必须是最终生图提示词，不要输出分析过程，不要输出 JSON，不要 markdown 代码块。

                    请按以下固定结构输出：

                    【画面结构】
                    说明画面比例、整体布局、主视觉焦点、辅助视觉区域、信息层级。

                    【场景】
                    说明汽车内饰、安装环境、驾驶场景、功能使用场景或棚拍场景。

                    【产品主体】
                    说明产品在画面中的角色、位置、状态，例如中控台安装、屏幕亮起、悬浮展示、接口特写等。
                    不要描述具体外观尺寸细节。

                    【视角】
                    说明镜头角度、距离、透视关系，例如正视、45 度、后排看向中控、驾驶员视角、俯视等。

                    【风格】
                    说明整体视觉风格，例如 Amazon A+ 信息图、高端汽车科技广告、真实电商产品摄影、暗色科技风等。

                    【色彩】
                    说明主色、辅助色、强调色、对比关系和背景色。

                    【光线】
                    说明光线类型、方向、阴影、高光、屏幕发光效果。

                    【文字及排版】
                    给出适合画面的主标题 1 个、短标签 2-4 个。
                    要求文字简短、清晰、适合 gpt-image-2 渲染。
                    说明文字位置、字体风格、颜色、对齐方式。
                    不要生成超长句子或大量小字。

                    【功能表达】
                    说明如何用视觉元素表达 car stereo 卖点，例如导航、CarPlay、Android Auto、蓝牙、倒车影像、DSP、EQ、RCA、低音炮、方向盘
                    控制等。
                    只选择与参考图最相关的 1-3 个功能，不要贪多。

                    【约束】
                    列出负面约束：无人物、无手、无品牌 Logo、无水印、无乱码、无错误接口、无产品变形、无杂乱背景、无过多线缆、无卡通风格。

                    【质量】
                    说明高分辨率、商业广告级、真实材质、适合 Amazon gallery / A+ 页面使用。

                    输出要求：
                    只输出以上固定结构内容。
                    每个标题下根据画面复杂度填写必要信息，不强制限制句数。
                    优先保证信息完整、层级清晰、可直接用于 gpt-image-2 生图。
                    如果参考图信息密度较高，可以使用短句或分号组织，但不要写成分析报告。
                    避免无关解释、创作理由、评价性语言。
                    每个标题下只写与生图直接相关的视觉指令，不要写“为什么这样设计”“这样能提升转化率”等分析性内容。
                    """.stripIndent().trim();

            // Build command (use cmd /c on Windows to resolve PATH)
            String osName = System.getProperty("os.name").toLowerCase();
            // Write prompt to a file and pipe via stdin (avoids Windows cmd line length limits)
            Path promptFile = tempDir.resolve("prompt.txt");
            Files.writeString(promptFile, prompt, StandardCharsets.UTF_8);

            List<String> cmd = new ArrayList<>();
            if (osName.contains("win")) {
                cmd.add("cmd");
                cmd.add("/c");
            }
            cmd.add(CODEX_BIN);
            cmd.add("exec");
            cmd.add("--image");
            cmd.add(tempImage.toAbsolutePath().toString());
            cmd.add("--output-last-message");
            cmd.add(outputFile.toAbsolutePath().toString());
            cmd.add("--skip-git-repo-check");
            cmd.add("--ephemeral");
            cmd.add("--dangerously-bypass-approvals-and-sandbox");
            cmd.add("-");  // Read prompt from stdin

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(tempDir.toFile());
            // Detect and set proxy for codex
            String proxyUrl = detectProxyUrl();
            log.info("[ImageAnalysis] Proxy detection result: {}", proxyUrl != null ? proxyUrl : "none");
            if (proxyUrl != null) {
                pb.environment().put("HTTP_PROXY", proxyUrl);
                pb.environment().put("HTTPS_PROXY", proxyUrl);
                pb.environment().put("http_proxy", proxyUrl);
                pb.environment().put("https_proxy", proxyUrl);
            }

            log.info("[ImageAnalysis] Running: {} exec --image {} --output-last-message {}",
                    CODEX_BIN, tempImage.getFileName(), outputFile.getFileName());

            // Redirect stdin from prompt file, stdout/stderr to files
            pb.redirectInput(promptFile.toFile());
            Path stdoutFile = tempDir.resolve("stdout.log");
            Path stderrFile = tempDir.resolve("stderr.log");
            pb.redirectOutput(stdoutFile.toFile());
            pb.redirectError(stderrFile.toFile());

            Process process = pb.start();
            boolean finished = process.waitFor(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);

            // Read captured output
            String stdout = Files.exists(stdoutFile) ? Files.readString(stdoutFile, StandardCharsets.UTF_8) : "";
            String stderr = Files.exists(stderrFile) ? Files.readString(stderrFile, StandardCharsets.UTF_8) : "";

            log.info("[ImageAnalysis] codex exec exitCode={}, finished={}, stdoutLen={}, stderrLen={}",
                    finished ? process.exitValue() : -1, finished,
                    stdout.length(), stderr.length());

            if (!stderr.isBlank()) {
                log.warn("[ImageAnalysis] codex exec STDERR (last 3K): {}", truncate(stderr, 100));
            }
            if (!stdout.isBlank()) {
                log.info("[ImageAnalysis] codex exec STDOUT (last 3K): {}", truncate(stdout, 100));
            }

            if (!finished) {
                process.destroyForcibly();
                log.warn("[ImageAnalysis] codex exec timed out after {}s", TIMEOUT_SECONDS);
                return generateFallbackExpression(imageUrl);
            }

            // Read output file directly (plain text in template format)
            if (Files.exists(outputFile) && Files.size(outputFile) > 0) {
                String output = Files.readString(outputFile, StandardCharsets.UTF_8).trim();
                log.info("[ImageAnalysis] Got output ({} chars)", output.length());
                if (output.length() > 50) {
                    return output;
                }
            }

            // Fallback: check stdout
            if (stdout.length() > 50) {
                log.info("[ImageAnalysis] Using stdout as result ({} chars)", stdout.length());
                return stdout;
            }

            log.warn("[ImageAnalysis] No analysis result from codex, using fallback");
            return generateFallbackExpression(imageUrl);

        } catch (Exception e) {
            log.error("[ImageAnalysis] Failed to analyze image: {}", e.getMessage(), e);
            return generateFallbackExpression(imageUrl);
        } finally {
            // Cleanup: delete temp directory and all contents recursively
            if (tempDir != null) {
                try {
                    Files.walk(tempDir)
                            .sorted((a, b) -> b.compareTo(a))  // delete children before parent
                            .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
                } catch (Exception ignored) {}
            }
        }
    }

    private String extractJson(String text) {
        if (text == null || text.isBlank()) return null;
        // Try to find JSON object in the text
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String candidate = text.substring(start, end + 1);
            try {
                objectMapper.readTree(candidate);
                return candidate;
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * 返回当前使用的分析提示词原文（硬编码模板）。
     * 用于 Controller 存入分析记录时记录当时的 prompt 内容。
     */
    public String getAnalysisPrompt() {
        return """
                你是一位 Amazon 汽车电子产品图视觉分析与副图策划专家。

                请按以下三步分析这张图片：

                【第一步：原图客观分析】
                不要改写，只做客观分析：
                1. 这张图表达的核心卖点是什么？
                2. 画面用了哪些视觉元素来表达这个卖点？
                3. 用户看完后会形成什么购买认知？
                4. 哪些表达方式值得借鉴？
                5. 哪些地方不适合照搬？

                【第二步：卖点转画面方案】
                基于上一步的卖点分析，重新构思一张适合该产品的 Amazon 副图方案：

                【核心卖点】
                提取 1 个最主要卖点，不要贪多。

                【买家利益】
                这个卖点解决了买家的什么问题，为什么会提高购买意愿。

                【卖点转画面逻辑】
                这个卖点应该通过什么视觉方式让买家一眼看懂。

                【目标】
                这一张新图要让用户相信什么。

                【主体】
                产品 + 关键元素（不要描述产品外观细节，外观由实际参考图提供）。

                【场景】
                适合该卖点的车内真实使用场景。

                【画面结构】
                重新设计画面结构，明确前景/中景/背景的位置关系。不要照搬原图布局。

                【屏幕显示内容】
                车机屏幕应该显示什么内容（导航、音乐、电话、倒车影像等）。

                【关键视觉元素】
                必须出现的元素（手机、无线信号、UI、箭头、图标等）。

                【文字】
                主标题 1 个，短标签 2-3 个，全部简短清晰。

                【文字排版样式】
                科技感胶囊标签 / 扁平化细线条 / 半透明信息卡片 / 纯色背景块。

                【风格】
                亚马逊优质汽车电子产品摄影，清晰、逼真、科技感、商业广告风格。

                【光线】
                根据场景选择明亮影棚光 / 自然车内光 / 夜间氛围光。

                【负面约束】
                避免产品变形、按钮错乱、屏幕比例错误、文字乱码、低清晰度、杂乱背景、多余线缆。

                【第三步：最终图生图提示词】
                请将以上方案整合成一段可以直接用于图生图的中文提示词。
                **重要：只描述画面概念（场景/布局/文字/风格/光线/负面约束），不要描述产品外观尺寸细节，产品外观由上传的参考图提供。最终只输出第三步的提示词内容，不要输出第一步和第二步的分析过程。**
                """.stripIndent().trim();
    }

    /**
     * 检测系统代理 URL：优先读环境变量 HTTP_PROXY，其次读 Windows 注册表系统代理设置。
     */
    /**
     * 检测系统代理 URL：优先读环境变量，其次读 Windows 注册表。
     */
    private String detectProxyUrl() {
        // 1. Environment variables
        for (String var : new String[]{"HTTPS_PROXY", "https_proxy", "HTTP_PROXY", "http_proxy"}) {
            String val = System.getenv(var);
            if (val != null && !val.isBlank()) return val;
        }
        // 2. Windows registry via reg query
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            try {
                // Read ProxyEnable and ProxyServer via reg query
                Process p = Runtime.getRuntime().exec(
                        "reg query \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" "
                                + "/v ProxyEnable");
                p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                boolean enabled = out.contains("0x1");
                if (enabled) {
                    p = Runtime.getRuntime().exec(
                            "reg query \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" "
                                    + "/v ProxyServer");
                    p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                    out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    // Parse: ProxyServer    REG_SZ    localhost:15236
                    var m = java.util.regex.Pattern.compile("ProxyServer\\s+REG_SZ\\s+(\\S+)").matcher(out);
                    if (m.find()) {
                        String proxy = "http://" + m.group(1).trim();
                        log.info("[ImageAnalysis] Detected Windows proxy from registry: {}", proxy);
                        return proxy;
                    }
                }
            } catch (Exception e) {
                log.warn("[ImageAnalysis] Failed to detect proxy: {}", e.getMessage());
            }
        }
        return null;
    }

    private String readStream(InputStream stream) throws IOException {
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private String truncate(String text, int maxLen) {
        return text != null && text.length() > maxLen ? text.substring(0, maxLen) : text;
    }

    private String generateFallbackExpression(String imageUrl) {
        return """
                【目标】
                让用户相信这款车载中控屏功能全面、安装便捷，适合日常驾驶使用

                【主体】
                car stereo 中控屏主机 + 连接手机 + 功能模块

                【场景】
                车内中控台真实安装场景，或 studio 合成展示

                【画面结构】
                产品居中或左上主视觉，功能模块/标签围绕排列

                【关键视觉元素】
                中控屏、手机连接界面、CarPlay/Android Auto 图标、方向盘

                【风格】
                Amazon premium automotive product photography, clean, realistic

                【光线】
                bright studio lighting

                【文字】
                功能短标签（3-5个）

                【文字排版样式】
                白色粗体无衬线字，半透明背景底块

                【约束】
                no clutter, no extra cables, accurate product appearance
                """.stripIndent().trim();
    }
}
