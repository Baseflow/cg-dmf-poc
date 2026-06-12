import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { describe, expect, it } from "vitest"
import { SecretCell } from "@/components/secret-cell"

describe("SecretCell", () => {
  it("shows placeholder dots when a value is provided", () => {
    render(<SecretCell value="my-secret" />)
    expect(screen.getByText("••••••••")).toBeInTheDocument()
    expect(screen.queryByText("my-secret")).not.toBeInTheDocument()
  })

  it("renders a toggle button with correct aria-label when hidden", () => {
    render(<SecretCell value="my-secret" />)
    expect(
      screen.getByRole("button", { name: "Toon waarde" })
    ).toBeInTheDocument()
  })

  it("reveals the value when the toggle button is clicked", async () => {
    const user = userEvent.setup()
    render(<SecretCell value="my-secret" />)
    await user.click(screen.getByRole("button", { name: "Toon waarde" }))
    expect(screen.getByText("my-secret")).toBeInTheDocument()
    expect(screen.queryByText("••••••••")).not.toBeInTheDocument()
  })

  it("updates the aria-label after reveal", async () => {
    const user = userEvent.setup()
    render(<SecretCell value="my-secret" />)
    await user.click(screen.getByRole("button", { name: "Toon waarde" }))
    expect(
      screen.getByRole("button", { name: "Verberg waarde" })
    ).toBeInTheDocument()
  })

  it("hides the value again when the toggle button is clicked a second time", async () => {
    const user = userEvent.setup()
    render(<SecretCell value="my-secret" />)
    await user.click(screen.getByRole("button", { name: "Toon waarde" }))
    await user.click(screen.getByRole("button", { name: "Verberg waarde" }))
    expect(screen.getByText("••••••••")).toBeInTheDocument()
    expect(screen.queryByText("my-secret")).not.toBeInTheDocument()
  })

  it("shows 'Ingesteld' when value is null and hasSecret is true", () => {
    render(<SecretCell value={null} hasSecret />)
    expect(screen.getByText("Ingesteld")).toBeInTheDocument()
    expect(screen.queryByRole("button")).not.toBeInTheDocument()
  })

  it("shows '—' when value is null and hasSecret is false", () => {
    render(<SecretCell value={null} hasSecret={false} />)
    expect(screen.getByText("—")).toBeInTheDocument()
  })

  it("shows '—' when value is null and hasSecret is not provided", () => {
    render(<SecretCell value={null} />)
    expect(screen.getByText("—")).toBeInTheDocument()
  })
})
