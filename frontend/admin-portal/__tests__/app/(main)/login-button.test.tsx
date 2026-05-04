import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { signIn, useSession } from "next-auth/react"
import { describe, expect, it, vi } from "vitest"
import { LoginButton } from "@/app/(main)/login-button"

vi.mock("next-auth/react", () => ({
  useSession: vi.fn(),
  signIn: vi.fn(),
}))

describe("LoginButton", () => {
  it("renders the login button when unauthenticated", () => {
    vi.mocked(useSession).mockReturnValue({
      data: null,
      status: "unauthenticated",
      update: vi.fn(),
    })
    render(<LoginButton />)
    expect(screen.getByRole("button", { name: /log in/i })).toBeInTheDocument()
  })

  it("renders nothing when authenticated", () => {
    vi.mocked(useSession).mockReturnValue({
      data: { user: {}, expires: "" },
      status: "authenticated",
      update: vi.fn(),
    })
    const { container } = render(<LoginButton />)
    expect(container.firstChild).toBeNull()
  })

  it("renders a disabled button while loading", () => {
    vi.mocked(useSession).mockReturnValue({
      data: null,
      status: "loading",
      update: vi.fn(),
    })
    render(<LoginButton />)
    expect(screen.getByRole("button")).toBeDisabled()
  })

  it("calls signIn with keycloak when clicked", async () => {
    vi.mocked(useSession).mockReturnValue({
      data: null,
      status: "unauthenticated",
      update: vi.fn(),
    })
    const user = userEvent.setup()
    render(<LoginButton />)
    await user.click(screen.getByRole("button", { name: /log in/i }))
    expect(signIn).toHaveBeenCalledWith("keycloak")
  })
})
