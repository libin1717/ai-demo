package com.libin.springai.aideepseek.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class AgentTools {

    private static final Path SANDBOX = Paths.get(".sandbox").toAbsolutePath().normalize();

    private final ThreadLocal<Integer> toolCallCounter = ThreadLocal.withInitial(() -> 0);

    public AgentTools() {
        try {
            Files.createDirectories(SANDBOX);
        } catch (IOException e) {
            log.error("Failed to create sandbox directory", e);
        }
    }

    public int getAndResetToolCallCount() {
        int count = toolCallCounter.get();
        toolCallCounter.set(0);
        return count;
    }

    @Tool(description = "读取沙箱中指定文件的完整内容")
    public String readFile(@ToolParam(description = "相对于沙箱根目录的文件路径") String path) {
        toolCallCounter.set(toolCallCounter.get() + 1);
        Path target = resolveSafe(path);
        if (target == null) return "Error: 路径不在沙箱范围内";
        if (!Files.exists(target)) return "Error: 文件不存在 - " + path;
        try {
            return Files.readString(target);
        } catch (IOException e) {
            return "Error: 读取文件失败 - " + e.getMessage();
        }
    }

    @Tool(description = "在沙箱中创建或覆盖写入文件")
    public String writeFile(
            @ToolParam(description = "相对于沙箱根目录的文件路径") String path,
            @ToolParam(description = "要写入的完整文件内容") String content) {
        toolCallCounter.set(toolCallCounter.get() + 1);
        Path target = resolveSafe(path);
        if (target == null) return "Error: 路径不在沙箱范围内";
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content);
            return "成功写入文件: " + path + " (" + content.length() + " 字符)";
        } catch (IOException e) {
            return "Error: 写入文件失败 - " + e.getMessage();
        }
    }

    @Tool(description = "在沙箱中搜索包含指定关键字的文件，返回匹配的文件路径和行内容")
    public String searchCode(@ToolParam(description = "搜索关键字") String keyword) {
        toolCallCounter.set(toolCallCounter.get() + 1);
        try (Stream<Path> files = Files.walk(SANDBOX)) {
            List<String> results = files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".md")
                            || p.toString().endsWith(".xml") || p.toString().endsWith(".yml"))
                    .flatMap(file -> {
                        try {
                            return Files.readAllLines(file).stream()
                                    .filter(line -> line.contains(keyword))
                                    .map(line -> SANDBOX.relativize(file) + ": " + line.trim());
                        } catch (IOException e) {
                            return Stream.empty();
                        }
                    })
                    .limit(20)
                    .collect(Collectors.toList());
            if (results.isEmpty()) return "未找到包含 '" + keyword + "' 的文件";
            return String.join("\n", results);
        } catch (IOException e) {
            return "Error: 搜索失败 - " + e.getMessage();
        }
    }

    @Tool(description = "列出沙箱中指定目录的文件和子目录结构")
    public String listDirectory(
            @ToolParam(description = "相对于沙箱根目录的路径，留空表示根目录") String path) {
        toolCallCounter.set(toolCallCounter.get() + 1);
        Path dir = SANDBOX;
        if (path != null && !path.isBlank()) {
            Path resolved = resolveSafe(path);
            if (resolved == null) return "Error: 路径不在沙箱范围内";
            dir = resolved;
        }
        if (!Files.exists(dir)) return "Error: 目录不存在 - " + path;
        if (!Files.isDirectory(dir)) return "Error: 不是目录 - " + path;

        try (Stream<Path> entries = Files.list(dir)) {
            return entries
                    .map(p -> {
                        String prefix = Files.isDirectory(p) ? "[DIR]  " : "[FILE] ";
                        return prefix + SANDBOX.relativize(p);
                    })
                    .sorted()
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "Error: 列出目录失败 - " + e.getMessage();
        }
    }

    /**
     * Path safety check: prevent path traversal attacks, restrict to SANDBOX directory
     */
    private Path resolveSafe(String path) {
        Path resolved = SANDBOX.resolve(path).normalize();
        if (!resolved.startsWith(SANDBOX)) {
            log.warn("Path traversal attempt blocked: {}", path);
            return null;
        }
        return resolved;
    }
}
