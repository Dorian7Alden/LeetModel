package com.leetmodel.knowledge.archive;

import com.leetmodel.knowledge.archive.vo.KnowledgeTreeNodeVO;
import com.leetmodel.knowledge.manifest.dto.DirectoryTagsYaml;
import com.leetmodel.knowledge.manifest.model.ManifestDirectory;
import com.leetmodel.knowledge.manifest.model.ManifestDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeTreeAssemblerTest {

    private final KnowledgeTreeAssembler assembler = new KnowledgeTreeAssembler();

    @Test
    @DisplayName("按路径补齐虚拟父目录并聚合后代文档数")
    void assemblesNestedDirectoriesAndAggregatesDocuments() {
        ManifestDirectory review = directory(
                "评审板块",
                "数学建模/论文评审/评审板块",
                "评审要点",
                List.of(document("摘要.md"), document("模型.md")));
        ManifestDirectory methods = directory(
                "模型方法",
                "数学建模/模型方法",
                "模型方法库",
                List.of(document("优化.md")));

        List<KnowledgeTreeNodeVO> roots = assembler.assemble(List.of(review, methods));

        assertThat(roots).singleElement().satisfies(root -> {
            assertThat(root.getName()).isEqualTo("数学建模");
            assertThat(root.isVirtual()).isTrue();
            assertThat(root.getDirectDocumentCount()).isZero();
            assertThat(root.getDocumentCount()).isEqualTo(3);
            assertThat(root.getChildren()).extracting(KnowledgeTreeNodeVO::getName)
                    .containsExactly("模型方法", "论文评审");
        });

        KnowledgeTreeNodeVO reviewParent = roots.get(0).getChildren().get(1);
        assertThat(reviewParent.isVirtual()).isTrue();
        assertThat(reviewParent.getDocumentCount()).isEqualTo(2);
        assertThat(reviewParent.getChildren()).singleElement().satisfies(node -> {
            assertThat(node.getPath()).isEqualTo("数学建模/论文评审/评审板块");
            assertThat(node.isVirtual()).isFalse();
            assertThat(node.getDirectDocumentCount()).isEqualTo(2);
            assertThat(node.getDocumentCount()).isEqualTo(2);
            assertThat(node.getDocuments()).extracting(KnowledgeTreeNodeVO.KnowledgeDocumentItemVO::getFile)
                    .containsExactly("摘要.md", "模型.md");
        });
    }

    @Test
    @DisplayName("无序输入与不同父目录下的同名节点仍按完整路径稳定归属")
    void keepsRepeatedNamesInTheirOwnBranches() {
        ManifestDirectory second = directory(
                "公共方法",
                "B/公共方法",
                "B 方法",
                List.of(document("b.md")));
        ManifestDirectory first = directory(
                "公共方法",
                "A/公共方法",
                "A 方法",
                List.of(document("a.md")));

        List<KnowledgeTreeNodeVO> roots = assembler.assemble(List.of(second, first));

        assertThat(roots).extracting(KnowledgeTreeNodeVO::getPath).containsExactly("A", "B");
        assertThat(roots.get(0).getChildren()).singleElement()
                .extracting(KnowledgeTreeNodeVO::getPath)
                .isEqualTo("A/公共方法");
        assertThat(roots.get(1).getChildren()).singleElement()
                .extracting(KnowledgeTreeNodeVO::getPath)
                .isEqualTo("B/公共方法");
    }

    @Test
    @DisplayName("空目录集合返回空树")
    void returnsEmptyTreeForEmptyInput() {
        assertThat(assembler.assemble(List.of())).isEmpty();
        assertThat(assembler.assemble(null)).isEmpty();
    }

    private ManifestDirectory directory(
            String name,
            String path,
            String title,
            List<ManifestDocument> documents) {
        DirectoryTagsYaml tags = new DirectoryTagsYaml();
        tags.setMethods(List.of("分类"));
        return new ManifestDirectory(name, path, title, title + "说明", tags, documents);
    }

    private ManifestDocument document(String file) {
        return new ManifestDocument(
                "目录/" + file,
                file,
                file + "标题",
                file + "摘要",
                List.of("标签"),
                List.of("方法"),
                List.of("标签", "方法"),
                "L3",
                120,
                "目录",
                "目录");
    }
}
