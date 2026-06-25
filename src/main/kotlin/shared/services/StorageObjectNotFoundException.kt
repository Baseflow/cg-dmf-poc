// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.services

/** Thrown when an object key is absent from the storage backend but was expected to exist. */
class StorageObjectNotFoundException(objectName: String, cause: Throwable? = null) :
    Exception("Storage object not found: $objectName", cause)
