package com.libin.springai.aideepseek.agent.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.libin.springai.aideepseek.agent.dto.SkillInfo;
import com.libin.springai.aideepseek.agent.dto.SkillUsageRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skill 自我进化核心服务，全自动运行，用户无感。
 *
 * 三大核心能力：
 * - 自动评分：usageCount×0.3 + successRate×0.4 + recency×0.3 = 0-100
 * - 自动富化：满足阈值 → LLM 分析 → 质量门禁 → 自动应用
 * - 自动回滚：7 天内 effectiveness 下降 ≥ 15 分 → 自动恢复上一版本
 *
 * 所有操作由 AgentService 在每次 execute cycle 后自动触发，不暴露任何 REST API。
 * 进化事件通过 log.info 记录到应用日志。
 */
@Slf4j
@Service
public class SkillEvolutionService {

    @Autowired
    private SkillManager skillManager;

    @Autowired
    private SkillUsageTracker usageTracker;

    @Autowired
    private DeepSeekChatModel deepSeekChatModel;

    private static final int ENRICH_MATCH_THRESHOLD = 5;
    private static final int ENRICH_SUCCESS_RATE_THRESHOLD = 80;
    private static final int REGRESSION_THRESHOLD = 15;
    private static final int REGRESSION_WINDOW_DAYS = 7;

    private static final String ENRICH_PROMPT =
            "以下是当前技能文档和最近几次成功使用的上下文。请分析技能文档可以在哪些方面改进：\n" +
            "1. 补充缺失的边界情况和注意事项\n" +
            "2. 澄清模糊或不够具体的步骤\n" +
            "3. 增加更具体的代码示例或模板\n\n" +
            "输出纯 JSON（不要包含任何其他文字）：\n" +
            "{\n" +
            "  \"suggestedContent\": \"改进后的完整技能正文（Markdown格式，不含frontmatter）\",\n" +
            "  \"changesSummary\": \"一句话描述本次改动\"\n" +
            "}\n\n" +
            "当前技能文档：\n";

    /** 记录每次变更前的 effectiveness，用于回归检测 */
    private final Map<String, Integer> preChangeScores = new ConcurrentHashMap<>();

    /** 记录每次变更的时间 */
    private final Map<String, LocalDateTime> lastChangeTime = new ConcurrentHashMap<>();

    /**
     * 重新计算 skill 的 effectiveness 评分并更新 frontmatter。
     */
    public int recalculateScore(String skillName) {
        String rawContent = skillManager.getByName(skillName);
        if (rawContent == null) return 0;
        SkillInfo info = skillManager.parseFrontMatter(rawContent);
        if (info == null) return 0;

        int usageCount = usageTracker.countMatches(skillName);
        int successCount = usageTracker.countSuccessfulMatches(skillName);
        int successRate = usageCount > 0
                ? (int) ((double) successCount / usageCount * 100)
                : 0;
        int recency = computeRecency(info.getLastUsed());

        int usageFactor = (int) (Math.min((double) usageCount / 50, 1.0) * 100);
        int effectiveness = (int) (usageFactor * 0.3 + successRate * 0.4 + recency * 0.3);

        String lastUsed = LocalDateTime.now().toString().substring(0, 19);
        skillManager.updateEffectiveness(skillName, effectiveness, usageCount, successRate, lastUsed);

        log.info("Skill '{}' score recalculated: effectiveness={}, usageCount={}, successRate={}%, recency={}",
                skillName, effectiveness, usageCount, successRate, recency);
        return effectiveness;
    }

    /**
     * 检查是否满足自动富化触发条件：成功匹配 ≥ 5 次 AND 成功率 ≥ 80%
     */
    public boolean checkEnrichmentThreshold(String skillName) {
        int totalMatches = usageTracker.countMatches(skillName);
        int successMatches = usageTracker.countSuccessfulMatches(skillName);
        if (successMatches < ENRICH_MATCH_THRESHOLD) return false;
        int successRate = totalMatches > 0
                ? (int) ((double) successMatches / totalMatches * 100) : 0;
        return successRate >= ENRICH_SUCCESS_RATE_THRESHOLD;
    }

    /**
     * 自动触发 LLM 富化分析，经质量门禁后自动应用。
     */
    public void generateAndApplyEnrichment(String skillName) {
        String currentContent = skillManager.getByName(skillName);
        if (currentContent == null) return;

        List<SkillUsageRecord> recentMatches = usageTracker.getRecentMatches(skillName, 5);
        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < recentMatches.size(); i++) {
            SkillUsageRecord r = recentMatches.get(i);
            contextBuilder.append(String.format("%d. [%s] %s (toolCalls=%d)\n",
                    i + 1, r.getTimestamp(), r.getUserMessageDigest(), r.getToolCallCount()));
        }

        String suggestedContent;
        String changesSummary;
        try {
            String prompt = ENRICH_PROMPT + currentContent + "\n\n最近成功使用上下文：\n" + contextBuilder;
            String result = deepSeekChatModel.call(
                    new Prompt(prompt,
                            DeepSeekChatOptions.builder().model("deepseek-v4-flash").temperature(0.0).build()))
                    .getResult().getOutput().getText();

            String json = cleanJson(result);
            JSONObject obj = JSON.parseObject(json);
            suggestedContent = obj.getString("suggestedContent");
            changesSummary = obj.getString("changesSummary");

            if (suggestedContent == null || suggestedContent.isBlank()) {
                log.warn("Enrichment LLM returned empty content for skill '{}', skipping", skillName);
                return;
            }
        } catch (Exception e) {
            log.error("Failed to generate enrichment for skill '{}': {}", skillName, e.getMessage());
            return;
        }

        if (!qualityGate(skillName, currentContent, suggestedContent)) {
            log.warn("Enrichment failed quality gate for skill '{}', skipping auto-apply", skillName);
            return;
        }

        SkillInfo currentInfo = skillManager.parseFrontMatter(currentContent);
        int preScore = currentInfo != null ? currentInfo.getEffectiveness() : 0;
        preChangeScores.put(skillName, preScore);
        lastChangeTime.put(skillName, LocalDateTime.now());

        skillManager.updateSkill(skillName, suggestedContent, "enrichment",
                changesSummary != null ? changesSummary : "LLM auto-enrichment");

        log.info("SKILL AUTO-ENRICHED: '{}' (pre-score={}) — {}", skillName, preScore, changesSummary);
    }

    /**
     * 内部质量门禁：非空 + 触发词保留≥50% + 长度变化≤±80%
     */
    boolean qualityGate(String skillName, String currentContent, String suggestedContent) {
        if (suggestedContent == null || suggestedContent.isBlank()) {
            log.warn("Quality gate [content-empty] failed for skill '{}'", skillName);
            return false;
        }

        SkillInfo currentInfo = skillManager.parseFrontMatter(currentContent);

        if (currentInfo != null && currentInfo.getTriggers() != null) {
            List<String> originalTriggers = currentInfo.getTriggers();
            String lowerSuggested = suggestedContent.toLowerCase();
            long retainedCount = originalTriggers.stream()
                    .filter(t -> lowerSuggested.contains(t.toLowerCase()))
                    .count();
            double retentionRate = (double) retainedCount / originalTriggers.size();
            if (retentionRate < 0.5) {
                log.warn("Quality gate [triggers-retention={}] failed for skill '{}': {}/{} triggers retained",
                        String.format("%.0f%%", retentionRate * 100), skillName, retainedCount, originalTriggers.size());
                return false;
            }
        }

        String currentBody = skillManager.extractBody(currentContent);
        if (currentBody == null) currentBody = currentContent;
        double lengthRatio = (double) suggestedContent.length() / Math.max(currentBody.length(), 1);
        if (lengthRatio < 0.2 || lengthRatio > 1.8) {
            log.warn("Quality gate [length-ratio={}] failed for skill '{}': {} → {} chars",
                    String.format("%.1f", lengthRatio), skillName, currentBody.length(), suggestedContent.length());
            return false;
        }

        log.info("Quality gate PASSED for skill '{}'", skillName);
        return true;
    }

    /**
     * 检测评分退化并自动回滚。7 天内下降 ≥ 15 分自动恢复到上一版本。
     */
    public void detectAndRevertRegression(String skillName, int currentScore) {
        Integer preScore = preChangeScores.get(skillName);
        LocalDateTime changeTime = lastChangeTime.get(skillName);
        if (preScore == null || changeTime == null) return;

        long daysSinceChange = ChronoUnit.DAYS.between(changeTime, LocalDateTime.now());
        if (daysSinceChange > REGRESSION_WINDOW_DAYS) {
            preChangeScores.remove(skillName);
            lastChangeTime.remove(skillName);
            return;
        }

        int drop = preScore - currentScore;
        if (drop >= REGRESSION_THRESHOLD) {
            log.warn("REGRESSION DETECTED for skill '{}': {} → {} (drop={}, {} days ago). Auto-reverting...",
                    skillName, preScore, currentScore, drop, daysSinceChange);

            String rawContent = skillManager.getByName(skillName);
            if (rawContent != null) {
                SkillInfo info = skillManager.parseFrontMatter(rawContent);
                if (info != null && info.getVersion() > 1) {
                    skillManager.revertSkill(skillName, info.getVersion() - 1);
                    log.info("SKILL AUTO-REVERTED: '{}' to v{} (effectiveness dropped {} → {})",
                            skillName, info.getVersion() - 1, preScore, currentScore);
                }
            }
            preChangeScores.remove(skillName);
            lastChangeTime.remove(skillName);
        }
    }

    // ===== 内部方法 =====

    private int computeRecency(String lastUsed) {
        if (lastUsed == null || lastUsed.isBlank() || "null".equals(lastUsed)) return 0;
        try {
            LocalDateTime last = LocalDateTime.parse(lastUsed.substring(0, 19));
            long days = ChronoUnit.DAYS.between(last, LocalDateTime.now());
            if (days <= 7) return 100;
            if (days <= 30) return 50;
            return 10;
        } catch (Exception e) {
            return 0;
        }
    }

    private String cleanJson(String raw) {
        String json = raw.trim();
        if (json.startsWith("```")) {
            json = json.substring(json.indexOf("\n") + 1);
            if (json.endsWith("```")) {
                json = json.substring(0, json.lastIndexOf("```")).trim();
            }
        }
        return json;
    }
}
