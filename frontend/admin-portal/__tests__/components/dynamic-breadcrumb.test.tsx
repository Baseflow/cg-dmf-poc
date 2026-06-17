import { render, screen } from "@testing-library/react"
import { usePathname } from "next/navigation"
import { describe, expect, it, vi } from "vitest"
import { DynamicBreadcrumb } from "@/components/dynamic-breadcrumb"

vi.mock("next/navigation", () => ({
  usePathname: vi.fn(),
}))

function setPathname(pathname: string) {
  vi.mocked(usePathname).mockReturnValue(pathname)
}

describe("DynamicBreadcrumb", () => {
  it("renders nothing visible for root path", () => {
    setPathname("/")
    const { container } = render(<DynamicBreadcrumb />)
    // No breadcrumb items — the list is empty
    expect(container.querySelectorAll("li").length).toBe(0)
  })

  it("renders known route labels correctly", () => {
    setPathname("/instellingen/dmf")
    render(<DynamicBreadcrumb />)
    expect(screen.getByText("Instellingen")).toBeInTheDocument()
    expect(screen.getByText("DMF instellingen")).toBeInTheDocument()
  })

  it("capitalizes unknown segments", () => {
    setPathname("/instellingen/unknown-route")
    render(<DynamicBreadcrumb />)
    expect(screen.getByText("Unknown Route")).toBeInTheDocument()
  })

  it("renders intermediate segments as links", () => {
    setPathname("/instellingen/dmf")
    render(<DynamicBreadcrumb />)
    const link = screen.getByRole("link", { name: "Instellingen" })
    expect(link).toHaveAttribute("href", "/instellingen")
  })

  it("renders the last segment as a non-link page item", () => {
    setPathname("/instellingen/dmf")
    render(<DynamicBreadcrumb />)
    // BreadcrumbPage renders with aria-current="page" and aria-disabled="true"
    const page = screen.getByText("DMF instellingen")
    expect(page).toHaveAttribute("aria-current", "page")
    expect(page.tagName).not.toBe("A")
  })

  it("renders verkenner routes under Documentatie > API verkenner", () => {
    setPathname("/documenten-api/openapi-ui")
    render(<DynamicBreadcrumb />)
    expect(screen.getByText("Documentatie")).toBeInTheDocument()
    const link = screen.getByRole("link", { name: "API verkenner" })
    expect(link).toHaveAttribute("href", "/documenten-verkenner")
    expect(screen.getByText("Documenten API")).toBeInTheDocument()
  })

  it("maps all known route slugs to their labels", () => {
    const cases: [string, string][] = [
      ["/instellingen/api-koppelingen", "API koppelingen"],
      ["/instellingen/dmf", "DMF instellingen"],
      ["/instellingen/repositories", "Opslag repositories"],
      ["/instellingen/applicaties", "Applicaties"],
    ]

    for (const [pathname, expectedLabel] of cases) {
      setPathname(pathname)
      const { unmount } = render(<DynamicBreadcrumb />)
      expect(screen.getByText(expectedLabel)).toBeInTheDocument()
      unmount()
    }
  })
})
