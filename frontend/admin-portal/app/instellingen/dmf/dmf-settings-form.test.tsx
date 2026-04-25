import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { beforeEach, describe, expect, it, vi } from "vitest"
import DmfSettingsForm from "./dmf-settings-form"

// Mock the server action
vi.mock("./actions", () => ({
  saveDmfSettings: vi.fn(),
}))

import { saveDmfSettings } from "./actions"

const defaultSettings = {
  triggerSize: 4294967296,
  chunkSize: 3221225472,
  validationEnabled: false,
}

describe("DmfSettingsForm", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("renders with initial values", () => {
    render(<DmfSettingsForm initialSettings={defaultSettings} />)
    expect(screen.getByDisplayValue("4294967296")).toBeInTheDocument()
    expect(screen.getByDisplayValue("3221225472")).toBeInTheDocument()
  })

  it("renders the save button", () => {
    render(<DmfSettingsForm initialSettings={defaultSettings} />)
    expect(screen.getByRole("button", { name: /opslaan/i })).toBeInTheDocument()
  })

  it("shows validation error when triggerSize is 0", async () => {
    render(<DmfSettingsForm initialSettings={defaultSettings} />)

    const triggerInput = screen.getByDisplayValue("4294967296")
    fireEvent.change(triggerInput, { target: { value: "0" } })
    fireEvent.submit(triggerInput.closest("form")!)

    expect(
      await screen.findByText("Moet minimaal 1 byte zijn.")
    ).toBeInTheDocument()
    expect(saveDmfSettings).not.toHaveBeenCalled()
  })

  it("shows validation error when chunkSize is empty", async () => {
    const user = userEvent.setup()
    render(<DmfSettingsForm initialSettings={defaultSettings} />)

    const chunkInput = screen.getByDisplayValue("3221225472")
    await user.clear(chunkInput)
    fireEvent.submit(
      screen.getByRole("button", { name: /opslaan/i }).closest("form")!
    )

    expect(
      await screen.findByText(/moet minimaal 1 byte zijn/i)
    ).toBeInTheDocument()
    expect(saveDmfSettings).not.toHaveBeenCalled()
  })

  it("calls saveDmfSettings with correct values on valid submit", async () => {
    vi.mocked(saveDmfSettings).mockResolvedValueOnce(undefined)
    const user = userEvent.setup()

    render(
      <DmfSettingsForm
        initialSettings={{
          triggerSize: 100,
          chunkSize: 50,
          validationEnabled: false,
        }}
      />
    )

    await user.click(screen.getByRole("button", { name: /opslaan/i }))

    await waitFor(() => {
      expect(saveDmfSettings).toHaveBeenCalledWith({
        triggerSize: 100,
        chunkSize: 50,
        validationEnabled: false,
      })
    })
  })

  it("shows success message after successful save", async () => {
    vi.mocked(saveDmfSettings).mockResolvedValueOnce(undefined)
    const user = userEvent.setup()

    render(
      <DmfSettingsForm
        initialSettings={{
          triggerSize: 100,
          chunkSize: 50,
          validationEnabled: true,
        }}
      />
    )

    await user.click(screen.getByRole("button", { name: /opslaan/i }))

    expect(
      await screen.findByText("Instellingen opgeslagen.")
    ).toBeInTheDocument()
  })

  it("shows error message when save fails", async () => {
    vi.mocked(saveDmfSettings).mockRejectedValueOnce(new Error("HTTP 500"))
    const user = userEvent.setup()

    render(
      <DmfSettingsForm
        initialSettings={{
          triggerSize: 100,
          chunkSize: 50,
          validationEnabled: false,
        }}
      />
    )

    await user.click(screen.getByRole("button", { name: /opslaan/i }))

    expect(
      await screen.findByText("Opslaan mislukt. Probeer het opnieuw.")
    ).toBeInTheDocument()
  })

  it("toggles validationEnabled when checkbox is clicked", async () => {
    vi.mocked(saveDmfSettings).mockResolvedValueOnce(undefined)
    const user = userEvent.setup()

    render(
      <DmfSettingsForm
        initialSettings={{
          triggerSize: 100,
          chunkSize: 50,
          validationEnabled: false,
        }}
      />
    )

    const checkbox = screen.getByRole("checkbox")
    expect(checkbox).not.toBeChecked()

    await user.click(checkbox)
    await user.click(screen.getByRole("button", { name: /opslaan/i }))

    await waitFor(() => {
      expect(saveDmfSettings).toHaveBeenCalledWith(
        expect.objectContaining({ validationEnabled: true })
      )
    })
  })
})
