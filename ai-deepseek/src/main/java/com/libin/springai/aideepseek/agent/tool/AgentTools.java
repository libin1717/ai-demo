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

/**
 * Agent 工具类，提供文件读写、代码搜索和目录列表等沙箱操作能力。
 * <p>
 * 所有文件操作限定在 .sandbox 目录内，通过路径安全校验防止路径遍历攻击。
 * 工具方法通过 {@code @Tool} 注解暴露给 Spring AI 框架，供 LLM 自动调用。
 * 使用 ThreadLocal 计录每个请求的工具调用次数。
 */
@Slf4j
public class AgentTools {

    /** 沙箱根目录的绝对路径 */
    private static final Path SANDBOX = Paths.get(".sandbox").toAbsolutePath().normalize();

    /** 线程隔离的工具调用计数器，每个请求独立计数 */
    private final ThreadLocal<Integer> toolCallCounter = ThreadLocal.withInitial(() -> 0);

    /**
     * 构造 AgentTools 实例，确保沙箱目录存在。
     * 如果创建目录失败，记录错误日志但不阻止后续操作。
     */
    public AgentTools() {
        try {
            Files.createDirectories(SANDBOX);
        } catch (IOException e) {
            log.error("Failed to create sandbox directory", e);
        }
    }

    /**
     * 获取并重置当前线程的工具调用计数。
     *
     * @return 重置前的工具调用次数
     */
    public int getAndResetToolCallCount() {
        int count = toolCallCounter.get();
        toolCallCounter.set(0);
        return count;
    }

    /**
     * 读取沙箱中指定文件的完整内容。
     * <p>
     * 文件路径会先经过安全校验，确保不会越出沙箱目录。
     *
     * @param path 相对于沙箱根目录的文件路径
     * @return 文件内容字符串，失败时返回以 "Error:" 开头的错误信息
     */
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

    /**
     * 在沙箱中创建或覆盖写入文件。
     * <p>
     * 如果目标路径的父目录不存在，会自动创建。
     * 文件路径会先经过安全校验。
     *
     * @param path    相对于沙箱根目录的文件路径
     * @param content 要写入的完整文件内容
     * @return 成功信息或错误信息字符串
     */
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

    /**
     * 在沙箱中搜索包含指定关键字的文件，返回匹配的文件路径和行内容。
     * <p>
     * 搜索范围限定为 .java、.md、.xml、.yml 文件，最多返回 20 条结果。
     *
     * @param keyword 搜索关键字
     * @return 匹配的文件路径和行内容（以换行符分隔），未找到时返回提示信息，失败时返回错误信息
     */
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

    /**
     * 列出沙箱中指定目录的文件和子目录结构，排序后显示。
     *
     * @param path 相对于沙箱根目录的路径，留空表示根目录
     * @return 目录结构列表（每行以 [DIR] 或 [FILE] 开头），失败时返回错误信息
     */
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
     * 路径安全校验：防止路径遍历攻击，将路径限制在沙箱目录范围内。
     * <p>
     * 通过规范化路径后检查前缀是否仍在沙箱目录内来实现。
     *
     * @param path 待校验的相对路径
     * @return 安全解析后的绝对路径，如果路径试图越出沙箱则返回 null
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
