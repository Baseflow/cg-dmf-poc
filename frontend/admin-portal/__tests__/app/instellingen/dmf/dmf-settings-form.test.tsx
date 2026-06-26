import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { beforeEach, describe, expect, it, vi } from "vitest"
import DmfSettingsForm from "@/app/(main)/instellingen/dmf/dmf-settings-form"

vi.mock("@/app/(main)/instellingen/dmf/actions", () => ({
  upsertDmfSetting: vi.fn(),
}))

import { upsertDmfSetting } from "@/app/(main)/instellingen/dmf/actions"

const defaultSettings = [
  {
    key: "trigger_size_bytes",
    type: "int",
    value: "4294967296",
    updatedAt: "2026-01-01",
  },
  {
    key: "chunk_size_bytes",
    type: "int",
    value: "3221225472",
    updatedAt: "2026-01-01",
  },
  {
    key: "validation_enabled",
    type: "boolean",
    value: "true",
    updatedAt: "2026-01-01",
  },
]

describe("DmfSettingsForm", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(upsertDmfSetting).mockResolvedValue(undefined)
  })

  it("renders with initial values from settings", () => {
    render(<DmfSettingsForm settings={defaultSettings} />)
    expect(screen.getByDisplayValue("4096")).toBeInTheDocument()
    expect(screen.getByDisplayValue("3072")).toBeInTheDocument()
    expect(screen.getByRole("checkbox")).toBeChecked()
  })

  it("renders the save button", () => {
    render(<DmfSettingsForm settings={defaultSettings} />)
    expect(screen.getByRole("button", { name: /opslaan/i })).toBeInTheDocument()
  })

  it("calls upsertDmfSetting for all three keys on submit", async () => {
    render(<DmfSettingsForm settings={defaultSettings} />)

    fireEvent.submit(
      screen.getByRole("button", { name: /opslaan/i }).closest("form")!
    )

    await waitFor(() => {
      expect(upsertDmfSetting).toHaveBeenCalledTimes(3)
    })
    expect(upsertDmfSetting).toHaveBeenCalledWith(
      "trigger_size_bytes",
      "4294967296"
    )
    expect(upsertDmfSetting).toHaveBeenCalledWith(
      "chunk_size_bytes",
      "3221225472"
    )
    expect(upsertDmfSetting).toHaveBeenCalledWith("validation_enabled", "true")
  })

  it("shows success message after saving", async () => {
    render(<DmfSettingsForm settings={defaultSettings} />)

    fireEvent.submit(
      screen.getByRole("button", { name: /opslaan/i }).closest("form")!
    )

    expect(
      await screen.findByText("Instellingen opgeslagen.")
    ).toBeInTheDocument()
  })

  it("shows error message when save fails", async () => {
    vi.mocked(upsertDmfSetting).mockRejectedValueOnce(new Error("HTTP 500"))

    render(<DmfSettingsForm settings={defaultSettings} />)

    fireEvent.submit(
      screen.getByRole("button", { name: /opslaan/i }).closest("form")!
    )

    expect(await screen.findByText(/opslaan mislukt/i)).toBeInTheDocument()
  })

  it("renders unchecked when validation_enabled is false", () => {
    const settings = defaultSettings.map((s) =>
      s.key === "validation_enabled" ? { ...s, value: "false" } : s
    )
    render(<DmfSettingsForm settings={settings} />)
    expect(screen.getByRole("checkbox")).not.toBeChecked()
  })

  // -----------------------------------------------------------------------
  // Type serialization / deserialization
  // -----------------------------------------------------------------------

  it("submits boolean as 'false' after unchecking the checkbox", async () => {
    const user = userEvent.setup()
    render(<DmfSettingsForm settings={defaultSettings} />)

    await user.click(screen.getByRole("checkbox"))
    fireEvent.submit(
      screen.getByRole("button", { name: /opslaan/i }).closest("form")!
    )

    await waitFor(() => expect(upsertDmfSetting).toHaveBeenCalled())
    expect(upsertDmfSetting).toHaveBeenCalledWith("validation_enabled", "false")
  })

  it("submits boolean as 'true' after checking the checkbox", async () => {
    const user = userEvent.setup()
    const uncheckedSettings = defaultSettings.map((s) =>
      s.key === "validation_enabled" ? { ...s, value: "false" } : s
    )
    render(<DmfSettingsForm settings={uncheckedSettings} />)

    await user.click(screen.getByRole("checkbox"))
    fireEvent.submit(
      screen.getByRole("button", { name: /opslaan/i }).closest("form")!
    )

    await waitFor(() => expect(upsertDmfSetting).toHaveBeenCalled())
    expect(upsertDmfSetting).toHaveBeenCalledWith("validation_enabled", "true")
  })

  it("submits updated int value after changing the trigger size input", async () => {
    const user = userEvent.setup()
    render(<DmfSettingsForm settings={defaultSettings} />)

    const input = screen.getByDisplayValue("4096")
    await user.clear(input)
    await user.type(input, "1024")

    fireEvent.submit(
      screen.getByRole("button", { name: /opslaan/i }).closest("form")!
    )

    await waitFor(() => expect(upsertDmfSetting).toHaveBeenCalled())
    expect(upsertDmfSetting).toHaveBeenCalledWith(
      "trigger_size_bytes",
      "1073741824"
    )
  })
})
