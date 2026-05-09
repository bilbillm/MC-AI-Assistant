package com.lumoren.agentchat.i18n;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 国际化测试：验证所有翻译键在中英文语言文件中均存在且值非空。
 * <p>
 * 测试方式：直接加载 JSON 资源文件，不依赖 Minecraft 运行时环境。
 */
class I18nTest {

    private static final String ZH_CN_PATH = "/assets/agentchat/lang/zh_cn.json";
    private static final String EN_US_PATH = "/assets/agentchat/lang/en_us.json";

    private static final Gson GSON = new Gson();

    /**
     * 加载 classpath 上的 JSON 语言文件为 {@code Map<String, String>}。
     */
    private static Map<String, String> loadLang(String path) {
        InputStream is = I18nTest.class.getResourceAsStream(path);
        assertNotNull(is, "语言文件未找到: " + path);
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
        } catch (Exception e) {
            fail("加载语言文件失败: " + path, e);
            return null;
        }
    }

    /**
     * 通过反射获取 {@link I18nKeys} 中所有 {@code public static final String} 常量的值。
     */
    private static java.util.List<String> getAllKeys() {
        return java.util.Arrays.stream(I18nKeys.class.getDeclaredFields())
                .filter(f -> Modifier.isStatic(f.getModifiers())
                        && Modifier.isPublic(f.getModifiers())
                        && Modifier.isFinal(f.getModifiers())
                        && f.getType() == String.class)
                .map(f -> {
                    try {
                        return (String) f.get(null);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();
    }

    @Test
    void testAllKeysExistInZhCn() {
        Map<String, String> zhCn = loadLang(ZH_CN_PATH);
        assertNotNull(zhCn, "zh_cn.json 不应为 null");
        assertFalse(zhCn.isEmpty(), "zh_cn.json 不应为空");

        var keys = getAllKeys();
        assertFalse(keys.isEmpty(), "I18nKeys 中应至少有一个常量");

        for (String key : keys) {
            assertTrue(zhCn.containsKey(key),
                    "zh_cn.json 缺少键: " + key);
            String value = zhCn.get(key);
            assertNotNull(value, "zh_cn.json 中键 '" + key + "' 的值为 null");
            assertFalse(value.isBlank(), "zh_cn.json 中键 '" + key + "' 的值为空");
        }
    }

    @Test
    void testAllKeysExistInEnUs() {
        Map<String, String> enUs = loadLang(EN_US_PATH);
        assertNotNull(enUs, "en_us.json 不应为 null");
        assertFalse(enUs.isEmpty(), "en_us.json 不应为空");

        var keys = getAllKeys();
        assertFalse(keys.isEmpty(), "I18nKeys 中应至少有一个常量");

        for (String key : keys) {
            assertTrue(enUs.containsKey(key),
                    "en_us.json 缺少键: " + key);
            String value = enUs.get(key);
            assertNotNull(value, "en_us.json 中键 '" + key + "' 的值为 null");
            assertFalse(value.isBlank(), "en_us.json 中键 '" + key + "' 的值为空");
        }
    }

    @Test
    void testZhCnAndEnUsHaveSameKeys() {
        Map<String, String> zhCn = loadLang(ZH_CN_PATH);
        Map<String, String> enUs = loadLang(EN_US_PATH);

        assertEquals(zhCn.keySet(), enUs.keySet(),
                "中英文语言文件的键集合不一致");
    }

    @Test
    void testTotalKeyCount() {
        Map<String, String> zhCn = loadLang(ZH_CN_PATH);
        assertTrue(zhCn.size() >= 20,
                "翻译键总数应至少为 20，当前: " + zhCn.size());
    }

    @Test
    void testI18nKeysConstantCount() {
        var keys = getAllKeys();
        assertTrue(keys.size() >= 20,
                "I18nKeys 常量数量应至少为 20，当前: " + keys.size());
    }

    @Test
    void testKeysMatchBetweenConstantsAndLangFiles() {
        var keys = getAllKeys();
        Map<String, String> zhCn = loadLang(ZH_CN_PATH);

        // I18nKeys 中定义的每个常量，在两个语言文件中都必须存在
        for (String key : keys) {
            assertTrue(zhCn.containsKey(key),
                    "I18nKeys 中定义了但 zh_cn.json 中缺少的键: " + key);
        }

        // 语言文件中的每个键，在 I18nKeys 中都必须对应一个常量
        for (String key : zhCn.keySet()) {
            assertTrue(keys.contains(key),
                    "zh_cn.json 中定义了但 I18nKeys 中缺少的键: " + key);
        }
    }
}
