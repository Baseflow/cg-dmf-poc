// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.wopi

// WOPI (Web Application Open Platform Interface) API
//
// This package will implement the WOPI protocol to support browser-based
// document editing via compatible editors (e.g., Collabora Online, OnlyOffice).
//
// Planned endpoints (per WOPI spec):
//   GET  /wopi/files/{fileId}           — CheckFileInfo
//   GET  /wopi/files/{fileId}/contents  — GetFile
//   POST /wopi/files/{fileId}/contents  — PutFile
//   POST /wopi/files/{fileId}           — WOPI actions (lock, unlock, rename, etc.)
//
// Not yet implemented.

