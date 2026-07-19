package com.edu.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 将项目 knowledge base 目录中的两门核心课程 Markdown 建成独立全局知识库。 */
@Component
public class CourseMarkdownKnowledgeSeeder implements ApplicationRunner {

    static final String SOURCE_TYPE = "COURSE_MARKDOWN";
    private static final Logger log = LoggerFactory.getLogger(CourseMarkdownKnowledgeSeeder.class);
    private static final String LICENSE = "用户提供的课程资料，仅用于本项目学习检索";

    private final KnowledgeBaseService knowledgeBaseService;
    private final String configuredDirectory;
    private final boolean enabled;

    public CourseMarkdownKnowledgeSeeder(
            KnowledgeBaseService knowledgeBaseService,
            @Value("${knowledge.seed.course-directory:../knowledge base}") String configuredDirectory,
            @Value("${knowledge.seed.core-courses-enabled:true}") boolean enabled) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.configuredDirectory = configuredDirectory;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        Path directory = locateDirectory();
        if (directory == null) {
            log.warn("核心课程知识库目录不存在，请配置 KNOWLEDGE_COURSE_DIRECTORY");
            return;
        }
        try {
            List<KnowledgeBaseService.SeedDocument> seeds = new ArrayList<>();
            addCourse(seeds, directory.resolve("Data Structure.md"),
                    "课程知识库：数据结构与算法", "knowledge://course/data-structure");
            addCourse(seeds, directory.resolve("Computer Organization Principles.md"),
                    "课程知识库：计算机组成原理", "knowledge://course/computer-organization");
            if (seeds.size() != 2) {
                log.warn("核心课程知识库文件不完整，目录={}", directory);
                return;
            }
            List<com.edu.agent.model.KnowledgeDocument> indexed =
                    knowledgeBaseService.replaceGlobalSeeds(SOURCE_TYPE, seeds);
            log.info("核心课程知识库初始化完成: documents={}, chunks={}", indexed.size(),
                    indexed.stream().mapToInt(value -> value.getChunkCount()).sum());
        } catch (Exception exception) {
            log.warn("核心课程知识库初始化失败: {}", exception.getMessage());
        }
    }

    private void addCourse(List<KnowledgeBaseService.SeedDocument> seeds, Path file,
                           String title, String sourceUri) throws Exception {
        if (!Files.isRegularFile(file)) return;
        String markdown = Files.readString(file, StandardCharsets.UTF_8);
        if (markdown.isBlank()) throw new IllegalArgumentException(file.getFileName() + " 内容为空");
        seeds.add(new KnowledgeBaseService.SeedDocument(title, sourceUri, LICENSE, markdown));
    }

    private Path locateDirectory() {
        Set<Path> candidates = new LinkedHashSet<>();
        if (configuredDirectory != null && !configuredDirectory.isBlank()) {
            candidates.add(Path.of(configuredDirectory));
        }
        candidates.add(Path.of("knowledge base"));
        candidates.add(Path.of("..", "knowledge base"));
        return candidates.stream().map(path -> path.toAbsolutePath().normalize())
                .filter(Files::isDirectory).findFirst().orElse(null);
    }
}
