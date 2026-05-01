package com.zunff.interview.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionListItemResponse {
    private String sessionId;
    private boolean hasResume;
    private String status;
    private String createTime;
}
