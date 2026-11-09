package com.campus.server.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表 referral_requests。参与者「希望有人联系我」时生成的待办工单。
 *
 * <p>这张表存在的意义：匿名提交之后，组织者既不知道是谁、也不知道说了什么，
 * 但仍需要知道「有一个人需要被联系」。所以它只存三个事实——需要联系、联系方式（可选）、
 * 什么时候提交的——不存任何报名答案。
 */
@Data
public class ReferralRequestEntity {
    private String id;
    private String projectId;
    /** 参与者本人（登录时才有）。组织者接口从不返回它。 */
    private String userId;
    private String contact;
    private String preferredTime;
    /** pending | contacted | closed */
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;
}
