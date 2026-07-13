package my.hive_back.module.label.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Variables supported by the shared label template designer.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LabelTemplateVariableVO {

    private String label;

    private String field;

    private String type;

    private String sampleValue;
}
