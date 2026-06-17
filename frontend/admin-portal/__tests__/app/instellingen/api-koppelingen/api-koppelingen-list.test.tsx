import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { describe, expect, it, vi } from "vitest"
import { ApiKoppelingenList } from "@/app/(main)/instellingen/api-koppelingen/api-koppelingen-list"
import type { ApiKoppeling } from "@/app/(main)/instellingen/api-koppelingen/actions"

vi.mock("@/app/(main)/instellingen/api-koppelingen/actions", () => ({
  createApiKoppeling: vi.fn(),
  updateApiKoppeling: vi.fn(),
  deleteApiKoppeling: vi.fn(),
  deleteApiKoppelingen: vi.fn(),
}))

vi.mock("@/hooks/use-mobile", () => ({ useIsMobile: () => false }))

const base: ApiKoppeling = {
  id: "1",
  name: "OpenZaak Productie",
  baseUrl: "https://api.example.com",
  clientId: "client-id",
  hasSecret: true,
  clientSecret: null,
  apiType: "zrc",
  authType: "zgw-auth",
  validationEnabled: true,
  enabled: true,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
  readonly: false,
}

const readonlyItem: ApiKoppeling = {
  ...base,
  id: "2",
  name: "OpenZaak Omgeving",
  readonly: true,
}

async function openActionsMenu(
  user: ReturnType<typeof userEvent.setup>,
  name: string
) {
  const row = screen.getByText(name).closest("tr")!
  const trigger =
    row.querySelector("button[aria-haspopup]") ?? row.querySelector("button")
  await user.click(trigger as HTMLElement)
}

describe("ApiKoppelingenList — readonly behaviour", () => {
  it("shows 'Bewerken' for a non-readonly item", async () => {
    const user = userEvent.setup()
    render(<ApiKoppelingenList settings={[base]} />)
    await openActionsMenu(user, base.name)
    expect(
      screen.getByRole("menuitem", { name: "Bewerken" })
    ).toBeInTheDocument()
  })

  it("shows 'Bekijken' for a readonly item", async () => {
    const user = userEvent.setup()
    render(<ApiKoppelingenList settings={[readonlyItem]} />)
    await openActionsMenu(user, readonlyItem.name)
    expect(
      screen.getByRole("menuitem", { name: "Bekijken" })
    ).toBeInTheDocument()
  })

  it("does not show a disabled 'Bewerken' for a readonly item", async () => {
    const user = userEvent.setup()
    render(<ApiKoppelingenList settings={[readonlyItem]} />)
    await openActionsMenu(user, readonlyItem.name)
    const bewerken = screen.queryByRole("menuitem", { name: "Bewerken" })
    expect(bewerken).not.toBeInTheDocument()
  })

  it("opens view mode when clicking 'Bekijken' on a readonly item", async () => {
    const user = userEvent.setup()
    render(<ApiKoppelingenList settings={[readonlyItem]} />)
    await openActionsMenu(user, readonlyItem.name)
    await user.click(screen.getByRole("menuitem", { name: "Bekijken" }))
    expect(
      screen.getByText("Bekijk de API koppelingsinstelling.")
    ).toBeInTheDocument()
  })

  it("shows only 'Sluiten' button in view mode", async () => {
    const user = userEvent.setup()
    render(<ApiKoppelingenList settings={[readonlyItem]} />)
    await openActionsMenu(user, readonlyItem.name)
    await user.click(screen.getByRole("menuitem", { name: "Bekijken" }))
    expect(screen.getByRole("button", { name: "Sluiten" })).toBeInTheDocument()
    expect(
      screen.queryByRole("button", { name: /opslaan/i })
    ).not.toBeInTheDocument()
    expect(
      screen.queryByRole("button", { name: /annuleren/i })
    ).not.toBeInTheDocument()
  })

  it("disables all form inputs in view mode", async () => {
    const user = userEvent.setup()
    render(<ApiKoppelingenList settings={[readonlyItem]} />)
    await openActionsMenu(user, readonlyItem.name)
    await user.click(screen.getByRole("menuitem", { name: "Bekijken" }))
    const inputs = screen.getAllByRole("textbox")
    for (const input of inputs) {
      expect(input).toBeDisabled()
    }
  })

  it("opens edit mode when clicking 'Bewerken' on a non-readonly item", async () => {
    const user = userEvent.setup()
    render(<ApiKoppelingenList settings={[base]} />)
    await openActionsMenu(user, base.name)
    await user.click(screen.getByRole("menuitem", { name: "Bewerken" }))
    expect(
      screen.getByText("Bewerk de API koppelingsinstelling.")
    ).toBeInTheDocument()
  })

  it("shows 'Opslaan' and 'Annuleren' buttons in edit mode", async () => {
    const user = userEvent.setup()
    render(<ApiKoppelingenList settings={[base]} />)
    await openActionsMenu(user, base.name)
    await user.click(screen.getByRole("menuitem", { name: "Bewerken" }))
    expect(screen.getByRole("button", { name: /opslaan/i })).toBeInTheDocument()
    expect(
      screen.getByRole("button", { name: /annuleren/i })
    ).toBeInTheDocument()
    expect(
      screen.queryByRole("button", { name: "Sluiten" })
    ).not.toBeInTheDocument()
  })
})
