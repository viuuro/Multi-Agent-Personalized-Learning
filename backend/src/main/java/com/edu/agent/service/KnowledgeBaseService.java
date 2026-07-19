package com.edu.agent.service;

import com.edu.agent.model.KnowledgeChunk;
import com.edu.agent.model.KnowledgeDocument;
import com.edu.agent.repository.KnowledgeChunkRepository;
import com.edu.agent.repository.KnowledgeDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 知识库核心服务：文档去重、结构化分块、作用域隔离与检索上下文组装。
 * MySQL 使用 ngram FULLTEXT；H2 等环境自动使用可移植的中英文词元评分。
 */
@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);
    private static final int MAX_DOCUMENT_CHARS = 500_000;
    private static final int MAX_CHUNK_CHARS = 900;
    private static final int CHUNK_OVERLAP_CHARS = 80;
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern LATIN_TOKEN_PATTERN = Pattern.compile("[a-z0-9_+#.-]{2,}");
    private static final Pattern HAN_PATTERN = Pattern.compile("\\p{IsHan}+");

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final boolean mysqlFulltextEnabled;
    private volatile Boolean mysqlDatabase;

    public KnowledgeBaseService(KnowledgeDocumentRepository documentRepository,
                                KnowledgeChunkRepository chunkRepository,
                                JdbcTemplate jdbcTemplate,
                                DataSource dataSource,
                                @Value("${knowledge.search.mysql-fulltext-enabled:true}")
                                boolean mysqlFulltextEnabled) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.mysqlFulltextEnabled = mysqlFulltextEnabled;
    }

    /** 在 Hibernate 建表完成后，为 MySQL 创建支持中文的全文索引。 */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeSearchIndex() {
        if (!mysqlFulltextEnabled || !isMySql()) return;
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                      AND table_name = 'knowledge_chunk'
                      AND index_name = 'ft_knowledge_chunk'
                    """, Integer.class);
            if (count == null || count == 0) {
                jdbcTemplate.execute("""
                        CREATE FULLTEXT INDEX ft_knowledge_chunk
                        ON knowledge_chunk (heading, content) WITH PARSER ngram
                        """);
                log.info("知识库 MySQL ngram FULLTEXT 索引创建完成");
            }
        } catch (Exception exception) {
            log.warn("无法创建知识库 FULLTEXT 索引，将使用可移植检索: {}", exception.getMessage());
        }
    }

    @Transactional
    public KnowledgeDocument indexParsedFile(Long userId,
                                             String conversationId,
                                             String fileName,
                                             String purpose,
                                             String content) {
        String normalizedPurpose = purpose == null ? "CHAT" : purpose.trim().toUpperCase(Locale.ROOT);
        String scope = switch (normalizedPurpose) {
            case "SUBMISSION", "KNOWLEDGE" -> KnowledgeDocument.SCOPE_USER;
            default -> KnowledgeDocument.SCOPE_CONVERSATION;
        };
        String safeConversationId = KnowledgeDocument.SCOPE_CONVERSATION.equals(scope)
                ? trimToNull(conversationId) : null;
        return indexDocument(userId, safeConversationId, scope, "UPLOAD", fileName,
                "upload://" + safeTitle(fileName), "用户上传资料", content);
    }

    @Transactional
    public KnowledgeDocument indexGlobalSeed(String title,
                                             String sourceUri,
                                             String license,
                                             String content) {
        return indexDocument(null, null, KnowledgeDocument.SCOPE_GLOBAL, "BUILTIN",
                title, sourceUri, license, content);
    }

    /**
     * 原子替换内置课程种子集合。内容未变化的文档复用原索引；更新或移除的旧种子会被清理，
     * 避免应用升级后同一课程的新旧版本同时参与检索。
     */
    @Transactional
    public List<KnowledgeDocument> replaceGlobalSeeds(List<SeedDocument> seeds) {
        return replaceGlobalSeeds("BUILTIN", seeds);
    }

    /** 按来源类型独立替换全局种子，避免不同课程包相互删除。 */
    @Transactional
    public List<KnowledgeDocument> replaceGlobalSeeds(String sourceType, List<SeedDocument> seeds) {
        if (seeds == null || seeds.isEmpty()) {
            throw new IllegalArgumentException("课程种子集合不能为空");
        }
        String safeSourceType = sourceType == null ? "BUILTIN"
                : sourceType.trim().toUpperCase(Locale.ROOT);
        if (!safeSourceType.matches("[A-Z0-9_]{2,30}")) {
            throw new IllegalArgumentException("课程种子来源类型不合法");
        }

        List<KnowledgeDocument> previousSeeds = documentRepository
                .findByScopeAndSourceTypeOrderByIdDesc(KnowledgeDocument.SCOPE_GLOBAL, safeSourceType);
        List<KnowledgeDocument> retainedSeeds = new ArrayList<>(seeds.size());
        Set<Long> retainedIds = new LinkedHashSet<>();

        for (SeedDocument seed : seeds) {
            KnowledgeDocument indexed = indexDocument(null, null, KnowledgeDocument.SCOPE_GLOBAL,
                    safeSourceType, seed.title(), seed.sourceUri(), seed.license(), seed.content());
            retainedSeeds.add(indexed);
            retainedIds.add(indexed.getId());
        }

        for (KnowledgeDocument previous : previousSeeds) {
            if (!retainedIds.contains(previous.getId())) {
                chunkRepository.deleteByDocumentId(previous.getId());
                documentRepository.delete(previous);
            }
        }
        return retainedSeeds;
    }

    private KnowledgeDocument indexDocument(Long userId,
                                            String conversationId,
                                            String scope,
                                            String sourceType,
                                            String title,
                                            String sourceUri,
                                            String license,
                                            String rawContent) {
        String content = normalizeContent(rawContent);
        if (content.isBlank()) throw new IllegalArgumentException("知识文档正文不能为空");
        if (content.length() > MAX_DOCUMENT_CHARS) {
            content = content.substring(0, MAX_DOCUMENT_CHARS);
        }
        String checksum = sha256(content);
        List<KnowledgeDocument> duplicates = documentRepository.findDuplicates(
                userId, conversationId, scope, checksum, PageRequest.of(0, 1));
        if (!duplicates.isEmpty()) return duplicates.get(0);

        List<ChunkDraft> drafts = splitIntoChunks(content);
        KnowledgeDocument document = new KnowledgeDocument();
        document.setUserId(userId);
        document.setConversationId(conversationId);
        document.setScope(scope);
        document.setSourceType(sourceType);
        document.setTitle(safeTitle(title));
        document.setSourceUri(trimToNull(sourceUri));
        document.setLicense(trimToNull(license));
        document.setChecksum(checksum);
        document.setStatus("INDEXING");
        document.setCharacterCount(content.length());
        document.setChunkCount(drafts.size());
        document = documentRepository.saveAndFlush(document);

        List<KnowledgeChunk> chunks = new ArrayList<>(drafts.size());
        for (int index = 0; index < drafts.size(); index++) {
            ChunkDraft draft = drafts.get(index);
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setDocumentId(document.getId());
            chunk.setUserId(userId);
            chunk.setConversationId(conversationId);
            chunk.setScope(scope);
            chunk.setChunkIndex(index);
            chunk.setHeading(draft.heading());
            chunk.setContent(draft.content());
            chunk.setDocumentTitle(document.getTitle());
            chunk.setSourceUri(document.getSourceUri());
            chunk.setLicense(document.getLicense());
            chunks.add(chunk);
        }
        chunkRepository.saveAll(chunks);
        document.setStatus("READY");
        KnowledgeDocument saved = documentRepository.save(document);
        log.info("知识文档已索引: id={}, scope={}, title={}, chunks={}",
                saved.getId(), scope, saved.getTitle(), chunks.size());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<SearchResult> search(Long userId, String conversationId, String query, int requestedLimit) {
        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.isBlank()) return List.of();
        if (safeQuery.length() > 600) safeQuery = safeQuery.substring(0, 600);
        safeQuery = expandCourseQuery(safeQuery);
        int limit = Math.max(1, Math.min(requestedLimit, 12));

        if (mysqlFulltextEnabled && isMySql()) {
            try {
                List<SearchResult> results = searchMySql(userId, trimToNull(conversationId), safeQuery, limit);
                if (!results.isEmpty()) return results;
            } catch (Exception exception) {
                log.warn("知识库 FULLTEXT 查询失败，使用可移植检索: {}", exception.getMessage());
            }
        }
        return searchPortable(userId, trimToNull(conversationId), safeQuery, limit);
    }

    @Transactional(readOnly = true)
    public String buildContext(Long userId, String conversationId, String query, int limit) {
        List<SearchResult> results = search(userId, conversationId, query, limit);
        if (results.isEmpty()) return "";
        StringBuilder context = new StringBuilder();
        for (int index = 0; index < results.size(); index++) {
            SearchResult result = results.get(index);
            context.append("[资料").append(index + 1).append("] 《")
                    .append(result.documentTitle()).append("》");
            if (result.heading() != null && !result.heading().isBlank()) {
                context.append(" / ").append(result.heading());
            }
            context.append('\n').append(result.content().trim()).append('\n');
            if (result.sourceUri() != null && !result.sourceUri().isBlank()) {
                context.append("来源：").append(result.sourceUri()).append('\n');
            }
            context.append('\n');
        }
        return context.toString().trim();
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDocument> listAccessible(Long userId, String conversationId, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 200));
        return documentRepository.findAccessible(userId, trimToNull(conversationId), PageRequest.of(0, limit));
    }

    @Transactional
    public boolean deleteOwnedDocument(Long userId, Long documentId) {
        Optional<KnowledgeDocument> document = documentRepository.findByIdAndUserId(documentId, userId);
        if (document.isEmpty() || KnowledgeDocument.SCOPE_GLOBAL.equals(document.get().getScope())) return false;
        chunkRepository.deleteByDocumentId(documentId);
        documentRepository.delete(document.get());
        return true;
    }

    private List<SearchResult> searchMySql(Long userId, String conversationId, String query, int limit) {
        String sql = """
                SELECT k.id, k.document_id, k.document_title, k.heading, k.content,
                       k.source_uri, k.license, k.scope,
                       MATCH(k.heading, k.content) AGAINST (? IN NATURAL LANGUAGE MODE) AS relevance
                FROM knowledge_chunk k
                WHERE (k.scope = 'GLOBAL'
                    OR (k.user_id = ? AND (k.scope <> 'CONVERSATION' OR k.conversation_id = ?)))
                  AND MATCH(k.heading, k.content) AGAINST (? IN NATURAL LANGUAGE MODE)
                ORDER BY relevance DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, (ResultSet rs, int rowNum) -> new SearchResult(
                        rs.getLong("id"),
                        rs.getLong("document_id"),
                        rs.getString("document_title"),
                        rs.getString("heading"),
                        rs.getString("content"),
                        rs.getString("source_uri"),
                        rs.getString("license"),
                        rs.getString("scope"),
                        rs.getDouble("relevance")),
                query, userId, conversationId, query, limit);
    }

    private List<SearchResult> searchPortable(Long userId, String conversationId, String query, int limit) {
        Set<String> tokens = tokenize(query);
        if (tokens.isEmpty()) return List.of();
        return chunkRepository.findAccessible(userId, conversationId, PageRequest.of(0, 2000))
                .stream()
                .map(chunk -> new ScoredChunk(chunk, score(chunk, tokens)))
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed()
                        .thenComparing(item -> item.chunk().getId(), Comparator.reverseOrder()))
                .limit(limit)
                .map(item -> toResult(item.chunk(), item.score()))
                .toList();
    }

    private double score(KnowledgeChunk chunk, Set<String> tokens) {
        String documentTitle = Optional.ofNullable(chunk.getDocumentTitle()).orElse("")
                .toLowerCase(Locale.ROOT);
        String heading = Optional.ofNullable(chunk.getHeading()).orElse("").toLowerCase(Locale.ROOT);
        String content = chunk.getContent().toLowerCase(Locale.ROOT);
        double score = 0;
        for (String token : tokens) {
            score += countOccurrences(documentTitle, token) * 6.0;
            score += countOccurrences(heading, token) * 4.0;
            score += Math.min(4, countOccurrences(content, token));
        }
        if (KnowledgeDocument.SCOPE_CONVERSATION.equals(chunk.getScope())) score += 0.15;
        if (KnowledgeDocument.SCOPE_USER.equals(chunk.getScope())) score += 0.08;
        return score;
    }

    private Set<String> tokenize(String query) {
        String normalized = query.toLowerCase(Locale.ROOT);
        Set<String> tokens = new LinkedHashSet<>();
        Matcher latinMatcher = LATIN_TOKEN_PATTERN.matcher(normalized);
        while (latinMatcher.find()) tokens.add(latinMatcher.group());
        Matcher hanMatcher = HAN_PATTERN.matcher(normalized);
        while (hanMatcher.find()) {
            String segment = hanMatcher.group();
            if (segment.length() <= 8) tokens.add(segment);
            if (segment.length() == 1) tokens.add(segment);
            for (int index = 0; index < segment.length() - 1; index++) {
                tokens.add(segment.substring(index, index + 2));
            }
        }
        return tokens;
    }

    private List<ChunkDraft> splitIntoChunks(String content) {
        List<Section> sections = new ArrayList<>();
        String currentHeading = "正文";
        List<String> headingPath = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        for (String line : content.split("\\n")) {
            Matcher headingMatcher = HEADING_PATTERN.matcher(line.trim());
            if (headingMatcher.matches()) {
                flushSection(sections, currentHeading, body);
                int level = headingMatcher.group(1).length();
                String heading = headingMatcher.group(2).trim();
                while (headingPath.size() >= level) headingPath.remove(headingPath.size() - 1);
                while (headingPath.size() < level - 1) headingPath.add("");
                headingPath.add(heading);
                currentHeading = headingPath.stream().filter(value -> !value.isBlank())
                        .reduce((left, right) -> left + " > " + right).orElse(heading);
            } else {
                if (!body.isEmpty()) body.append('\n');
                body.append(line.trim());
            }
        }
        flushSection(sections, currentHeading, body);

        List<ChunkDraft> chunks = new ArrayList<>();
        for (Section section : sections) {
            splitSection(section, chunks);
        }
        if (chunks.isEmpty()) chunks.add(new ChunkDraft("正文", content));
        return chunks;
    }

    private String expandCourseQuery(String query) {
        String lowered = query.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        if (containsAny(lowered, "数据结构", "链表", "线性表", "栈", "队列", "二叉树", "并查集",
                "排序", "查找", "散列", "哈希", "最短路径", "拓扑排序", "最小生成树")) {
            aliases.add("数据结构");
            aliases.add("算法");
        }
        if (containsAny(lowered, "计组", "计算机组成", "组成原理", "cache", "缓存", "补码", "浮点数",
                "指令系统", "数据通路", "流水线", "控制信号", "中断", "dma", "总线", "存储器")) {
            aliases.add("计算机组成原理");
            aliases.add("计组");
        }
        if (lowered.contains("cache")) {
            aliases.add("高速缓存");
            aliases.add("地址映射");
        }
        if (lowered.contains("哈希")) aliases.add("散列表");
        if (lowered.contains("散列")) aliases.add("哈希");
        if (aliases.isEmpty()) return query;
        return (query + " " + String.join(" ", aliases)).trim();
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) return true;
        }
        return false;
    }

    private void flushSection(List<Section> sections, String heading, StringBuilder body) {
        String text = body.toString().trim();
        if (!text.isBlank()) sections.add(new Section(heading, text));
        body.setLength(0);
    }

    private void splitSection(Section section, List<ChunkDraft> chunks) {
        String text = section.content();
        int start = 0;
        while (start < text.length()) {
            int hardEnd = Math.min(text.length(), start + MAX_CHUNK_CHARS);
            int end = hardEnd;
            if (hardEnd < text.length()) {
                int preferred = findBreak(text, start + 450, hardEnd);
                if (preferred > start) end = preferred;
            }
            String piece = text.substring(start, end).trim();
            if (!piece.isBlank()) chunks.add(new ChunkDraft(section.heading(), piece));
            if (end >= text.length()) break;
            int next = Math.max(start + 1, end - CHUNK_OVERLAP_CHARS);
            start = next;
        }
    }

    private int findBreak(String text, int from, int to) {
        int safeFrom = Math.max(0, Math.min(from, text.length()));
        int safeTo = Math.max(safeFrom, Math.min(to, text.length()));
        String punctuation = "。！？；\\n.!?;";
        for (int index = safeTo - 1; index >= safeFrom; index--) {
            if (punctuation.indexOf(text.charAt(index)) >= 0) return index + 1;
        }
        return safeTo;
    }

    private String normalizeContent(String content) {
        if (content == null) return "";
        return content.replace("\u0000", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private boolean isMySql() {
        Boolean cached = mysqlDatabase;
        if (cached != null) return cached;
        try (var connection = dataSource.getConnection()) {
            cached = connection.getMetaData().getDatabaseProductName()
                    .toLowerCase(Locale.ROOT).contains("mysql");
        } catch (Exception exception) {
            cached = false;
        }
        mysqlDatabase = cached;
        return cached;
    }

    private SearchResult toResult(KnowledgeChunk chunk, double score) {
        return new SearchResult(chunk.getId(), chunk.getDocumentId(), chunk.getDocumentTitle(),
                chunk.getHeading(), chunk.getContent(), chunk.getSourceUri(), chunk.getLicense(),
                chunk.getScope(), score);
    }

    private int countOccurrences(String value, String token) {
        if (token.isBlank()) return 0;
        int count = 0;
        int from = 0;
        while ((from = value.indexOf(token, from)) >= 0) {
            count++;
            from += Math.max(1, token.length());
        }
        return count;
    }

    private String safeTitle(String title) {
        String value = title == null || title.isBlank() ? "未命名资料" : title.trim();
        return value.length() > 255 ? value.substring(0, 255) : value;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算知识文档摘要", exception);
        }
    }

    public record SearchResult(Long chunkId,
                               Long documentId,
                               String documentTitle,
                               String heading,
                               String content,
                               String sourceUri,
                               String license,
                               String scope,
                               double score) {}

    public record SeedDocument(String title,
                               String sourceUri,
                               String license,
                               String content) {}

    private record Section(String heading, String content) {}
    private record ChunkDraft(String heading, String content) {}
    private record ScoredChunk(KnowledgeChunk chunk, double score) {}
}
