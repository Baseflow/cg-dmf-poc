import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { describe, expect, it, vi } from "vitest"
import { ApplicationList } from "@/app/(main)/instellingen/applicaties/application-list"
import type { ApplicationSetting } from "@/app/(main)/instellingen/applicaties/actions"

vi.mock("@/app/(main)/instellingen/applicaties/actions", () => ({
  createApplication: vi.fn(),
  updateApplication: vi.fn(),
  deleteApplication: vi.fn(),
  deleteApplications: vi.fn(),
  rotateApplicationSecret: vi.fn(),
}))

vi.mock("@/hooks/use-mobile", () => ({ useIsMobile: () => false }))

const base: ApplicationSetting = {
  id: "1",
  name: "Mijn Applicatie",
  clientId: "my-client",
  hasSecret: true,
  clientSecret: null,
  updatedAt: "2026-01-01T00:00:00Z",
  readonly: false,
}

const readonlyItem: ApplicationSetting = { ...base, id: "2", name: "Omgeving Applicatie", readonly: true }

async function openActionsMenu(user: ReturnType<typeof userEvent.setup>, name: string) {
  const row = screen.getByText(name).closest("tr")!
  const trigger = row.querySelector("button[aria-haspopup]") ?? row.querySelector("button")
  await user.click(trigger as HTMLElement)
}

describe("ApplicationList — readonly behaviour", () => {
  it("shows 'Bewerken' for a non-readonly item", async () => {
    const user = userEvent.setup()
    render(<ApplicationList applications={[base]} />)
    await openActionsMenu(user, base.name)
    expect(screen.getByRole("menuitem", { name: "Bewerken" })).toBeInTheDocument()
  })

  it("shows 'Bekijken' for a readonly item", async () => {
    const user = userEvent.setup()
    render(<ApplicationList applications={[readonlyItem]} />)
    await openActionsMenu(user, readonlyItem.name)
    expect(screen.getByRole("menuitem", { name: "Bekijken" })).toBeInTheDocument()
  })

  it("opens view mode when clicking 'Bekijken' on a readonly item", async () => {
    const user = userEvent.setup()
    render(<ApplicationList applications={[readonlyItem]} />)
    await openActionsMenu(user, readonlyItem.name)
    await user.click(screen.getByRole("menuitem", { name: "Bekijken" }))
    expect(screen.getByText("Bekijk de applicatie-instellingen.")).toBeInTheDocument()
  })

  it("shows only 'Sluiten' button in view mode", async () => {
    const user = userEvent.setup()
    render(<ApplicationList applications={[readonlyItem]} />)
    await openActionsMenu(user, readonlyItem.name)
    await user.click(screen.getByRole("menuitem", { name: "Bekijken" }))
    expect(screen.getByRole("button", { name: "Sluiten" })).toBeInTheDocument()
    expect(screen.queryByRole("button", { name: /opslaan/i })).not.toBeInTheDocument()
    expect(screen.queryByRole("button", { name: /annuleren/i })).not.toBeInTheDocument()
  })

  it("disables all form inputs in view mode", async () => {
    const user = userEvent.setup()
    render(<ApplicationList applications={[readonlyItem]} />)
    await openActionsMenu(user, readonlyItem.name)
    await user.click(screen.getByRole("menuitem", { name: "Bekijken" }))
    const inputs = screen.getAllByRole("textbox")
    for (const input of inputs) {
      expect(input).toBeDisabled()
    }
  })

  it("shows 'Opslaan' and 'Annuleren' buttons in edit mode", async () => {
    const user = userEvent.setup()
    render(<ApplicationList applications={[base]} />)
    await openActionsMenu(user, base.name)
    await user.click(screen.getByRole("menuitem", { name: "Bewerken" }))
    expect(screen.getByRole("button", { name: /opslaan/i })).toBeInTheDocument()
    expect(screen.getByRole("button", { name: /annuleren/i })).toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Sluiten" })).not.toBeInTheDocument()
  })
})
