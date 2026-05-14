"use client"

import { Airport, Shipment, getStatusColor } from "@/lib/simulation-data"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Progress } from "@/components/ui/progress"
import { cn } from "@/lib/utils"
import { Plane, Package, Clock, ArrowRight, Warehouse } from "lucide-react"

interface AirportDetailProps {
  airport: Airport
  incomingShipments: Shipment[]
  outgoingShipments: Shipment[]
  airports: Airport[]
}

export function AirportDetail({
  airport,
  incomingShipments,
  outgoingShipments,
  airports,
}: AirportDetailProps) {
  const utilizationPercent = (airport.currentStorage / airport.storageCapacity) * 100
  const status = getStatusColor(utilizationPercent)
  
  const getAirportName = (id: string) => {
    const a = airports.find((ap) => ap.id === id)
    return a ? a.code : id
  }

  return (
    <Card className="h-full bg-card border-border">
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          <CardTitle className="text-lg flex items-center gap-2">
            <Plane className="h-5 w-5 text-primary" />
            {airport.city} ({airport.code})
          </CardTitle>
          <span className="text-xs px-2 py-1 rounded bg-secondary text-secondary-foreground">
            {airport.continent}
          </span>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* Capacidad del almacén */}
        <div className="space-y-2">
          <div className="flex items-center justify-between text-sm">
            <div className="flex items-center gap-2 text-muted-foreground">
              <Warehouse className="h-4 w-4" />
              Capacidad Almacén
            </div>
            <span className="font-mono">
              {airport.currentStorage} / {airport.storageCapacity}
            </span>
          </div>
          <Progress
            value={utilizationPercent}
            className={cn(
              "h-2",
              status === "green" && "[&>div]:bg-status-green",
              status === "amber" && "[&>div]:bg-status-amber",
              status === "red" && "[&>div]:bg-status-red"
            )}
          />
          <div className="flex justify-between text-xs text-muted-foreground">
            <span>{utilizationPercent.toFixed(1)}% utilizado</span>
            <span
              className={cn(
                "font-medium",
                status === "green" && "text-status-green",
                status === "amber" && "text-status-amber",
                status === "red" && "text-status-red"
              )}
            >
              {status === "green" && "Normal"}
              {status === "amber" && "Atención"}
              {status === "red" && "Crítico"}
            </span>
          </div>
        </div>

        {/* Envíos salientes */}
        <div className="space-y-2">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Package className="h-4 w-4" />
            Envíos Salientes ({outgoingShipments.length})
          </div>
          <div className="max-h-32 overflow-y-auto space-y-1">
            {outgoingShipments.slice(0, 5).map((shipment) => (
              <div
                key={shipment.id}
                className="flex items-center justify-between text-xs bg-secondary/50 rounded px-2 py-1.5"
              >
                <div className="flex items-center gap-1">
                  <span className="font-mono">{shipment.quantity} mal.</span>
                  <ArrowRight className="h-3 w-3 text-muted-foreground" />
                  <span>{getAirportName(shipment.destination)}</span>
                </div>
                <span
                  className={cn(
                    "px-1.5 py-0.5 rounded text-[10px]",
                    shipment.status === "pending" && "bg-status-amber/20 text-status-amber",
                    shipment.status === "in-transit" && "bg-primary/20 text-primary",
                    shipment.status === "delivered" && "bg-status-green/20 text-status-green",
                    shipment.status === "delayed" && "bg-status-red/20 text-status-red"
                  )}
                >
                  {shipment.status === "pending" && "Pendiente"}
                  {shipment.status === "in-transit" && "En tránsito"}
                  {shipment.status === "delivered" && "Entregado"}
                  {shipment.status === "delayed" && "Retrasado"}
                </span>
              </div>
            ))}
            {outgoingShipments.length > 5 && (
              <p className="text-xs text-muted-foreground text-center py-1">
                +{outgoingShipments.length - 5} más
              </p>
            )}
            {outgoingShipments.length === 0 && (
              <p className="text-xs text-muted-foreground text-center py-2">
                Sin envíos salientes
              </p>
            )}
          </div>
        </div>

        {/* Envíos entrantes */}
        <div className="space-y-2">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Clock className="h-4 w-4" />
            Envíos Entrantes ({incomingShipments.length})
          </div>
          <div className="max-h-32 overflow-y-auto space-y-1">
            {incomingShipments.slice(0, 5).map((shipment) => (
              <div
                key={shipment.id}
                className="flex items-center justify-between text-xs bg-secondary/50 rounded px-2 py-1.5"
              >
                <div className="flex items-center gap-1">
                  <span>{getAirportName(shipment.origin)}</span>
                  <ArrowRight className="h-3 w-3 text-muted-foreground" />
                  <span className="font-mono">{shipment.quantity} mal.</span>
                </div>
                <span
                  className={cn(
                    "px-1.5 py-0.5 rounded text-[10px]",
                    shipment.status === "pending" && "bg-status-amber/20 text-status-amber",
                    shipment.status === "in-transit" && "bg-primary/20 text-primary",
                    shipment.status === "delivered" && "bg-status-green/20 text-status-green",
                    shipment.status === "delayed" && "bg-status-red/20 text-status-red"
                  )}
                >
                  {shipment.status === "pending" && "Pendiente"}
                  {shipment.status === "in-transit" && "En tránsito"}
                  {shipment.status === "delivered" && "Entregado"}
                  {shipment.status === "delayed" && "Retrasado"}
                </span>
              </div>
            ))}
            {incomingShipments.length > 5 && (
              <p className="text-xs text-muted-foreground text-center py-1">
                +{incomingShipments.length - 5} más
              </p>
            )}
            {incomingShipments.length === 0 && (
              <p className="text-xs text-muted-foreground text-center py-2">
                Sin envíos entrantes
              </p>
            )}
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
