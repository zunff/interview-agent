package com.zunff.interview.model.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 追问决策结果业务对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpDecisionBO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 路由决策: followUp / deepDive / challengeMode / nextQuestion */
    private String decision;

    /** 追问的具体问题 */
    private String followUpQuestion;

    /** 决策理由 */
    private String reason;

    /** 追问类型：深度挖掘、验证澄清、补充细节、探索异常 */
    private String followUpType;
}
