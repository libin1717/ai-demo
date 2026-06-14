package com.libin.springai.aideepseek.agent.controller;

import com.alibaba.fastjson.JSON;
import com.libin.springai.aicommon.dto.ResultDto;
import com.libin.springai.aideepseek.agent.dto.AgentResponse;
import com.libin.springai.aideepseek.agent.dto.SkillInfo;
import com.libin.springai.aideepseek.agent.service.AgentService;
import com.libin.springai.aideepseek.agent.service.MemoryStore;
import com.libin.springai.aideepseek.agent.service.SkillManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hermes Agent REST API 控制器，提供对话、记忆查看和技能管理接口。
 * <p>
 * 所有接口统一使用 {@link ResultDto} 包装响应结果。
 */
@Slf4j
@RestController
@RequestMapping("/agent")
public class HermesAgentController {

    @Autowired
    private AgentService agentService;

    @Autowired
    private MemoryStore memoryStore;

    @Autowired
    private SkillManager skillManager;

    /**
     * 向 Agent 发送一条消息并获取完整响应。
     * <p>
     * 响应中包含 AI 回复、工具调用次数、使用的记忆统计以及技能沉淀状态。
     *
     * @param message 用户输入的消息文本
     * @return 包含 Agent 完整执行结果的响应
     */
    @PostMapping("/chat")
    public ResultDto<AgentResponse> chat(@RequestParam String message) {
        AgentResponse response = agentService.executeCycle(message);
        return ResultDto.success(response);
    }

    /**
     * 获取当前所有记忆文件的条目统计。
     *
     * @return 文件名到条目数量的映射
     */
    @GetMapping("/memory")
    public ResultDto<Map<String, Integer>> memorySummary() {
        return ResultDto.success(memoryStore.getMemorySummary());
    }

    /**
     * 获取指定记忆文件的完整内容。
     *
     * @param fileName 文件名，如 facts.md、profile.md、decisions.md
     * @return 文件内容字符串，文件不存在时返回失败结果
     */
    @GetMapping("/memory/{fileName}")
    public ResultDto<String> memoryFile(@PathVariable String fileName) {
        String content = memoryStore.getMemoryFile(fileName);
        if (content == null) {
            return ResultDto.fail();
        }
        return ResultDto.success(content);
    }

    /**
     * 获取所有已存储的技能列表。
     *
     * @return 技能信息列表
     */
    @GetMapping("/skills")
    public ResultDto<List<SkillInfo>> skills() {
        return ResultDto.success(skillManager.listAll());
    }

    /**
     * 获取指定技能的完整文档内容。
     *
     * @param skillName 技能名称
     * @return 技能文档的完整 Markdown 内容，不存在时返回失败结果
     */
    @GetMapping("/skills/{skillName}")
    public ResultDto<String> skillDetail(@PathVariable String skillName) {
        String content = skillManager.getByName(skillName);
        if (content == null) {
            return ResultDto.fail();
        }
        return ResultDto.success(content);
    }

    // ========== Demo endpoint ==========

    /**
     * 运行预定义的 4 轮演示对话，展示 Agent 的记忆累积和技能沉淀能力。
     * <p>
     * 演示流程依次创建用户注册 Controller、添加参数校验、创建订单 Controller、创建商品 Controller，
     * 每轮对话后会记录记忆状态和技能列表的变化。
     *
     * @return 包含各轮对话详情、记忆前后状态的汇总结果
     */
    @PostMapping("/demo/run")
    public ResultDto<Map<String, Object>> runDemo() {
        String[] messages = {
                "帮我写一个用户注册的 Controller，包含 POST /register 接口，接收 username 和 password 参数，返回注册结果",
                "给刚才的 Controller 加上参数校验，使用 @Valid 注解，用户名不能为空且长度 3-20，密码不能为空且长度 6-50",
                "帮我再写一个订单 Controller，包含创建订单和查询订单两个接口，风格跟之前的保持一致",
                "最后再写一个商品 Controller，包含商品列表查询和商品详情查询接口"
        };

        List<Map<String, Object>> rounds = new ArrayList<>();
        String beforeState = captureMemoryState();

        for (int i = 0; i < messages.length; i++) {
            AgentResponse response = agentService.executeCycle(messages[i]);
            Map<String, Object> round = new LinkedHashMap<>();
            round.put("round", i + 1);
            round.put("userMessage", messages[i]);
            round.put("response", response);
            round.put("memoryAfterRound", memoryStore.getMemorySummary());
            round.put("skillsAfterRound", skillManager.listAll());
            rounds.add(round);
        }

        String afterState = captureMemoryState();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rounds", rounds);
        result.put("memoryBefore", beforeState);
        result.put("memoryAfter", afterState);

        ResultDto<Map<String, Object>> success = ResultDto.success(result);
        log.info("/demo/run,result :{}", JSON.toJSONString(success));
        return success;
    }

    /**
     * 捕获当前所有记忆文件的完整内容快照。
     *
     * @return 包含 facts.md、profile.md、decisions.md 全部内容的字符串
     */
    private String captureMemoryState() {
        StringBuilder sb = new StringBuilder();
        for (String file : List.of("facts.md", "profile.md", "decisions.md")) {
            String content = memoryStore.getMemoryFile(file);
            if (content != null) {
                sb.append("=== ").append(file).append(" ===\n").append(content).append("\n");
            }
        }
        return sb.toString();
    }
}
