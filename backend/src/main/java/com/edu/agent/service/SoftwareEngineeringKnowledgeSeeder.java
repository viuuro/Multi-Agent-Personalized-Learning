package com.edu.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 安装按课程拆分的软件工程专业种子知识，并自动替换旧版本。 */
@Component
public class SoftwareEngineeringKnowledgeSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SoftwareEngineeringKnowledgeSeeder.class);
    private static final String RESOURCE_PATH = "knowledge/software-engineering-courses.md";
    private static final String SOURCE_URI = "https://csed.acm.org/knowledge-areas/";
    private static final String LICENSE = "项目原创课程摘要；知识结构参考 ACM/IEEE CS2023 与 SWEBOK V4";
    private static final Pattern COURSE_HEADING = Pattern.compile("^#\\s+(.+?)\\s*$");

    private final KnowledgeBaseService knowledgeBaseService;
    private final boolean enabled;

    public SoftwareEngineeringKnowledgeSeeder(
            KnowledgeBaseService knowledgeBaseService,
            @Value("${knowledge.seed.software-engineering-enabled:true}") boolean enabled) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        try {
            ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
            String markdown = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            List<KnowledgeBaseService.SeedDocument> seeds = parseCourseSeeds(markdown);
            knowledgeBaseService.replaceGlobalSeeds(seeds);
            log.info("软件工程专业课程种子知识初始化完成，共 {} 门课程", seeds.size());
        } catch (Exception exception) {
            log.warn("软件工程专业课程种子知识初始化失败: {}", exception.getMessage());
        }
    }

    static List<KnowledgeBaseService.SeedDocument> parseCourseSeeds(String markdown) {
        List<KnowledgeBaseService.SeedDocument> seeds = new ArrayList<>();
        String currentTitle = null;
        StringBuilder currentContent = new StringBuilder();

        for (String line : markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            Matcher matcher = COURSE_HEADING.matcher(line.trim());
            if (matcher.matches()) {
                addCourseSeed(seeds, currentTitle, currentContent);
                currentTitle = matcher.group(1).trim();
                currentContent.setLength(0);
                currentContent.append("# ").append(currentTitle).append('\n');
            } else if (currentTitle != null) {
                currentContent.append(line).append('\n');
            }
        }
        addCourseSeed(seeds, currentTitle, currentContent);
        if (seeds.isEmpty()) throw new IllegalArgumentException("课程种子文件没有一级课程标题");
        return seeds;
    }

    private static void addCourseSeed(List<KnowledgeBaseService.SeedDocument> seeds,
                                      String title,
                                      StringBuilder content) {
        if (title == null || content.toString().isBlank()) return;
        seeds.add(new KnowledgeBaseService.SeedDocument(
                "课程种子：" + title,
                SOURCE_URI,
                LICENSE,
                content.toString().trim()));
    }
}
