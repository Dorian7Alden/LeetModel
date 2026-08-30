package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AI 供应商实时模型目录条目。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiProviderModelDTO {
    private String id;
    private String provider;
    private String ownedBy;
}
