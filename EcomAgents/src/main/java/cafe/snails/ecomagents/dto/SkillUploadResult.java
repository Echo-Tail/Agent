package cafe.snails.ecomagents.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ZIP 技能上传结果 DTO，支持部分成功机制。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillUploadResult {
    /** 成功导入的技能数 */
    private int successCount;
    /** ZIP 中识别到的技能目录总数 */
    private int totalCount;
    /** 成功导入的技能名称列表 */
    private List<String> imported;
    /** 失败的技能名称及原因 */
    private List<FailedItem> failed;

    /**
     * 单个技能导入失败的明细。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FailedItem {
        /** 技能目录名称 */
        private String name;
        /** 失败原因 */
        private String reason;
    }
}
