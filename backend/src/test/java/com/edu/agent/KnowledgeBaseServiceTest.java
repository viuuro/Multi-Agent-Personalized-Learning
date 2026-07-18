package com.edu.agent;

import com.edu.agent.model.KnowledgeDocument;
import com.edu.agent.repository.KnowledgeChunkRepository;
import com.edu.agent.repository.KnowledgeDocumentRepository;
import com.edu.agent.service.KnowledgeBaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:knowledge-base;MODE=MYSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "knowledge.search.mysql-fulltext-enabled=true"
})
@Import(KnowledgeBaseService.class)
class KnowledgeBaseServiceTest {

    @Autowired private KnowledgeBaseService knowledgeBaseService;
    @Autowired private KnowledgeDocumentRepository documentRepository;
    @Autowired private KnowledgeChunkRepository chunkRepository;

    @Test
    void indexesDeduplicatesAndRetrievesChineseKnowledge() {
        KnowledgeDocument first = knowledgeBaseService.indexParsedFile(
                7L, "conversation-a", "requirements.txt", "CHAT",
                "# 需求工程\n需求追踪把业务目标、需求、设计和测试关联起来。"
                        + "验收标准必须清晰并且可以验证。".repeat(20));
        KnowledgeDocument duplicate = knowledgeBaseService.indexParsedFile(
                7L, "conversation-a", "requirements-copy.txt", "CHAT",
                "# 需求工程\n需求追踪把业务目标、需求、设计和测试关联起来。"
                        + "验收标准必须清晰并且可以验证。".repeat(20));

        assertThat(duplicate.getId()).isEqualTo(first.getId());
        assertThat(first.getChunkCount()).isGreaterThan(0);
        assertThat(knowledgeBaseService.search(
                7L, "conversation-a", "需求追踪和验收标准", 5))
                .isNotEmpty()
                .allSatisfy(result -> assertThat(result.documentId()).isEqualTo(first.getId()));
    }

    @Test
    void respectsConversationUserAndGlobalScopes() {
        KnowledgeDocument privateDocument = knowledgeBaseService.indexParsedFile(
                8L, "private-conversation", "private.txt", "CHAT",
                "金丝雀发布先把新版本交给少量流量，用指标验证风险。".repeat(12));
        KnowledgeDocument userDocument = knowledgeBaseService.indexParsedFile(
                8L, "private-conversation", "result.txt", "SUBMISSION",
                "持续集成要求频繁合并代码，并让自动测试尽早反馈。".repeat(12));
        KnowledgeDocument globalDocument = knowledgeBaseService.indexGlobalSeed(
                "全局测试知识", "https://example.test/source", "原创测试内容",
                "软件测试需要覆盖正常路径、边界条件和异常路径。".repeat(12));

        assertThat(knowledgeBaseService.search(8L, "another-conversation", "金丝雀发布", 5))
                .extracting(KnowledgeBaseService.SearchResult::documentId)
                .doesNotContain(privateDocument.getId());
        assertThat(knowledgeBaseService.search(8L, "another-conversation", "持续集成", 5))
                .extracting(KnowledgeBaseService.SearchResult::documentId)
                .contains(userDocument.getId());
        assertThat(knowledgeBaseService.search(99L, "unrelated", "软件测试边界条件", 5))
                .extracting(KnowledgeBaseService.SearchResult::documentId)
                .contains(globalDocument.getId());
    }

    @Test
    void onlyOwnerCanDeleteNonGlobalDocument() {
        KnowledgeDocument document = knowledgeBaseService.indexParsedFile(
                10L, "conversation-delete", "delete-me.txt", "KNOWLEDGE",
                "可删除的用户知识内容。".repeat(15));

        assertThat(knowledgeBaseService.deleteOwnedDocument(11L, document.getId())).isFalse();
        assertThat(knowledgeBaseService.deleteOwnedDocument(10L, document.getId())).isTrue();
        assertThat(documentRepository.findById(document.getId())).isEmpty();
        assertThat(chunkRepository.findAll())
                .noneSatisfy(chunk -> assertThat(chunk.getDocumentId()).isEqualTo(document.getId()));
    }

    @Test
    void replacesBuiltinCourseSeedsAndRemovesStaleIndexes() {
        List<KnowledgeDocument> firstVersion = knowledgeBaseService.replaceGlobalSeeds(List.of(
                seed("课程种子：课程A", "课程A第一版，讲解基础语法和工程实践。".repeat(20)),
                seed("课程种子：课程B", "课程B内容，讲解数据模型和查询方法。".repeat(20))));
        Long oldCourseAId = firstVersion.get(0).getId();
        Long oldCourseBId = firstVersion.get(1).getId();

        List<KnowledgeDocument> secondVersion = knowledgeBaseService.replaceGlobalSeeds(List.of(
                seed("课程种子：课程A", "课程A第二版，新增内存管理和测试实践。".repeat(20)),
                seed("课程种子：课程C", "课程C内容，讲解网络协议和排障方法。".repeat(20))));

        assertThat(secondVersion).extracting(KnowledgeDocument::getTitle)
                .containsExactly("课程种子：课程A", "课程种子：课程C");
        assertThat(secondVersion.get(0).getId()).isNotEqualTo(oldCourseAId);
        assertThat(documentRepository.findById(oldCourseAId)).isEmpty();
        assertThat(documentRepository.findById(oldCourseBId)).isEmpty();
        assertThat(chunkRepository.findAll())
                .noneMatch(chunk -> chunk.getDocumentId().equals(oldCourseAId)
                        || chunk.getDocumentId().equals(oldCourseBId));
    }

    private KnowledgeBaseService.SeedDocument seed(String title, String content) {
        return new KnowledgeBaseService.SeedDocument(
                title, "classpath://test/" + title, "测试原创内容", content);
    }
}
