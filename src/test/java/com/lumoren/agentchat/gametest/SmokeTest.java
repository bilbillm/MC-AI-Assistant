package com.lumoren.agentchat.gametest;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 冒烟测试，验证 JUnit 5 + Mockito 测试框架可正常工作。
 * <p>
 * 这是一个最小化的冒烟测试，用于确认测试基础设施已正确配置。
 */
public class SmokeTest {

    /**
     * 最基本的 JUnit 5 测试：确认 assertTrue(true) 通过。
     */
    @Test
    public void testFrameworkWorks() {
        assertTrue(true, "JUnit 5 应该能够正确执行测试");
    }

    /**
     * 验证 Mockito 可正常创建 mock 对象。
     */
    @Test
    public void testMockitoWorks() {
        Runnable mock = Mockito.mock(Runnable.class);
        assertNotNull(mock, "Mockito.mock() 应返回非 null 对象");
    }
}
