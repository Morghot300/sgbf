import { httpClient } from "./httpClient";
import type { NotificationDto } from "../types/notification";

/** Liste des notifications du compte connecte, plus recentes en premier - jamais celles d'un autre utilisateur (voir NotificationController, pas de parametre id). */
export async function listerNotifications(): Promise<NotificationDto[]> {
  return (await httpClient.get<NotificationDto[]>("/notifications")).data;
}

export async function compterNotificationsNonLues(): Promise<number> {
  return (await httpClient.get<{ nombre: number }>("/notifications/non-lues/compte")).data.nombre;
}

export async function marquerNotificationCommeLue(id: number): Promise<NotificationDto> {
  return (await httpClient.put<NotificationDto>(`/notifications/${id}/lue`)).data;
}
