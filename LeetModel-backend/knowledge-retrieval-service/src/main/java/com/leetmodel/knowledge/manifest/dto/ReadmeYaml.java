package com.leetmodel.knowledge.manifest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 根目录或各级知识目录自描述 README.yaml 根映射实体。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReadmeYaml {

    @JsonProperty("schema_version")
    private String schemaVersion;

    @JsonProperty("directory_name")
    private String directoryName;

    @JsonProperty("path")
    private String path;

    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

    @JsonProperty("tags")
    private DirectoryTagsYaml tags = new DirectoryTagsYaml();

    @JsonProperty("documents")
    private List<DocumentYaml> documents = new ArrayList<>();
}
