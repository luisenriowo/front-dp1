"use client"

import { Shipment, Airport, getDeliveryStatus } from "@/lib/simulation-data"
import { cn } from "@/lib/utils"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Package, ArrowRight } from "lucide-react"

interface ShipmentsTableProps {
  shipments: Shipment[]
  airports: Airport[]
  currentDate: Date
  filter?: "all" | "pending" | "in-transit" | "delivered" | "delayed"
}

export function ShipmentsTable({
  shipments,
  airports,
  currentDate,
  filter = "all",
}: ShipmentsTableProps) {
  const getAirportName = (id: string) => {
    const airport = airports.find((a) => a.id === id)
    return airport ? airport.code : id
  }

  const getAirportCity = (id: string) => {
    const airport = airports.find((a) => a.id === id)
    return airport ? airport.city : id
  }

  const filteredShipments = shipments.filter((s) => {
    if (filter === "all") return true
    return s.status === filter
  })

  const sortedShipments = [...filteredShipments].sort((a, b) => {
    // Priorizar por estado: delayed > pending > in-transit > delivered
    const priority: Record<string, number> = {
      delayed: 0,
      pending: 1,
      "in-transit": 2,
      delivered: 3,
    }
    return priority[a.status] - priority[b.status]
  })

  return (
    <Card className="bg-card border-border h-full">
      <CardHeader className="pb-3">
        <CardTitle className="text-sm flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Package className="h-4 w-4 text-primary" />
            Envíos Recientes
          </div>
          <span className="text-xs text-muted-foreground font-normal">
            {filteredShipments.length} envíos
          </span>
        </CardTitle>
      </CardHeader>
      <CardContent className="p-0">
        <div className="max-h-[300px] overflow-y-auto">
          <Table>
            <TableHeader className="sticky top-0 bg-card">
              <TableRow className="hover:bg-transparent border-border">
                <TableHead className="text-xs">ID</TableHead>
                <TableHead className="text-xs">Ruta</TableHead>
                <TableHead className="text-xs text-center">Cant.</TableHead>
                <TableHead className="text-xs">Ubicación</TableHead>
                <TableHead className="text-xs text-right">Estado</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {sortedShipments.slice(0, 20).map((shipment) => {
                const status = getDeliveryStatus(shipment, currentDate)
                return (
                  <TableRow
                    key={shipment.id}
                    className="hover:bg-secondary/50 border-border"
                  >
                    <TableCell className="text-xs font-mono py-2">
                      {shipment.id.slice(0, 8)}
                    </TableCell>
                    <TableCell className="text-xs py-2">
                      <div className="flex items-center gap-1">
                        <span title={getAirportCity(shipment.origin)}>
                          {getAirportName(shipment.origin)}
                        </span>
                        <ArrowRight className="h-3 w-3 text-muted-foreground" />
                        <span title={getAirportCity(shipment.destination)}>
                          {getAirportName(shipment.destination)}
                        </span>
                      </div>
                    </TableCell>
                    <TableCell className="text-xs text-center py-2 font-mono">
                      {shipment.quantity}
                    </TableCell>
                    <TableCell className="text-xs py-2">
                      <span title={getAirportCity(shipment.currentLocation)}>
                        {getAirportName(shipment.currentLocation)}
                      </span>
                    </TableCell>
                    <TableCell className="text-xs text-right py-2">
                      <span
                        className={cn(
                          "px-2 py-0.5 rounded text-[10px] font-medium",
                          shipment.status === "pending" &&
                            "bg-status-amber/20 text-status-amber",
                          shipment.status === "in-transit" &&
                            "bg-primary/20 text-primary",
                          shipment.status === "delivered" &&
                            "bg-status-green/20 text-status-green",
                          shipment.status === "delayed" &&
                            "bg-status-red/20 text-status-red"
                        )}
                      >
                        {shipment.status === "pending" && "Pendiente"}
                        {shipment.status === "in-transit" && "En Tránsito"}
                        {shipment.status === "delivered" && "Entregado"}
                        {shipment.status === "delayed" && "Retrasado"}
                      </span>
                    </TableCell>
                  </TableRow>
                )
              })}
              {sortedShipments.length === 0 && (
                <TableRow>
                  <TableCell
                    colSpan={5}
                    className="text-center text-muted-foreground py-8 text-sm"
                  >
                    No hay envíos para mostrar
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </div>
      </CardContent>
    </Card>
  )
}
