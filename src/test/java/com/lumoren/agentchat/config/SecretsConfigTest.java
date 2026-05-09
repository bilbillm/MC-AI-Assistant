package com.lumoren.agentchat.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SecretsConfig（JSON secrets 文件读写）单元测试。
 * <p>
 * 使用 {@link TempDir} 创建临时目录进行文件 I/O 测试，
 * 避免污染实际配置文件。
 */
class SecretsConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void testTemplateGeneratedOnFirstAccess() {
        // 在临时目录中创建 SecretsConfig，应自动生成模板文件
        Path secretsFile = tempDir.resolve("agentchat-secrets.json");
        assertFalse(Files.exists(secretsFile), "测试前文件不应存在");

        new SecretsConfig(secretsFile);

        assertTrue(Files.exists(secretsFile), "构造函数应自动生成模板文件");
    }

    @Test
    void testHasApiKeyReturnsFalseByDefault() {
        Path secretsFile = tempDir.resolve("agentchat-secrets.json");
        SecretsConfig secrets = new SecretsConfig(secretsFile);

        assertFalse(secrets.hasApiKey(), "新生成的模板文件应返回 hasApiKey()=false");
    }

    @Test
    void testSaveAndLoadApiKey() {
        Path secretsFile = tempDir.resolve("agentchat-secrets.json");
        SecretsConfig secrets = new SecretsConfig(secretsFile);

        // 设置 API Key
        String testKey = "sk-test-key-12345";
        secrets.setApiKey(testKey);

        // 验证读取
        assertEquals(testKey, secrets.getApiKey(),
                "setApiKey 后 getApiKey 应返回相同值");
        assertTrue(secrets.hasApiKey(), "设置 Key 后 hasApiKey 应返回 true");
    }

    @Test
    void testPersistenceAcrossInstances() {
        Path secretsFile = tempDir.resolve("agentchat-secrets.json");

        // 实例 A：设置 API Key
        SecretsConfig writer = new SecretsConfig(secretsFile);
        writer.setApiKey("sk-persistent-key");

        // 实例 B：重新加载，验证 Key 持久化
        SecretsConfig reader = new SecretsConfig(secretsFile);
        assertEquals("sk-persistent-key", reader.getApiKey(),
                "API Key 应在不同实例间保持持久化");
    }

    @Test
    void testTemplateJsonContent() throws IOException {
        Path secretsFile = tempDir.resolve("agentchat-secrets.json");
        new SecretsConfig(secretsFile);

        String content = Files.readString(secretsFile);
        assertTrue(content.contains("apiKey"), "模板 JSON 应包含 apiKey 字段");
        assertTrue(content.contains("_comment"), "模板 JSON 应包含 _comment 说明字段");
    }

    @Test
    void testSetApiKeyWithNull() {
        Path secretsFile = tempDir.resolve("agentchat-secrets.json");
        SecretsConfig secrets = new SecretsConfig(secretsFile);

        secrets.setApiKey(null);
        // null 应被视为空字符串保存
        assertFalse(secrets.hasApiKey(), "null API Key 应被视为未配置");
        assertEquals("", secrets.getApiKey(), "null 应被转换为空字符串");
    }

    @Test
    void testEmptyKeyAfterClear() {
        Path secretsFile = tempDir.resolve("agentchat-secrets.json");
        SecretsConfig secrets = new SecretsConfig(secretsFile);

        secrets.setApiKey("sk-old-key");
        assertTrue(secrets.hasApiKey());

        // 清空 Key
        secrets.setApiKey("");
        assertFalse(secrets.hasApiKey(), "空字符串 Key 应被视为未配置");
    }

    @Test
    void testGetSecretsPath() {
        Path secretsFile = tempDir.resolve("custom-secrets.json");
        SecretsConfig secrets = new SecretsConfig(secretsFile);

        assertEquals(secretsFile, secrets.getSecretsPath(),
                "getSecretsPath() 应返回构造时传入的路径");
    }
}
