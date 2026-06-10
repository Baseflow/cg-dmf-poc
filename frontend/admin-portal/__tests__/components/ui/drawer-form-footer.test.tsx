// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { describe, expect, it, vi } from "vitest"
import { DrawerFormFooter } from "@/components/ui/drawer-form-footer"

describe("DrawerFormFooter", () => {
  describe("read-only mode", () => {
    it("shows only Sluiten", () => {
      render(
        <DrawerFormFooter
          readOnly
          saving={false}
          formId="test"
          onCancel={vi.fn()}
        />
      )
      expect(screen.getByRole("button", { name: "Sluiten" })).toBeInTheDocument()
      expect(
        screen.queryByRole("button", { name: /opslaan/i })
      ).not.toBeInTheDocument()
      expect(
        screen.queryByRole("button", { name: /annuleren/i })
      ).not.toBeInTheDocument()
    })

    it("calls onCancel when Sluiten is clicked", async () => {
      const user = userEvent.setup()
      const onCancel = vi.fn()
      render(
        <DrawerFormFooter
          readOnly
          saving={false}
          formId="test"
          onCancel={onCancel}
        />
      )
      await user.click(screen.getByRole("button", { name: "Sluiten" }))
      expect(onCancel).toHaveBeenCalledOnce()
    })
  })

  describe("write mode", () => {
    it("shows Opslaan and Annuleren, not Sluiten", () => {
      render(
        <DrawerFormFooter
          readOnly={false}
          saving={false}
          formId="test"
          onCancel={vi.fn()}
        />
      )
      expect(
        screen.getByRole("button", { name: /opslaan/i })
      ).toBeInTheDocument()
      expect(
        screen.getByRole("button", { name: /annuleren/i })
      ).toBeInTheDocument()
      expect(
        screen.queryByRole("button", { name: "Sluiten" })
      ).not.toBeInTheDocument()
    })

    it("submit button targets the correct form", () => {
      render(
        <DrawerFormFooter
          readOnly={false}
          saving={false}
          formId="my-form"
          onCancel={vi.fn()}
        />
      )
      expect(screen.getByRole("button", { name: /opslaan/i })).toHaveAttribute(
        "form",
        "my-form"
      )
    })

    it("calls onCancel when Annuleren is clicked", async () => {
      const user = userEvent.setup()
      const onCancel = vi.fn()
      render(
        <DrawerFormFooter
          readOnly={false}
          saving={false}
          formId="test"
          onCancel={onCancel}
        />
      )
      await user.click(screen.getByRole("button", { name: /annuleren/i }))
      expect(onCancel).toHaveBeenCalledOnce()
    })

    it("disables both buttons and shows Opslaan... while saving", () => {
      render(
        <DrawerFormFooter
          readOnly={false}
          saving
          formId="test"
          onCancel={vi.fn()}
        />
      )
      expect(screen.getByRole("button", { name: /opslaan\.\.\./i })).toBeDisabled()
      expect(screen.getByRole("button", { name: /annuleren/i })).toBeDisabled()
    })
  })
})