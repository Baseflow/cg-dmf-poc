import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { describe, expect, it, vi } from "vitest"
import { RepositoryList } from "@/app/(main)/instellingen/repositories/repository-list"
import type { Repository } from "@/app/(main)/instellingen/repositories/actions"

vi.mock("@/app/(main)/instellingen/repositories/actions", () => ({
  createRepository: vi.fn(),
  updateRepository: vi.fn(),
  deleteRepository: vi.fn(),
  deleteRepositories: vi.fn(),
}))

vi.mock("@/hooks/use-mobile", () => ({ useIsMobile: () => false }))

const base: Repository = {
  id: "1",
  name: "Productie S3",
  storageType: "S3",
  url: "https://s3.example.com",
  bucket: "mijn-bucket",
  isDefault: false,
  enabled: true,
  accessKey: "AKIAIOSFODNN7EXAMPLE",
  secretKey: null,
  storageAccountName: null,
  updatedAt: "2026-01-01T00:00:00Z",
  readonly: false,
}

const readonlyItem: Repository = {
  ...base,
  id: "2",
  name: "Omgeving S3",
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

describe("RepositoryList — readonly behaviour", () => {
  it("shows 'Bewerken' for a non-readonly item", async () => {
    const user = userEvent.setup()
    render(<RepositoryList repositories={[base]} />)
    await openActionsMenu(user, base.name)
    expect(
      screen.getByRole("menuitem", { name: "Bewerken" })
    ).toBeInTheDocument()
  })

  it("shows 'Bekijken' for a readonly item", async () => {
    const user = userEvent.setup()
    render(<RepositoryList repositories={[readonlyItem]} />)
    await openActionsMenu(user, readonlyItem.name)
    expect(
      screen.getByRole("menuitem", { name: "Bekijken" })
    ).toBeInTheDocument()
  })

  it("opens view mode when clicking 'Bekijken' on a readonly item", async () => {
    const user = userEvent.setup()
    render(<RepositoryList repositories={[readonlyItem]} />)
    await openActionsMenu(user, readonlyItem.name)
    await user.click(screen.getByRole("menuitem", { name: "Bekijken" }))
    expect(
      screen.getByText("Bekijk de repository-instellingen.")
    ).toBeInTheDocument()
  })

  it("shows only 'Sluiten' button in view mode", async () => {
    const user = userEvent.setup()
    render(<RepositoryList repositories={[readonlyItem]} />)
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
    render(<RepositoryList repositories={[readonlyItem]} />)
    await openActionsMenu(user, readonlyItem.name)
    await user.click(screen.getByRole("menuitem", { name: "Bekijken" }))
    const inputs = screen.getAllByRole("textbox")
    for (const input of inputs) {
      expect(input).toBeDisabled()
    }
  })

  it("shows 'Opslaan' and 'Annuleren' buttons in edit mode", async () => {
    const user = userEvent.setup()
    render(<RepositoryList repositories={[base]} />)
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
