package com.campus.server.service;

import com.campus.server.common.BizException;
import com.campus.server.dto.Dtos;
import com.campus.server.entity.UserNotificationEntity;
import com.campus.server.mapper.WellbeingMapper;
import com.campus.server.security.CurrentUser;
import com.campus.server.support.Ids;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 站内通知。
 *
 * <p>以前前端的通知是两条写死假数据、已读状态塞 localStorage。现在落库：
 * 组织者收到报名、活动到期等都往 user_notifications 写一条。
 * 通知内容里永远不出现参与者的身份信息。
 */
@Service
public class NotificationService {

    private final WellbeingMapper wellbeingMapper;

    public NotificationService(WellbeingMapper wellbeingMapper) {
        this.wellbeingMapper = wellbeingMapper;
    }

    public List<Dtos.NotificationDto> list() {
        String userId = CurrentUser.require().userId();
        return wellbeingMapper.listByUser(userId).stream()
                .map(this::toDto)
                .toList();
    }

    public long unreadCount() {
        String userId = CurrentUser.require().userId();
        return wellbeingMapper.listByUser(userId).stream()
                .filter(n -> n.getReadAt() == null)
                .count();
    }

    public void markRead(String id) {
        String userId = CurrentUser.require().userId();
        int updated = wellbeingMapper.markRead(id, userId);
        if (updated == 0) {
            // 已读过的也算成功（幂等），只有"根本不是这个人的通知"才报错
            UserNotificationEntity existing = wellbeingMapper.listByUser(userId).stream()
                    .filter(n -> n.getId().equals(id))
                    .findFirst().orElse(null);
            if (existing == null) {
                throw BizException.notFound("通知不存在");
            }
        }
    }

    public void markAllRead() {
        wellbeingMapper.markAllRead(CurrentUser.require().userId());
    }

    /** 供其他 service 落一条通知。 */
    public void push(String userId, String type, String title, String detail, String targetPath) {
        UserNotificationEntity n = new UserNotificationEntity();
        n.setId(Ids.uuid());
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setDetail(detail);
        n.setTargetPath(targetPath);
        n.setDedupeKey("manual:" + Ids.uuid());
        wellbeingMapper.insertNotification(n);
    }

    private Dtos.NotificationDto toDto(UserNotificationEntity n) {
        return new Dtos.NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getDetail(),
                n.getTargetPath(), n.getReadAt() != null, Ids.iso(n.getCreatedAt()));
    }
}
