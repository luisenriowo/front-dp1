"use client"

import { SimulationMetrics, Airport, CollapseEvent, getStatusColor } from "@/lib/simulation-data"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Progress } from "@/components/ui/progress"
import { cn } from "@/lib/utils"
import {
  Package,
  CheckCircle2,
  AlertTriangle,
  Truck,
  Clock,
  TrendingUp,
  Zap,
} from "lucide-react"

interface MetricsPanelProps {
  metrics: SimulationMetrics
  airports: Airport[]
  currentDay: number
  totalDays: number
  collapseEvents?: CollapseEvent[]
}

export function MetricsPanel({
  metrics,
  airports,
  currentDay,
  totalDays,
  collapseEvents = [],
}: MetricsPanelProps) {
  const activeCollapses = collapseEvents.filter(e => !e.resolved).length
  const totalCollapses = collapseEvents.length
  const deliveryRate =
    metrics.totalShipments > 0
      ? ((metrics.deliveredOnTime / metrics.totalShipments) * 100).toFixed(1)
      : "0.0"

  const onTimeRate =
    metrics.deliveredOnTime + metrics.deliveredLate > 0
      ? (
          (metrics.deliveredOnTime /
            (metrics.deliveredOnTime + metrics.deliveredLate)) *
          100
        ).toFixed(1)
      : "100.0"

  return (
    <div className="space-y-4">
      {/* Progreso de simulación */}
      <Card className="bg-card border-border">
        <CardHeader className="pb-2">
          <CardTitle className="text-sm flex items-center gap-2">
            <Clock className="h-4 w-4 text-primary" />
            Progreso de Simulación
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-2">
            <div className="flex justify-between text-sm">
              <span className="text-muted-foreground">Día {currentDay} de {totalDays}</span>
              <span className="font-mono">{((currentDay / totalDays) * 100).toFixed(0)}%</span>
            </div>
            <Progress value={(currentDay / totalDays) * 100} className="h-2" />
          </div>
        </CardContent>
      </Card>

      {/* KPIs principales */}
      <div className="grid grid-cols-2 gap-3">
        <Card className="bg-card border-border">
          <CardContent className="pt-4">
            <div className="flex items-center gap-2 mb-2">
              <Package className="h-4 w-4 text-primary" />
              <span className="text-xs text-muted-foreground">Total Envíos</span>
            </div>
            <p className="text-2xl font-bold font-mono">{metrics.totalShipments}</p>
          </CardContent>
        </Card>

        <Card className="bg-card border-border">
          <CardContent className="pt-4">
            <div className="flex items-center gap-2 mb-2">
              <CheckCircle2 className="h-4 w-4 text-status-green" />
              <span className="text-xs text-muted-foreground">A Tiempo</span>
            </div>
            <p className="text-2xl font-bold font-mono text-status-green">
              {metrics.deliveredOnTime}
            </p>
          </CardContent>
        </Card>

        <Card className="bg-card border-border">
          <CardContent className="pt-4">
            <div className="flex items-center gap-2 mb-2">
              <AlertTriangle className="h-4 w-4 text-status-red" />
              <span className="text-xs text-muted-foreground">Retrasados</span>
            </div>
            <p className="text-2xl font-bold font-mono text-status-red">
              {metrics.deliveredLate}
            </p>
          </CardContent>
        </Card>

        <Card className="bg-card border-border">
          <CardContent className="pt-4">
            <div className="flex items-center gap-2 mb-2">
              <Truck className="h-4 w-4 text-status-amber" />
              <span className="text-xs text-muted-foreground">En Tránsito</span>
            </div>
            <p className="text-2xl font-bold font-mono text-status-amber">
              {metrics.inTransit}
            </p>
          </CardContent>
        </Card>

        <Card className={cn("border-border col-span-2", activeCollapses > 0 ? "bg-red-950/40 border-red-500/30" : "bg-card")}>
          <CardContent className="pt-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Zap className={cn("h-4 w-4", activeCollapses > 0 ? "text-red-400" : "text-muted-foreground")} />
                <span className="text-xs text-muted-foreground">Colapsos Logísticos</span>
              </div>
              {activeCollapses > 0 && (
                <span className="text-xs text-red-400 font-medium animate-pulse">
                  {activeCollapses} activo{activeCollapses > 1 ? "s" : ""}
                </span>
              )}
            </div>
            <div className="flex items-baseline gap-2 mt-2">
              <p className={cn("text-2xl font-bold font-mono", activeCollapses > 0 ? "text-red-400" : "text-muted-foreground")}>
                {totalCollapses}
              </p>
              <span className="text-xs text-muted-foreground">total</span>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Tasas de desempeño */}
      <Card className="bg-card border-border">
        <CardHeader className="pb-2">
          <CardTitle className="text-sm flex items-center gap-2">
            <TrendingUp className="h-4 w-4 text-primary" />
            Desempeño
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <div className="flex justify-between text-sm">
              <span className="text-muted-foreground">Tasa de Entrega</span>
              <span className="font-mono">{deliveryRate}%</span>
            </div>
            <Progress
              value={parseFloat(deliveryRate)}
              className={cn(
                "h-2",
                parseFloat(deliveryRate) >= 80
                  ? "[&>div]:bg-status-green"
                  : parseFloat(deliveryRate) >= 50
                  ? "[&>div]:bg-status-amber"
                  : "[&>div]:bg-status-red"
              )}
            />
          </div>
          <div className="space-y-2">
            <div className="flex justify-between text-sm">
              <span className="text-muted-foreground">Puntualidad</span>
              <span className="font-mono">{onTimeRate}%</span>
            </div>
            <Progress
              value={parseFloat(onTimeRate)}
              className={cn(
                "h-2",
                parseFloat(onTimeRate) >= 90
                  ? "[&>div]:bg-status-green"
                  : parseFloat(onTimeRate) >= 70
                  ? "[&>div]:bg-status-amber"
                  : "[&>div]:bg-status-red"
              )}
            />
          </div>
          <div className="space-y-2">
            <div className="flex justify-between text-sm">
              <span className="text-muted-foreground">Tiempo Promedio</span>
              <span className="font-mono">{metrics.averageDeliveryTime.toFixed(1)} días</span>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Utilización de almacenes */}
      <Card className="bg-card border-border">
        <CardHeader className="pb-2">
          <CardTitle className="text-sm">Almacenes Críticos</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-2 max-h-48 overflow-y-auto">
            {airports
              .map((airport) => ({
                airport,
                utilization: (airport.currentStorage / airport.storageCapacity) * 100,
              }))
              .sort((a, b) => b.utilization - a.utilization)
              .slice(0, 6)
              .map(({ airport, utilization }) => {
                const status = getStatusColor(utilization)
                return (
                  <div key={airport.id} className="space-y-1">
                    <div className="flex justify-between text-xs">
                      <span className="text-muted-foreground">
                        {airport.code} - {airport.city}
                      </span>
                      <span
                        className={cn(
                          "font-mono",
                          status === "green" && "text-status-green",
                          status === "amber" && "text-status-amber",
                          status === "red" && "text-status-red"
                        )}
                      >
                        {utilization.toFixed(0)}%
                      </span>
                    </div>
                    <Progress
                      value={utilization}
                      className={cn(
                        "h-1.5",
                        status === "green" && "[&>div]:bg-status-green",
                        status === "amber" && "[&>div]:bg-status-amber",
                        status === "red" && "[&>div]:bg-status-red"
                      )}
                    />
                  </div>
                )
              })}
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
