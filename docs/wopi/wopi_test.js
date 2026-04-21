// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

const WOPI_CLIENT_BASE_URL = "wopiClientBaseUrl";
const WOPI_HOST_BASE_URL = "wopiHostBaseUrl";
const FILE_ID = "fileId";
const WOPI_CLIENT_HASH = "wopiClientHash";
const WOPI_PATH = "/wopi/api/v1/files";

// Restore saved values from localStorage on load
(function restore() {
  const fields = {
    wopiClientBaseUrl: WOPI_CLIENT_BASE_URL,
    wopiHostBaseUrl: WOPI_HOST_BASE_URL,
    fileId: FILE_ID,
    wopiClientHash: WOPI_CLIENT_HASH,
  };
  for (const [id, key] of Object.entries(fields)) {
    const saved = localStorage.getItem(key);
    if (saved) document.getElementById(id).value = saved;
  }
})();

function openWopi() {
  const errorEl = document.getElementById("error");
  errorEl.textContent = "";

  const clientBase = document.getElementById("wopiClientBaseUrl").value.trim().replace(/\/$/, "");
  const hostBase = document.getElementById("wopiHostBaseUrl").value.trim().replace(/\/$/, "");
  const fileId = document.getElementById("fileId").value.trim();
  const accessToken = document.getElementById("accessToken").value.trim();
  const clientHash = document.getElementById("wopiClientHash").value.trim();

  if (!clientBase || !hostBase || !fileId) {
    errorEl.textContent = "Please fill in all required fields.";
    return;
  }

  // Persist values for convenience
  localStorage.setItem(WOPI_CLIENT_BASE_URL, clientBase);
  localStorage.setItem(WOPI_HOST_BASE_URL, hostBase);
  localStorage.setItem(FILE_ID, fileId);
  localStorage.setItem(WOPI_CLIENT_HASH, clientHash);

  const wopiSrc = encodeURIComponent(`${hostBase}${WOPI_PATH}/${fileId}`);
  // The /browser/{hash}/cool.html path is Collabora-specific.
  // For other WOPI clients, adjust the action URL format accordingly.
  const hashSegment = clientHash ? `${clientHash}/` : "";
  const actionUrl = `${clientBase}/browser/${hashSegment}cool.html?WOPISrc=${wopiSrc}`;

  const form = document.createElement("form");
  form.method = "POST";
  form.action = actionUrl;
  form.enctype = "multipart/form-data";
  form.target = "_blank";

  const tokenInput = document.createElement("input");
  tokenInput.type = "hidden";
  tokenInput.name = "access_token";
  tokenInput.value = accessToken;
  form.appendChild(tokenInput);

  document.body.appendChild(form);
  form.submit();
  document.body.removeChild(form);
}
