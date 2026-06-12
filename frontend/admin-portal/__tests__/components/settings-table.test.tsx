import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { type ColumnDef } from "@tanstack/react-table"
import { describe, expect, it, vi } from "vitest"
import { SettingsTable } from "@/components/settings-table"

type Item = { id: string; name: string }

const columns: ColumnDef<Item>[] = [
  {
    accessorKey: "name",
    header: "Naam",
    cell: ({ row }) => row.original.name,
  },
]

const items: Item[] = [
  { id: "1", name: "First" },
  { id: "2", name: "Second" },
]

describe("SettingsTable", () => {
  it("renders the description", () => {
    render(
      <SettingsTable
        data={items}
        columns={columns}
        description="Test description"
        emptyMessage="No items"
        onAdd={vi.fn()}
      />
    )
    expect(screen.getByText("Test description")).toBeInTheDocument()
  })

  it("renders the add button with the default label", () => {
    render(
      <SettingsTable
        data={items}
        columns={columns}
        description="Desc"
        emptyMessage="Empty"
        onAdd={vi.fn()}
      />
    )
    expect(
      screen.getByRole("button", { name: /toevoegen/i })
    ).toBeInTheDocument()
  })

  it("renders the add button with a custom label", () => {
    render(
      <SettingsTable
        data={items}
        columns={columns}
        description="Desc"
        emptyMessage="Empty"
        onAdd={vi.fn()}
        addLabel="Nieuw aanmaken"
      />
    )
    expect(
      screen.getByRole("button", { name: /nieuw aanmaken/i })
    ).toBeInTheDocument()
  })

  it("calls onAdd when the add button is clicked", async () => {
    const onAdd = vi.fn()
    const user = userEvent.setup()
    render(
      <SettingsTable
        data={items}
        columns={columns}
        description="Desc"
        emptyMessage="Empty"
        onAdd={onAdd}
      />
    )
    await user.click(screen.getByRole("button", { name: /toevoegen/i }))
    expect(onAdd).toHaveBeenCalledOnce()
  })

  it("renders all data rows", () => {
    render(
      <SettingsTable
        data={items}
        columns={columns}
        description="Desc"
        emptyMessage="Empty"
        onAdd={vi.fn()}
      />
    )
    expect(screen.getByText("First")).toBeInTheDocument()
    expect(screen.getByText("Second")).toBeInTheDocument()
  })

  it("shows the empty state message when data is empty", () => {
    render(
      <SettingsTable
        data={[]}
        columns={columns}
        description="Desc"
        emptyMessage="Geen items gevonden"
        onAdd={vi.fn()}
      />
    )
    expect(screen.getByText("Geen items gevonden")).toBeInTheDocument()
  })

  it("calls onAdd from the empty-state button", async () => {
    const onAdd = vi.fn()
    const user = userEvent.setup()
    render(
      <SettingsTable
        data={[]}
        columns={columns}
        description="Desc"
        emptyMessage="Geen items"
        onAdd={onAdd}
      />
    )
    const buttons = screen.getAllByRole("button", { name: /toevoegen/i })
    await user.click(buttons[buttons.length - 1])
    expect(onAdd).toHaveBeenCalled()
  })

  it("renders row and header checkboxes when onBulkDelete is provided", () => {
    render(
      <SettingsTable
        data={items}
        columns={columns}
        description="Desc"
        emptyMessage="Empty"
        onAdd={vi.fn()}
        onBulkDelete={vi.fn()}
      />
    )
    const checkboxes = screen.getAllByRole("checkbox")
    expect(checkboxes).toHaveLength(items.length + 1)
  })

  it("does not render checkboxes when onBulkDelete is not provided", () => {
    render(
      <SettingsTable
        data={items}
        columns={columns}
        description="Desc"
        emptyMessage="Empty"
        onAdd={vi.fn()}
      />
    )
    expect(screen.queryAllByRole("checkbox")).toHaveLength(0)
  })

  it("shows the bulk delete button after a row is selected", async () => {
    const user = userEvent.setup()
    render(
      <SettingsTable
        data={items}
        columns={columns}
        description="Desc"
        emptyMessage="Empty"
        onAdd={vi.fn()}
        onBulkDelete={vi.fn()}
      />
    )
    expect(
      screen.queryByRole("button", { name: /verwijderen/i })
    ).not.toBeInTheDocument()
    const rowCheckboxes = screen.getAllByRole("checkbox", {
      name: /selecteer rij/i,
    })
    await user.click(rowCheckboxes[0])
    expect(
      screen.getByRole("button", { name: /verwijderen/i })
    ).toBeInTheDocument()
  })

  it("calls onBulkDelete with the selected row ids", async () => {
    const onBulkDelete = vi.fn()
    const user = userEvent.setup()
    render(
      <SettingsTable
        data={items}
        columns={columns}
        description="Desc"
        emptyMessage="Empty"
        onAdd={vi.fn()}
        onBulkDelete={onBulkDelete}
      />
    )
    const rowCheckboxes = screen.getAllByRole("checkbox", {
      name: /selecteer rij/i,
    })
    await user.click(rowCheckboxes[0])
    await user.click(screen.getByRole("button", { name: /verwijderen/i }))
    expect(onBulkDelete).toHaveBeenCalledWith(["1"])
  })

  it("selects all rows when the header checkbox is clicked and bulk-deletes all", async () => {
    const onBulkDelete = vi.fn()
    const user = userEvent.setup()
    render(
      <SettingsTable
        data={items}
        columns={columns}
        description="Desc"
        emptyMessage="Empty"
        onAdd={vi.fn()}
        onBulkDelete={onBulkDelete}
      />
    )
    await user.click(screen.getByRole("checkbox", { name: /selecteer alles/i }))
    await user.click(screen.getByRole("button", { name: /verwijderen/i }))
    expect(onBulkDelete).toHaveBeenCalledWith(
      expect.arrayContaining(["1", "2"])
    )
  })
})
