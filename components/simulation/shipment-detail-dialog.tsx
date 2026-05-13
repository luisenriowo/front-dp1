"use client"

import { useState } from "react"
import { Shipment, Airport, getDeliveryStatus } from "@/lib/simulation-data"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { cn } from "@/lib/utils"
import {
  Search,
  Package,
  MapPin,
  Calendar,
  Clock,
  ArrowRight,
  Plane,
} from "lucide-react"

interface ShipmentDetailDialogProps {
  shipments: Shipment[]
  airports: Airport[]
  currentDate: Date
  children: React.ReactNode
}

export function ShipmentDetailDialog({
  shipments,
  airports,
  currentDate,
  children,
}: ShipmentDetailDialogProps) {
  const [searchId, setSearchId] = useState("")
  const [selectedShipment, setSelectedShipment] = useState<Shipment | null>(null)

  const getAirportInfo = (id: string) => {
    const airport = airports.find((a) => a.id === id)
    return airport ? { code: airport.code, city: airport.city } : { code: id, city: id }
  }

  const handleSearch = () => {
    const found = shipments.find(
      (s) => s.id.toLowerCase().includes(searchId.toLowerCase())
    )
    setSelectedShipment(found || null)
  }

  const filteredShipments = searchId
    ? shipments.filter((s) => s.id.toLowerCase().includes(searchId.toLowerCase()))
    : shipments.slice(0, 10)

  const formatDate = (date: Date) => {
    return date.toLocaleDateString("es-ES", {
      day: "2-digit",
      month: "short",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    })
  }

  return (
    <Dialog>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="max-w-2xl max-h-[80vh] overflow-hidden flex flex-col">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Search className="h-5 w-5 text-primary" />
            Buscar Envío Específico
          </DialogTitle>
        </DialogHeader>

        <div className="flex gap-2 mb-4">
          <Input
            placeholder="Buscar por ID de envío (ej: s-1-0)"
            value={searchId}
            onChange={(e) => setSearchId(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleSearch()}
            className="flex-1"
          />
          <Button onClick={handleSearch}>Buscar</Button>
        </div>

        <div className="flex-1 overflow-y-auto space-y-3">
          {selectedShipment ? (
            <Card className="bg-secondary/30 border-primary/30">
              <CardContent className="pt-4 space-y-4">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Package className="h-5 w-5 text-primary" />
                    <span className="font-mono font-bold">{selectedShipment.id}</span>
                  </div>
                  <Badge
                    className={cn(
                      selectedShipment.status === "pending" &&
                        "bg-status-amber/20 text-status-amber border-status-amber/30",
                      selectedShipment.status === "in-transit" &&
                        "bg-primary/20 text-primary border-primary/30",
                      selectedShipment.status === "delivered" &&
                        "bg-status-green/20 text-status-green border-status-green/30",
                      selectedShipment.status === "delayed" &&
                        "bg-status-red/20 text-status-red border-status-red/30"
                    )}
                  >
                    {selectedShipment.status === "pending" && "Pendiente"}
                    {selectedShipment.status === "in-transit" && "En Tránsito"}
                    {selectedShipment.status === "delivered" && "Entregado"}
                    {selectedShipment.status === "delayed" && "Retrasado"}
                  </Badge>
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-1">
                    <p className="text-xs text-muted-foreground flex items-center gap-1">
                      <MapPin className="h-3 w-3" /> Origen
                    </p>
                    <p className="font-medium">
                      {getAirportInfo(selectedShipment.origin).code} -{" "}
                      {getAirportInfo(selectedShipment.origin).city}
                    </p>
                  </div>
                  <div className="space-y-1">
                    <p className="text-xs text-muted-foreground flex items-center gap-1">
                      <MapPin className="h-3 w-3" /> Destino
                    </p>
                    <p className="font-medium">
                      {getAirportInfo(selectedShipment.destination).code} -{" "}
                      {getAirportInfo(selectedShipment.destination).city}
                    </p>
                  </div>
                  <div className="space-y-1">
                    <p className="text-xs text-muted-foreground flex items-center gap-1">
                      <Calendar className="h-3 w-3" /> Creado
                    </p>
                    <p className="font-medium text-sm">
                      {formatDate(selectedShipment.createdAt)}
                    </p>
                  </div>
                  <div className="space-y-1">
                    <p className="text-xs text-muted-foreground flex items-center gap-1">
                      <Clock className="h-3 w-3" /> Fecha Límite
                    </p>
                    <p className="font-medium text-sm">
                      {formatDate(selectedShipment.deadline)}
                    </p>
                  </div>
                  <div className="space-y-1">
                    <p className="text-xs text-muted-foreground flex items-center gap-1">
                      <Package className="h-3 w-3" /> Cantidad
                    </p>
                    <p className="font-medium">{selectedShipment.quantity} maletas</p>
                  </div>
                  <div className="space-y-1">
                    <p className="text-xs text-muted-foreground flex items-center gap-1">
                      <Plane className="h-3 w-3" /> Ubicación Actual
                    </p>
                    <p className="font-medium">
                      {getAirportInfo(selectedShipment.currentLocation).code}
                    </p>
                  </div>
                </div>

                <div className="space-y-2">
                  <p className="text-xs text-muted-foreground">Ruta Planificada</p>
                  <div className="flex items-center gap-2 flex-wrap">
                    {selectedShipment.route.map((stop, idx) => (
                      <div key={idx} className="flex items-center gap-2">
                        <Badge
                          variant={
                            idx <= selectedShipment.currentRouteIndex
                              ? "default"
                              : "outline"
                          }
                          className="text-xs"
                        >
                          {getAirportInfo(stop).code}
                        </Badge>
                        {idx < selectedShipment.route.length - 1 && (
                          <ArrowRight className="h-3 w-3 text-muted-foreground" />
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              </CardContent>
            </Card>
          ) : (
            <>
              <p className="text-sm text-muted-foreground mb-2">
                {searchId ? "Resultados de búsqueda:" : "Envíos recientes:"}
              </p>
              {filteredShipments.length === 0 ? (
                <p className="text-center text-muted-foreground py-8">
                  No se encontraron envíos
                </p>
              ) : (
                filteredShipments.map((shipment) => (
                  <Card
                    key={shipment.id}
                    className="cursor-pointer hover:bg-secondary/50 transition-colors"
                    onClick={() => setSelectedShipment(shipment)}
                  >
                    <CardContent className="py-3 flex items-center justify-between">
                      <div className="flex items-center gap-3">
                        <Package className="h-4 w-4 text-muted-foreground" />
                        <div>
                          <p className="font-mono text-sm">{shipment.id}</p>
                          <p className="text-xs text-muted-foreground">
                            {getAirportInfo(shipment.origin).code} →{" "}
                            {getAirportInfo(shipment.destination).code}
                          </p>
                        </div>
                      </div>
                      <Badge
                        variant="outline"
                        className={cn(
                          "text-xs",
                          shipment.status === "pending" && "text-status-amber",
                          shipment.status === "in-transit" && "text-primary",
                          shipment.status === "delivered" && "text-status-green",
                          shipment.status === "delayed" && "text-status-red"
                        )}
                      >
                        {shipment.status === "pending" && "Pendiente"}
                        {shipment.status === "in-transit" && "En Tránsito"}
                        {shipment.status === "delivered" && "Entregado"}
                        {shipment.status === "delayed" && "Retrasado"}
                      </Badge>
                    </CardContent>
                  </Card>
                ))
              )}
            </>
          )}
        </div>

        {selectedShipment && (
          <Button
            variant="outline"
            className="mt-2"
            onClick={() => setSelectedShipment(null)}
          >
            Volver a la lista
          </Button>
        )}
      </DialogContent>
    </Dialog>
  )
}
