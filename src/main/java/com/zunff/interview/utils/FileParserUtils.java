package com.zunff.interview.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

@Slf4j
@Component
public class FileParserUtils {

    public record Response(String text, boolean success, String error) {}

    public Response parse(String filePath) {
        log.info("解析文件: {}", filePath);

        if (filePath == null || filePath.isEmpty()) {
            return new Response("", false, "文件路径为空");
        }

        File file = new File(filePath);
        if (!file.exists()) {
            return new Response("", false, "文件不存在: " + filePath);
        }

        String fileName = file.getName().toLowerCase();
        try {
            String text;
            if (fileName.endsWith(".pdf")) {
                text = parsePdf(file);
            } else if (fileName.endsWith(".docx") || fileName.endsWith(".doc")) {
                text = parseDocx(file);
            } else {
                text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            }
            log.info("文件解析完成，文本长度: {}", text.length());
            return new Response(text, true, null);
        } catch (Exception e) {
            log.error("解析文件失败: {}", filePath, e);
            return new Response("", false, e.getMessage());
        }
    }

    private String parsePdf(File file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private String parseDocx(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph paragraph : paragraphs) {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty()) {
                    sb.append(text).append("\n");
                }
            }
            return sb.toString();
        }
    }
}
