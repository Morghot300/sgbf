package com.snef.sgbf.notification.mapper;

import com.snef.sgbf.notification.dto.NotificationDto;
import com.snef.sgbf.notification.entity.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Conversion {@link Notification} vers {@link NotificationDto}. */
@Mapper(componentModel = "spring")
public interface NotificationMapper {

    @Mapping(target = "declencheParIdentifiant", source = "declenchePar.identifiant")
    NotificationDto toDto(Notification notification);
}
