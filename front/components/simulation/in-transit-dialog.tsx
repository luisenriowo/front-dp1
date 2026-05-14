"use client"

import { Shipment, Airport } from "@/lib/simulation-data"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Progress } from "@/components/ui/progress"
import { ScrollArea } from "@/components/ui/scroll-area"
import {
  Truck,
  Package,
  MapPin,
  ArrowRight,
  Clock,
} from "lucide-react"

interface InTransitDialogProps {
  shipments: Shipment[]
  airports: Airport[]
  currentDate: Date
  children: React.ReactNode
}

export function InTransitDialog({
  shipments,
  airports,
  currentDate,
  children,
}: InTransitDialogProps) {
  const inTransitShipments = shipments.filter((s) => s.status === "in-transit")

  const getAirportInfo = (id: string) => {
    const airport = airports.find((a) => a.id === id)
    return airport ? { code: airport.code, city: airport.city } : { code: id, city: id }
  }

  const getTimeRemaining = (deadline: Date) => {
    const diff = deadline.getTime() - currentDate.getTime()
    const hours = Math.floor(diff / (1000 * 60 * 60))
    const days = Math.floor(hours / 24)
    const remainingHours = hours % 24
    
    if (days > 0) {
      return `${days}d ${remainingHours}h`
    }
    return `${hours}h`
  }

  const getProgressToDeadline = (createdAt: Date, deadline: Date) => {
    const totalTime = deadline.getTime() - createdAt.getTime()
    const elapsed = currentDate.getTime() - createdAt.getTime()
    return Math.min((elapsed / totalTime) * 100, 100)
  }

  return (
    <Dialog>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="max-w-3xl max-h-[80vh] overflow-hidden flex flex-col">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Truck className="h-5 w-5 text-status-amber" />
            Envíos en Tránsito
            <Badge variant="secondary" className="ml-2">
              {inTransitShipments.length} envíos
            </Badge>
          </DialogTitle>
        </DialogHeader>

        <ScrollArea className="flex-1 pr-4">
          {inTransitShipments.length === 0 ? (
            <div className="text-center py-12 text-muted-foreground">
              <Truck className="h-12 w-12 mx-auto mb-4 opacity-50" />
              <p>No hay envíos en tránsito actualmente</p>
            </div>
          ) : (
            <div className="space-y-3">
              {inTransitShipments.map((shipment) => {
                const progress = getProgressToDeadline(shipment.createdAt, shipment.deadline)
                const isUrgent = progress > 80
                
                return (
                  <Card
                    key={shipment.id}
                    className={`border ${isUrgent ? "border-status-red/50" : "border-border"}`}
                  >
                    <CardContent className="py-4">
                      <div className="flex items-start justify-between mb-3">
                        <div className="flex items-center gap-2">
                          <Package className="h-4 w-4 text-primary" />
                          <span className="font-mono text-sm font-medium">
                            {shipment.id}
                          </span>
                          {isUrgent && (
                            <Badge variant="destructive" className="text-[10px]">
                              Urgente
                            </Badge>
                          )}
                        </div>
                        <div className="text-right">
                          <div className="flex items-center gap-1 text-xs text-muted-foreground">
                            <Clock className="h-3 w-3" />
                            <span>{getTimeRemaining(shipment.deadline)} restante</span>
                          </div>
                        </div>
                      </div>

                      <div className="grid grid-cols-4 gap-4 mb-3">
                        <div>
                          <p className="text-[10px] text-muted-foreground mb-1">Origen</p>
                          <div className="flex items-center gap-1">
                            <MapPin className="h-3 w-3 text-muted-foreground" />
                            <span className="text-sm font-medium">
                              {getAirportInfo(shipment.origin).code}
                            </span>
                          </div>
                        </div>
                        <div>
                          <p className="text-[10px] text-muted-foreground mb-1">Destino</p>
                          <div className="flex items-center gap-1">
                            <MapPin className="h-3 w-3 text-muted-foreground" />
                            <span className="text-sm font-medium">
                              {getAirportInfo(shipment.destination).code}
                            </span>
                          </div>
                        </div>
                        <div>
                          <p className="text-[10px] text-muted-foreground mb-1">
                            Ubicación Actual
                          </p>
                          <Badge variant="outline" className="text-xs">
                            {getAirportInfo(shipment.currentLocation).code}
                          </Badge>
                        </div>
                        <div>
                          <p className="text-[10px] text-muted-foreground mb-1">Cantidad</p>
                          <span className="text-sm font-mono">{shipment.quantity}</span>
                        </div>
                      </div>

                      <div className="space-y-1">
                        <div className="flex items-center gap-2 text-xs">
                          <span className="text-muted-foreground">Progreso de entrega:</span>
                          <span className="font-mono">{progress.toFixed(0)}%</span>
                        </div>
                        <Progress
                          value={progress}
                          className={`h-1.5 ${
                            isUrgent
                              ? "[&>div]:bg-status-red"
                              : progress > 60
                              ? "[&>div]:bg-status-amber"
                              : "[&>div]:bg-status-green"
                          }`}
                        />
                      </div>

                      <div className="mt-3 flex items-center gap-1 flex-wrap">
                        {shipment.route.map((stop, idx) => (
                          <div key={idx} className="flex items-center gap-1">
                            <Badge
                              variant={
                                stop === shipment.currentLocation
                                  ? "default"
                                  : idx <= shipment.currentRouteIndex
                                  ? "secondary"
                                  : "outline"
                              }
                              className="text-[10px]"
                            >
                              {getAirportInfo(stop).code}
                            </Badge>
                            {idx < shipment.route.length - 1 && (
                              <ArrowRight className="h-2 w-2 text-muted-foreground" />
                            )}
                          </div>
                        ))}
                      </div>
                    </CardContent>
                  </Card>
                )
              })}
            </div>
          )}
        </ScrollArea>

        <div className="pt-4 border-t border-border mt-4">
          <div className="grid grid-cols-3 gap-4 text-center">
            <div>
              <p className="text-2xl font-bold font-mono text-status-amber">
                {inTransitShipments.length}
              </p>
              <p className="text-xs text-muted-foreground">Total en tránsito</p>
            </div>
            <div>
              <p className="text-2xl font-bold font-mono text-status-red">
                {inTransitShipments.filter(
                  (s) => getProgressToDeadline(s.createdAt, s.deadline) > 80
                ).length}
              </p>
              <p className="text-xs text-muted-foreground">Urgentes</p>
            </div>
            <div>
              <p className="text-2xl font-bold font-mono">
                {inTransitShipments.reduce((acc, s) => acc + s.quantity, 0)}
              </p>
              <p className="text-xs text-muted-foreground">Maletas totales</p>
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
