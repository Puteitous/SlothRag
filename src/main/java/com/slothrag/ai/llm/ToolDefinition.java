package com.slothrag.ai.llm;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具定义（用于向模型声明可调用的 function）
 */
public class ToolDefinition {

    private final String name;
    private final String description;
    private final Map<String, Object> parameters;

    public ToolDefinition(String name, String description, Map<String, Object> parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    /**
     * 转成 OpenAI 兼容的 tools 数组元素
     */
    public Map<String, Object> toPayload() {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        if (description != null) {
            function.put("description", description);
        }
        if (parameters != null) {
            function.put("parameters", parameters);
        }
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    public String getName() {
        return name;
    }
}