package com.zunff.interview.utils;

/**
 * Omni 综合分与提示词一致：S = avg(accuracy,logic,fluency,confidence)，V = avg(emotion, body)，
 * overall = round(clamp(S*0.75 + voiceTone*0.15 + V*0.10, 0, 100))。
 */
public final class OmniOverallScoreUtils {


    public static int computeOverallScore(int accuracy, int logic, int fluency, int confidence,
                                          int emotionScore, int bodyLanguageScore, int voiceToneScore) {
        int a = clamp(accuracy, 0, 100);
        int l = clamp(logic, 0, 100);
        int f = clamp(fluency, 0, 100);
        int c = clamp(confidence, 0, 100);
        int e = clamp(emotionScore, 0, 100);
        int b = clamp(bodyLanguageScore, 0, 100);
        int v = clamp(voiceToneScore, 0, 100);
        double s = (a + l + f + c) / 4.0;
        double visualAvg = (e + b) / 2.0;
        double raw = s * 0.75 + v * 0.15 + visualAvg * 0.10;
        return clamp((int) Math.round(raw), 0, 100);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
