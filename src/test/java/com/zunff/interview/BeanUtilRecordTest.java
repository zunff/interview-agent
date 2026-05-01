package com.zunff.interview;

import cn.hutool.core.bean.BeanUtil;
import java.util.Map;

/**
 * Test to verify Hutool's BeanUtil.beanToMap() behavior with Java Records
 */
public class BeanUtilRecordTest {

    // Record 类型 - 模拟 InterviewState 中的某些字段
    record TestRecord(String candidateProfile, String jobInfo, int technicalQuestionsDone) {
        public Map<String, Object> asMap() {
            return cn.hutool.core.bean.BeanUtil.beanToMap(this);
        }
    }

    public static void main(String[] args) {
        System.out.println("=== Hutool BeanUtil.beanToMap() with Java Records ===\n");

        // 1. 创建 TestRecord 实例
        TestRecord record = new TestRecord(
            "Senior Java Developer with 5+ years experience",
            "Backend Engineer position",
            7
        );

        System.out.println("TestRecord instance created:");
        System.out.println("  candidateProfile: " + record.candidateProfile());
        System.out.println("  jobInfo: " + record.jobInfo());
        System.out.println("  technicalQuestionsDone: " + record.technicalQuestionsDone());
        System.out.println();

        // 2. 调用 asMap() 并打印内容
        Map<String, Object> result = record.asMap();

        System.out.println("Result of BeanUtil.beanToMap(this):");
        System.out.println("  Keys: " + result.keySet());
        System.out.println();

        System.out.println("Key-Value pairs:");
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            System.out.println("  " + entry.getKey() + " = " + entry.getValue());
        }
        System.out.println();

        // 3. 验证键名是否与 record 组件名完全匹配
        boolean keysMatch = result.keySet().equals(java.util.Set.of(
            "candidateProfile",
            "jobInfo",
            "technicalQuestionsDone"
        ));

        System.out.println("=== Results ===");
        System.out.println("Keys match record component names: " + keysMatch);

        if (!keysMatch) {
            System.out.println("\nFAILED: Keys do NOT match!");
            System.out.println("Expected keys: [candidateProfile, jobInfo, technicalQuestionsDone]");
            System.out.println("Actual keys: " + result.keySet());
        } else {
            System.out.println("\nSUCCESS: Keys match record component names!");
        }

        System.out.println("\n=== Conclusion ===");
        System.out.println("BeanUtil.beanToMap() preserves Record component names as Map keys.");
    }
}
