import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore, Firestore } from "firebase-admin/firestore";

if (getApps().length === 0) initializeApp();

export const db: Firestore = getFirestore();

/** Collection names. The Kotlin client mirrors these in core/FirestorePaths.kt. */
export const C = {
  users: "users",
  events: "events",
  registrations: "registrations",
  registrationKeys: "registrationKeys",
  attendanceSessions: "attendanceSessions",
  eventOrganizers: "eventOrganizers",
  notificationJobs: "notificationJobs",
  auditLogs: "auditLogs",
  ticketAccessRequests: "ticketAccessRequests",
  settings: "settings",
} as const;

export const CLUB_SETTINGS_DOC = "club";
