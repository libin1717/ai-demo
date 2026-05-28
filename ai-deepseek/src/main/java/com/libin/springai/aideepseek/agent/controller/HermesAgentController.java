package com.libin.springai.aideepseek.agent.controller;

import com.libin.springai.aicommon.dto.ResultDto;
import com.libin.springai.aideepseek.agent.dto.AgentResponse;
import com.libin.springai.aideepseek.agent.dto.SkillInfo;
import com.libin.springai.aideepseek.agent.service.AgentService;
import com.libin.springai.aideepseek.agent.service.MemoryStore;
import com.libin.springai.aideepseek.agent.service.SkillManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/agent")
public class HermesAgentController {

    @Autowired
    private AgentService agentService;

    @Autowired
    private MemoryStore memoryStore;

    @Autowired
    private SkillManager skillManager;

    @PostMapping("/chat")
    public ResultDto<AgentResponse> chat(@RequestParam String message) {
        AgentResponse response = agentService.executeCycle(message);
        return ResultDto.success(response);
    }

    @GetMapping("/memory")
    public ResultDto<Map<String, Integer>> memorySummary() {
        return ResultDto.success(memoryStore.getMemorySummary());
    }

    @GetMapping("/memory/{fileName}")
    public ResultDto<String> memoryFile(@PathVariable String fileName) {
        String content = memoryStore.getMemoryFile(fileName);
        if (content == null) {
            return ResultDto.fail();
        }
        return ResultDto.success(content);
    }

    @GetMapping("/skills")
    public ResultDto<List<SkillInfo>> skills() {
        return ResultDto.success(skillManager.listAll());
    }

    @GetMapping("/skills/{skillName}")
    public ResultDto<String> skillDetail(@PathVariable String skillName) {
        String content = skillManager.getByName(skillName);
        if (content == null) {
            return ResultDto.fail();
        }
        return ResultDto.success(content);
    }

    // ========== Demo endpoint ==========

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

        return ResultDto.success(result);
    }

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
