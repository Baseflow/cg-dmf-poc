"use client"

import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
  type RowSelectionState,
} from "@tanstack/react-table"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { Plus, Trash2 } from "lucide-react"
import * as React from "react"

interface SettingsTableProps<T extends { id: string }> {
  data: T[]
  columns: ColumnDef<T>[]
  description: string
  emptyMessage: string
  onAdd: () => void
  addLabel?: string
  emptyAddLabel?: string
  onBulkDelete?: (ids: string[]) => void
}

export function SettingsTable<T extends { id: string }>({
  data,
  columns: dataCols,
  description,
  emptyMessage,
  onAdd,
  addLabel = "Toevoegen",
  emptyAddLabel,
  onBulkDelete,
}: SettingsTableProps<T>) {
  const [rowSelection, setRowSelection] = React.useState<RowSelectionState>({})

  React.useEffect(() => {
    setRowSelection({})
  }, [data])

  const selectedIds = React.useMemo(
    () =>
      Object.entries(rowSelection)
        .filter(([, v]) => v)
        .map(([id]) => id),
    [rowSelection]
  )

  const allColumns = React.useMemo<ColumnDef<T>[]>(() => {
    if (!onBulkDelete) return dataCols
    const selectCol: ColumnDef<T> = {
      id: "select",
      header: ({ table }) => (
        <Checkbox
          checked={
            table.getIsAllRowsSelected()
              ? true
              : table.getIsSomeRowsSelected()
                ? "indeterminate"
                : false
          }
          onCheckedChange={(checked) => table.toggleAllRowsSelected(!!checked)}
          aria-label="Selecteer alles"
        />
      ),
      cell: ({ row }) => (
        <Checkbox
          checked={row.getIsSelected()}
          onCheckedChange={(checked) => row.toggleSelected(!!checked)}
          aria-label="Selecteer rij"
        />
      ),
    }
    return [selectCol, ...dataCols]
  }, [dataCols, onBulkDelete])

  const table = useReactTable({
    data,
    columns: allColumns,
    getCoreRowModel: getCoreRowModel(),
    onRowSelectionChange: setRowSelection,
    getRowId: (row) => row.id,
    state: { rowSelection },
  })

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-4">
        <p className="text-sm text-muted-foreground">{description}</p>
        <div className="flex items-center gap-2">
          {onBulkDelete && selectedIds.length > 0 && (
            <Button
              variant="destructive"
              size="sm"
              onClick={() => onBulkDelete(selectedIds)}
            >
              <Trash2 />
              {selectedIds.length} verwijderen
            </Button>
          )}
          <Button variant="outline" size="sm" onClick={onAdd}>
            <Plus />
            {addLabel}
          </Button>
        </div>
      </div>

      {data.length === 0 ? (
        <div className="flex flex-col items-center gap-3 rounded-lg border py-12 text-center">
          <p className="text-sm text-muted-foreground">{emptyMessage}</p>
          <Button variant="outline" size="sm" onClick={onAdd}>
            <Plus />
            {emptyAddLabel ?? addLabel}
          </Button>
        </div>
      ) : (
        <div className="rounded-lg border">
          <Table>
            <TableHeader>
              {table.getHeaderGroups().map((headerGroup) => (
                <TableRow key={headerGroup.id}>
                  {headerGroup.headers.map((header) => (
                    <TableHead key={header.id}>
                      {header.isPlaceholder
                        ? null
                        : flexRender(
                            header.column.columnDef.header,
                            header.getContext()
                          )}
                    </TableHead>
                  ))}
                </TableRow>
              ))}
            </TableHeader>
            <TableBody>
              {table.getRowModel().rows.map((row) => (
                <TableRow
                  key={row.id}
                  data-state={row.getIsSelected() ? "selected" : undefined}
                >
                  {row.getVisibleCells().map((cell) => (
                    <TableCell key={cell.id}>
                      {flexRender(
                        cell.column.columnDef.cell,
                        cell.getContext()
                      )}
                    </TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  )
}
