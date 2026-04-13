package com.zunff.interview.utils;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AudioUtils {

    /**
     * 将 PCM 原始音频数据转换为 WAV 格式
     * 在 PCM 数据前添加 44 字节 WAV header
     *
     * @param pcmData     原始 PCM 字节数据
     * @param sampleRate  采样率（如 16000）
     * @param bitsPerSample  位深度（如 16）
     * @param channels    声道数（如 1）
     * @return WAV 格式字节数组
     */
    public static byte[] pcmToWav(byte[] pcmData, int sampleRate, int bitsPerSample, int channels) {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        int dataSize = pcmData.length;
        int fileSize = 36 + dataSize;

        ByteArrayOutputStream wavBuffer = new ByteArrayOutputStream(44 + dataSize);
        ByteBuffer header = ByteBuffer.allocate(44);
        header.order(ByteOrder.LITTLE_ENDIAN);

        // RIFF chunk
        header.put(new byte[]{'R', 'I', 'F', 'F'});
        header.putInt(fileSize);
        header.put(new byte[]{'W', 'A', 'V', 'E'});

        // fmt subchunk
        header.put(new byte[]{'f', 'm', 't', ' '});
        header.putInt(16);                    // Subchunk1Size (PCM = 16)
        header.putShort((short) 1);           // AudioFormat (PCM = 1)
        header.putShort((short) channels);
        header.putInt(sampleRate);
        header.putInt(byteRate);
        header.putShort((short) blockAlign);
        header.putShort((short) bitsPerSample);

        // data subchunk
        header.put(new byte[]{'d', 'a', 't', 'a'});
        header.putInt(dataSize);

        wavBuffer.write(header.array(), 0, 44);
        wavBuffer.write(pcmData, 0, dataSize);

        return wavBuffer.toByteArray();
    }
}
