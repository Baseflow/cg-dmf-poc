import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { beforeEach, describe, expect, it, vi } from "vitest"
import DmfSettingsForm from "@/app/(main)/instellingen/dmf/dmf-settings-form"

vi.mock("@/app/(main)/instellingen/dmf/actions", () => ({
  saveDmfSettings: vi.fn(),
}))

import { saveDmfSettings } from "@/app/(main)/instellingen/dmf/actions"

const defaultSettings = {
  triggerSize: 4294967296,
  chunkSize: 3221225472,
  validationEnabled: false,
}

describe("DmfSettingsForm", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // Ensure the mock always returns a valid FormState so useActionState never
    // sets state to undefined. Individual tests override this as needed.
    vi.mocked(saveDmfSettings).mockResolvedValue({})
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
    vi.mocked(saveDmfSettings).mockResolvedValueOnce({
      errors: { triggerSize: "Moet minimaal 1 byte zijn." },
    })

    render(<DmfSettingsForm initialSettings={defaultSettings} />)

    const triggerInput = screen.getByDisplayValue("4294967296")
    fireEvent.change(triggerInput, { target: { value: "0" } })
    fireEvent.submit(triggerInput.closest("form")!)

    expect(
      await screen.findByText("Moet minimaal 1 byte zijn.")
    ).toBeInTheDocument()
  })

  it("shows validation error when chunkSize is empty", async () => {
    vi.mocked(saveDmfSettings).mockResolvedValueOnce({
      errors: { chunkSize: "Moet minimaal 1 byte zijn." },
    })

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
  })

  it("calls saveDmfSettings on valid submit and passes form values", async () => {
    const user = userEvent.setup()

    render(
      <DmfSettingsForm
        initialSettings={{ triggerSize: 100, chunkSize: 50, validationEnabled: false }}
      />
    )

    await user.click(screen.getByRole("button", { name: /opslaan/i }))

    await waitFor(() => {
      expect(saveDmfSettings).toHaveBeenCalled()
    })

    const [, formData] = vi.mocked(saveDmfSettings).mock.calls[0] as [
      unknown,
      FormData,
    ]
    expect(formData.get("triggerSize")).toBe("100")
    expect(formData.get("chunkSize")).toBe("50")
  })

  it("shows success message after successful save", async () => {
    vi.mocked(saveDmfSettings).mockResolvedValueOnce({ saved: true })
    const user = userEvent.setup()

    render(
      <DmfSettingsForm
        initialSettings={{ triggerSize: 100, chunkSize: 50, validationEnabled: true }}
      />
    )

    await user.click(screen.getByRole("button", { name: /opslaan/i }))

    expect(
      await screen.findByText("Instellingen opgeslagen.")
    ).toBeInTheDocument()
  })

  it("shows error message when save fails", async () => {
    vi.mocked(saveDmfSettings).mockResolvedValueOnce({
      error: "Opslaan mislukt. Probeer het opnieuw.",
    })
    const user = userEvent.setup()

    render(
      <DmfSettingsForm
        initialSettings={{ triggerSize: 100, chunkSize: 50, validationEnabled: false }}
      />
    )

    await user.click(screen.getByRole("button", { name: /opslaan/i }))

    expect(
      await screen.findByText("Opslaan mislukt. Probeer het opnieuw.")
    ).toBeInTheDocument()
  })

  it("toggles validationEnabled when checkbox is clicked", async () => {
    const user = userEvent.setup()

    render(
      <DmfSettingsForm
        initialSettings={{ triggerSize: 100, chunkSize: 50, validationEnabled: false }}
      />
    )

    const checkbox = screen.getByRole("checkbox")
    expect(checkbox).not.toBeChecked()

    await user.click(checkbox)
    await user.click(screen.getByRole("button", { name: /opslaan/i }))

    await waitFor(() => {
      expect(saveDmfSettings).toHaveBeenCalled()
    })

    const [, formData] = vi.mocked(saveDmfSettings).mock.calls[0] as [
      unknown,
      FormData,
    ]
    expect(formData.get("validationEnabled")).toBe("on")
  })
})
