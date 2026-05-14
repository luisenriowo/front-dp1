"use client"

import { useState, useEffect, useCallback, useRef } from "react"
import {
  Airport,
  Flight,
  Shipment,
  SimulationMetrics,
  CollapseEvent,
  CollapseReason,
  CollapseSeverity,
  initialAirports,
  initialFlights,
  generateRandomShipments,
} from "@/lib/simulation-data"
import { airportsApi, flightsApi, shipmentsApi } from "@/lib/api"
import type { CreateShipmentDto } from "@/lib/api"
import { WorldMap } from "./world-map"
import { MetricsPanel } from "./metrics-panel"
import { AirportDetail } from "./airport-detail"
import { ShipmentsTable } from "./shipments-table"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Plane, Calendar, MapPin, Truck, Search, PlaneTakeoff,
  ChevronLeft, ChevronRight, PanelLeftClose, PanelRightClose,
  AlertTriangle, Zap,
} from "lucide-react"
import { InTransitDialog } from "./in-transit-dialog"
import { ShipmentDetailDialog } from "./shipment-detail-dialog"
import { ActiveFlightsDialog } from "./active-flights-dialog"

interface SimulationConfig {
  totalDays: number
  shipmentsPerDay: number
  simulationSpeed: number
}

const DEFAULT_CONFIG: SimulationConfig = {
  totalDays: 5,
  shipmentsPerDay: 25,
  simulationSpeed: 500,
}

const COLLAPSE_REASONS: CollapseReason[] = ["storm", "strike", "technical"]

const COLLAPSE_REASON_LABELS: Record<CollapseReason, string> = {
  overflow: "Saturación",
  storm: "Tormenta",
  strike: "Huelga",
  technical: "Falla Técnica",
}

const COLLAPSE_SEVERITY_LABELS: Record<CollapseSeverity, string> = {
  mild: "Leve",
  critical: "Crítico",
  catastrophic: "Catastrófico",
}

const BACKEND_STATUS_MAP: Record<string, Shipment["status"]> = {
  PENDING: "pending",
  IN_TRANSIT: "in-transit",
  DELIVERED: "delivered",
  DELIVERED_LATE: "delayed",
  NO_ROUTE: "pending",
}

export function SimulationDashboard() {
  const [config] = useState<SimulationConfig>(DEFAULT_CONFIG)
  const [isRunning, setIsRunning] = useState(false)
  const [hasStarted, setHasStarted] = useState(false)
  const [leftPanelOpen, setLeftPanelOpen] = useState(true)
  const [rightPanelOpen, setRightPanelOpen] = useState(true)
  const [dataSource, setDataSource] = useState<"api" | "mock">("mock")

  // Estado de la simulación
  const [currentDay, setCurrentDay] = useState(1)
  const [currentHour, setCurrentHour] = useState(0)
  const [airports, setAirports] = useState<Airport[]>(initialAirports)
  const [flights, setFlights] = useState<Flight[]>(initialFlights)
  const [shipments, setShipments] = useState<Shipment[]>([])
  const [activeFlights, setActiveFlights] = useState<
    { flight: Flight; progress: number; shipments: number }[]
  >([])
  const [selectedAirport, setSelectedAirport] = useState<string | null>(null)
  const [metrics, setMetrics] = useState<SimulationMetrics>({
    totalShipments: 0,
    deliveredOnTime: 0,
    deliveredLate: 0,
    inTransit: 0,
    pending: 0,
    averageDeliveryTime: 0,
    warehouseUtilization: {},
  })

  // Estado de colapso
  const [collapseEvents, setCollapseEvents] = useState<CollapseEvent[]>([])
  const [cancelledFlightIds, setCancelledFlightIds] = useState<Set<string>>(new Set())

  const tickRef = useRef<NodeJS.Timeout | null>(null)
  const shipmentsRef = useRef(shipments)
  const airportsRef = useRef(airports)
  const collapseEventsRef = useRef<CollapseEvent[]>([])
  const cancelledFlightIdsRef = useRef<Set<string>>(new Set())
  const pendingCollapseReasonRef = useRef<Map<string, CollapseReason>>(new Map())
  const burstCounterRef = useRef(0)
  const currentDayRef = useRef(currentDay)
  const currentHourRef = useRef(currentHour)
  // Días para los que ya se generaron envíos (evita duplicados)
  const generatedDaysRef = useRef<Set<number>>(new Set())

  useEffect(() => { shipmentsRef.current = shipments }, [shipments])
  useEffect(() => { airportsRef.current = airports }, [airports])
  useEffect(() => { collapseEventsRef.current = collapseEvents }, [collapseEvents])
  useEffect(() => { cancelledFlightIdsRef.current = cancelledFlightIds }, [cancelledFlightIds])
  useEffect(() => { currentDayRef.current = currentDay }, [currentDay])
  useEffect(() => { currentHourRef.current = currentHour }, [currentHour])

  // Detectar y gestionar colapsos basados en el estado de aeropuertos
  useEffect(() => {
    const prevEvents = collapseEventsRef.current
    let changed = false
    const newEvents = prevEvents.map(e => ({ ...e }))
    const newCancelled = new Set(cancelledFlightIdsRef.current)

    airports.forEach(airport => {
      const utilization = airport.currentStorage / airport.storageCapacity
      const existingIdx = newEvents.findIndex(e => e.airportId === airport.id && !e.resolved)

      if (utilization >= 0.90 && existingIdx === -1) {
        const severity: CollapseSeverity =
          utilization >= 1.0 ? "catastrophic" :
          utilization >= 0.95 ? "critical" : "mild"

        const outboundFlights = flights.filter(f => f.origin === airport.id)
        const cancelRate =
          severity === "catastrophic" ? 1.0 :
          severity === "critical" ? 0.6 : 0.35
        const toCancel = outboundFlights
          .slice(0, Math.ceil(outboundFlights.length * cancelRate))
          .map(f => f.id)

        toCancel.forEach(id => newCancelled.add(id))

        const reason = pendingCollapseReasonRef.current.get(airport.id) ?? "overflow"
        pendingCollapseReasonRef.current.delete(airport.id)

        newEvents.push({
          id: `collapse-${airport.id}-d${currentDayRef.current}h${currentHourRef.current}`,
          airportId: airport.id,
          reason,
          severity,
          startDay: currentDayRef.current,
          startHour: currentHourRef.current,
          cancelledFlightIds: toCancel,
          affectedShipmentCount: 0,
          resolved: false,
        })
        changed = true

      } else if (existingIdx !== -1 && !newEvents[existingIdx].resolved) {
        if (utilization < 0.70) {
          // Resolver colapso
          newEvents[existingIdx].cancelledFlightIds.forEach(id => newCancelled.delete(id))
          newEvents[existingIdx] = { ...newEvents[existingIdx], resolved: true }
          changed = true
        } else {
          // Actualizar severidad
          const newSeverity: CollapseSeverity =
            utilization >= 1.0 ? "catastrophic" :
            utilization >= 0.95 ? "critical" : "mild"

          if (newSeverity !== newEvents[existingIdx].severity) {
            const outboundFlights = flights.filter(f => f.origin === airport.id)
            const cancelRate =
              newSeverity === "catastrophic" ? 1.0 :
              newSeverity === "critical" ? 0.6 : 0.35
            const newToCancel = outboundFlights
              .slice(0, Math.ceil(outboundFlights.length * cancelRate))
              .map(f => f.id)

            newEvents[existingIdx].cancelledFlightIds.forEach(id => newCancelled.delete(id))
            newToCancel.forEach(id => newCancelled.add(id))
            newEvents[existingIdx] = {
              ...newEvents[existingIdx],
              severity: newSeverity,
              cancelledFlightIds: newToCancel,
            }
            changed = true
          }
        }
      }
    })

    if (changed) {
      setCollapseEvents(newEvents)
      setCancelledFlightIds(newCancelled)
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [airports])

  // Disparar colapso manual en un aeropuerto aleatorio
  const triggerManualCollapse = useCallback(() => {
    const activeCollapseIds = new Set(
      collapseEventsRef.current.filter(e => !e.resolved).map(e => e.airportId)
    )
    const candidates = airportsRef.current.filter(a => !activeCollapseIds.has(a.id))
    if (candidates.length === 0) return

    const target = candidates[Math.floor(Math.random() * candidates.length)]
    const reason = COLLAPSE_REASONS[Math.floor(Math.random() * COLLAPSE_REASONS.length)]

    pendingCollapseReasonRef.current.set(target.id, reason)

    // Inyectar oleada de envíos que llena el almacén
    const burst: Shipment[] = []
    for (let i = 0; i < 85; i++) {
      burstCounterRef.current++
      let dest = airportsRef.current[Math.floor(Math.random() * airportsRef.current.length)]
      while (dest.id === target.id) {
        dest = airportsRef.current[Math.floor(Math.random() * airportsRef.current.length)]
      }
      const createdAt = new Date(2026, 2, 31 + currentDayRef.current, currentHourRef.current)
      const deadline = new Date(createdAt)
      deadline.setDate(deadline.getDate() + 2)
      burst.push({
        id: `burst-${burstCounterRef.current}-${i}`,
        origin: target.id,
        destination: dest.id,
        quantity: Math.floor(Math.random() * 8) + 3,
        createdAt,
        deadline,
        currentLocation: target.id,
        status: "pending",
        route: [target.id, dest.id],
        currentRouteIndex: 0,
      })
    }

    setShipments(prev => [...prev, ...burst])
  }, [])

  const calculateMetrics = useCallback((currentShipments: Shipment[]): SimulationMetrics => {
    let deliveredOnTime = 0, deliveredLate = 0, inTransit = 0, pending = 0, totalDuration = 0
    for (const s of currentShipments) {
      const duration = (s.deadline.getTime() - s.createdAt.getTime()) / (1000 * 60 * 60 * 24)
      if (s.status === "delivered") { deliveredOnTime++; totalDuration += duration }
      else if (s.status === "delayed") { deliveredLate++; totalDuration += duration }
      else if (s.status === "in-transit") { inTransit++ }
      else { pending++ }
    }
    const totalDelivered = deliveredOnTime + deliveredLate
    return {
      totalShipments: currentShipments.length,
      deliveredOnTime,
      deliveredLate,
      inTransit,
      pending,
      averageDeliveryTime: totalDelivered > 0 ? totalDuration / totalDelivered : 0,
      warehouseUtilization: {},
    }
  }, [])

  // Construye los DTOs con origen/destino aleatorios y llama al back para obtener rutas
  const generateShipmentsForDay = useCallback(async (day: number) => {
    if (generatedDaysRef.current.has(day)) return
    generatedDaysRef.current.add(day)

    const airports = airportsRef.current
    const dtos: CreateShipmentDto[] = Array.from({ length: config.shipmentsPerDay }, (_, i) => {
      const originIdx = Math.floor(Math.random() * airports.length)
      let destIdx = Math.floor(Math.random() * airports.length)
      while (destIdx === originIdx) destIdx = Math.floor(Math.random() * airports.length)

      const origin = airports[originIdx]
      const destination = airports[destIdx]
      const sameCont = origin.continent === destination.continent
      const deadlineDays = sameCont ? 1 : 2
      const createdAt = new Date(2026, 2, 31 + day)
      const deadline = new Date(createdAt)
      deadline.setDate(deadline.getDate() + deadlineDays)

      return {
        origin: origin.id,
        destination: destination.id,
        quantity: Math.floor(Math.random() * 10) + 1,
        deadline: deadline.toISOString(),
      }
    })

    try {
      const fromApi = await shipmentsApi.createBulk(dtos)
      if (fromApi.length > 0) {
        const normalized = fromApi.map((s: any) => ({
          ...s,
          createdAt: new Date(s.createdAt),
          deadline: new Date(s.deadline),
          status: BACKEND_STATUS_MAP[String(s.status)] ?? "pending",
        }))
        setShipments((prev) => [...prev, ...normalized])
        setDataSource("api")
        return
      }
    } catch {
      // El back no está disponible — fallback a generación local
    }

    // Fallback: generación local sin ruteo del backend
    const local = generateRandomShipments(airports, config.shipmentsPerDay, day)
    setShipments((prev) => [...prev, ...local])
  }, [config.shipmentsPerDay])

  const processTick = useCallback(() => {
    const nextHour = currentHourRef.current + 1

    if (nextHour >= 24) {
      const nextDay = currentDayRef.current + 1
      if (nextDay > config.totalDays) {
        setIsRunning(false)
        return
      }
      setCurrentDay(nextDay)
      setCurrentHour(0)
      return
    }

    setCurrentHour(nextHour)

    // Vuelos activos (excluyendo cancelados)
    const currentFlights = flights.filter((f) => {
      const flightEnd = f.departureTime + f.duration * 24
      return nextHour >= f.departureTime && nextHour < flightEnd
    })

    setActiveFlights(
      currentFlights.map((flight) => {
        const elapsed = nextHour - flight.departureTime
        const progress = Math.min(elapsed / (flight.duration * 24), 1)
        const shipmentsOnFlight = shipmentsRef.current.filter(
          (s) =>
            s.status === "in-transit" &&
            s.currentLocation === flight.origin &&
            s.route.includes(flight.destination)
        ).length
        return { flight, progress, shipments: shipmentsOnFlight }
      })
    )

    // Procesar envíos (omitir vuelos cancelados)
    setShipments((prevShipments) => {
      return prevShipments.map((shipment) => {
        if (shipment.status === "delivered" || shipment.status === "delayed") {
          return shipment
        }

        const availableFlight = flights.find(
          (f) =>
            f.origin === shipment.currentLocation &&
            shipment.route.includes(f.destination) &&
            f.departureTime === nextHour &&
            !cancelledFlightIdsRef.current.has(f.id)
        )

        if (availableFlight && shipment.status === "pending") {
          return { ...shipment, status: "in-transit" as const }
        }

        if (shipment.status === "in-transit") {
          const currentFlight = flights.find(
            (f) =>
              f.origin === shipment.currentLocation &&
              shipment.route.includes(f.destination)
          )

          if (currentFlight) {
            const arrivalHour = currentFlight.departureTime + currentFlight.duration * 24
            if (nextHour >= arrivalHour) {
              const newLocation = currentFlight.destination

              if (newLocation === shipment.destination) {
                const currentDate = new Date(2026, 2, 31 + currentDayRef.current, nextHour)
                const isOnTime = currentDate <= shipment.deadline
                return {
                  ...shipment,
                  currentLocation: newLocation,
                  status: isOnTime ? ("delivered" as const) : ("delayed" as const),
                }
              }

              return {
                ...shipment,
                currentLocation: newLocation,
                currentRouteIndex: shipment.currentRouteIndex + 1,
                status: "pending" as const,
              }
            }
          }
        }

        return shipment
      })
    })

    // Actualizar almacenes (sin cap rígido para permitir overflow)
    setAirports((prevAirports) => {
      return prevAirports.map((airport) => {
        const shipmentsAtAirport = shipmentsRef.current.filter(
          (s) =>
            s.currentLocation === airport.id &&
            (s.status === "pending" || s.status === "in-transit")
        )
        const totalBags = shipmentsAtAirport.reduce((acc, s) => acc + s.quantity, 0)
        return { ...airport, currentStorage: Math.min(totalBags, airport.storageCapacity * 2) }
      })
    })
  }, [config.totalDays, flights])

  // Genera envíos para el día actual (via API con fallback local)
  useEffect(() => {
    if (hasStarted) {
      generateShipmentsForDay(currentDay)
    }
  }, [currentDay, hasStarted, generateShipmentsForDay])

  // Carga aeropuertos y vuelos desde el backend; si falla, usa datos mock
  useEffect(() => {
    Promise.allSettled([airportsApi.getAll(), flightsApi.getAll()]).then(
      ([airportsResult, flightsResult]) => {
        if (airportsResult.status === "fulfilled") {
          setAirports(airportsResult.value)
          setDataSource("api")
        }
        if (flightsResult.status === "fulfilled") {
          setFlights(flightsResult.value)
          setDataSource("api")
        }
      }
    )
  }, [])

  useEffect(() => {
    if (!hasStarted) {
      setHasStarted(true)
      setIsRunning(true)
    }
  }, [hasStarted])

  useEffect(() => {
    if (isRunning) {
      tickRef.current = setInterval(() => {
        processTick()
      }, config.simulationSpeed)
    }
    return () => {
      if (tickRef.current) clearInterval(tickRef.current)
    }
  }, [isRunning, processTick, config.simulationSpeed])

  useEffect(() => {
    setMetrics(calculateMetrics(shipments))
  }, [shipments, calculateMetrics])

  const selectedAirportData = airports.find((a) => a.id === selectedAirport)
  const currentDate = new Date(2026, 2, 31 + currentDay, currentHour)
  const activeCollapses = collapseEvents.filter(e => !e.resolved)

  const incomingShipments = selectedAirport
    ? shipments.filter((s) => s.destination === selectedAirport && s.status !== "delivered")
    : []

  const outgoingShipments = selectedAirport
    ? shipments.filter(
        (s) => s.origin === selectedAirport || s.currentLocation === selectedAirport
      )
    : []

  return (
    <div className="min-h-screen bg-background p-4">
      {/* Header */}
      <header className="mb-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2">
              <Plane className="h-6 w-6 text-primary" />
              <h1 className="text-xl font-bold text-foreground">PPlanning</h1>
            </div>
            <Badge variant="outline" className="text-xs">
              Simulación de Periodo
            </Badge>
          </div>
          <div className="flex items-center gap-4 text-sm">
            <div className="flex items-center gap-2 text-muted-foreground">
              <Calendar className="h-4 w-4" />
              <span>Día {currentDay} de {config.totalDays}</span>
            </div>
            <div className="flex items-center gap-2 text-muted-foreground">
              <span className="font-mono">
                {String(currentHour).padStart(2, "0")}:00
              </span>
            </div>
            {activeCollapses.length > 0 && (
              <Badge className="bg-red-500/20 text-red-400 border-red-500/40 animate-pulse gap-1">
                <AlertTriangle className="h-3 w-3" />
                {activeCollapses.length} Colapso{activeCollapses.length > 1 ? "s" : ""}
              </Badge>
            )}
            <Badge
              className={
                isRunning
                  ? "bg-status-green/20 text-status-green border-status-green/30"
                  : currentDay >= config.totalDays
                  ? "bg-primary/20 text-primary border-primary/30"
                  : "bg-status-amber/20 text-status-amber border-status-amber/30"
              }
            >
              {isRunning
                ? "En Ejecución"
                : currentDay >= config.totalDays
                ? "Completado"
                : "Pausado"}
            </Badge>
            <Badge variant="outline" className="text-xs gap-1">
              <span
                className={`inline-block h-1.5 w-1.5 rounded-full ${
                  dataSource === "api" ? "bg-status-green" : "bg-status-amber"
                }`}
              />
              {dataSource === "api" ? "API" : "Mock"}
            </Badge>
          </div>
        </div>
      </header>

      {/* Banner de colapso logístico */}
      {activeCollapses.length > 0 && (
        <div className="mb-4 rounded-lg border border-red-500/40 bg-red-500/10 p-3">
          <div className="flex items-center gap-2 mb-2">
            <AlertTriangle className="h-4 w-4 text-red-400 animate-pulse" />
            <span className="text-sm font-semibold text-red-400">
              COLAPSO LOGÍSTICO — {activeCollapses.length} aeropuerto{activeCollapses.length > 1 ? "s" : ""} afectado{activeCollapses.length > 1 ? "s" : ""}
            </span>
          </div>
          <div className="flex flex-wrap gap-2">
            {activeCollapses.map(event => {
              const airport = airports.find(a => a.id === event.airportId)
              const severityColor =
                event.severity === "catastrophic" ? "bg-red-600 border-red-400" :
                event.severity === "critical" ? "bg-orange-600 border-orange-400" :
                "bg-yellow-600 border-yellow-400"
              return (
                <div
                  key={event.id}
                  className={`inline-flex items-center gap-1.5 px-2 py-1 rounded text-xs text-white border ${severityColor}`}
                >
                  <span className="font-bold">{airport?.code}</span>
                  <span className="opacity-80">·</span>
                  <span>{COLLAPSE_REASON_LABELS[event.reason]}</span>
                  <span className="opacity-80">·</span>
                  <span className="font-semibold">{COLLAPSE_SEVERITY_LABELS[event.severity]}</span>
                  <span className="opacity-60">({event.cancelledFlightIds.length} vuelos)</span>
                </div>
              )
            })}
          </div>
        </div>
      )}

      {/* Action Buttons Bar */}
      <div className="mb-4 flex items-center gap-3">
        <InTransitDialog
          shipments={shipments}
          airports={airports}
          currentDate={currentDate}
        >
          <Button variant="outline" className="gap-2">
            <Truck className="h-4 w-4" />
            Envíos en Tránsito
            <Badge variant="secondary" className="ml-1">
              {metrics.inTransit}
            </Badge>
          </Button>
        </InTransitDialog>

        <ShipmentDetailDialog
          shipments={shipments}
          airports={airports}
          currentDate={currentDate}
        >
          <Button variant="outline" className="gap-2">
            <Search className="h-4 w-4" />
            Buscar Envío Específico
          </Button>
        </ShipmentDetailDialog>

        <ActiveFlightsDialog
          flights={flights}
          activeFlights={activeFlights}
          airports={airports}
          currentHour={currentHour}
        >
          <Button variant="outline" className="gap-2">
            <PlaneTakeoff className="h-4 w-4" />
            Vuelos Activos
            <Badge variant="secondary" className="ml-1">
              {activeFlights.filter(af => !cancelledFlightIds.has(af.flight.id)).length}
            </Badge>
          </Button>
        </ActiveFlightsDialog>

        <div className="ml-auto">
          <Button
            variant="outline"
            className="gap-2 border-red-500/40 text-red-400 hover:bg-red-500/10 hover:text-red-300"
            onClick={triggerManualCollapse}
            title="Inyecta una oleada de carga en un aeropuerto aleatorio, forzando un colapso"
          >
            <Zap className="h-4 w-4" />
            Simular Colapso
          </Button>
        </div>
      </div>

      {/* Layout principal */}
      <div className="flex gap-4 h-[calc(100vh-180px)]">
        {/* Panel izquierdo - Métricas */}
        <div
          className={`relative transition-all duration-300 ease-in-out ${
            leftPanelOpen ? "w-80" : "w-12"
          }`}
        >
          <Button
            variant="ghost"
            size="icon"
            className="absolute -right-3 top-4 z-10 h-6 w-6 rounded-full border bg-background shadow-md hover:bg-accent"
            onClick={() => setLeftPanelOpen(!leftPanelOpen)}
          >
            {leftPanelOpen ? <ChevronLeft className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
          </Button>

          {leftPanelOpen ? (
            <div className="h-full overflow-y-auto">
              <MetricsPanel
                metrics={metrics}
                airports={airports}
                currentDay={currentDay}
                totalDays={config.totalDays}
                collapseEvents={collapseEvents}
              />
            </div>
          ) : (
            <Card className="h-full bg-card border-border flex flex-col items-center py-4 gap-2">
              <Button
                variant="ghost"
                size="icon"
                className="h-8 w-8"
                onClick={() => setLeftPanelOpen(true)}
                title="Abrir Métricas"
              >
                <PanelLeftClose className="h-4 w-4" />
              </Button>
              <div className="flex-1 flex flex-col items-center justify-center gap-4">
                <div className="text-xs font-medium text-muted-foreground [writing-mode:vertical-lr] rotate-180">
                  Métricas
                </div>
              </div>
            </Card>
          )}
        </div>

        {/* Panel central - Mapa */}
        <div className="flex-1 flex flex-col gap-4 min-w-0">
          <Card className="flex-1 bg-card border-border">
            <CardHeader className="pb-2">
              <CardTitle className="text-sm flex items-center gap-2">
                <MapPin className="h-4 w-4 text-primary" />
                Mapa de Operaciones Global
              </CardTitle>
            </CardHeader>
            <CardContent className="h-[calc(100%-60px)]">
              <WorldMap
                airports={airports}
                flights={flights}
                activeFlights={activeFlights}
                selectedAirport={selectedAirport}
                onAirportClick={setSelectedAirport}
                collapseEvents={collapseEvents}
                cancelledFlightIds={cancelledFlightIds}
              />
            </CardContent>
          </Card>

          <div className="h-[300px]">
            <ShipmentsTable
              shipments={shipments}
              airports={airports}
              currentDate={currentDate}
            />
          </div>
        </div>

        {/* Panel derecho - Detalle de aeropuerto */}
        <div
          className={`relative transition-all duration-300 ease-in-out ${
            rightPanelOpen ? "w-80" : "w-12"
          }`}
        >
          <Button
            variant="ghost"
            size="icon"
            className="absolute -left-3 top-4 z-10 h-6 w-6 rounded-full border bg-background shadow-md hover:bg-accent"
            onClick={() => setRightPanelOpen(!rightPanelOpen)}
          >
            {rightPanelOpen ? <ChevronRight className="h-4 w-4" /> : <ChevronLeft className="h-4 w-4" />}
          </Button>

          {rightPanelOpen ? (
            <div className="h-full overflow-y-auto">
              {selectedAirportData ? (
                <AirportDetail
                  airport={selectedAirportData}
                  incomingShipments={incomingShipments}
                  outgoingShipments={outgoingShipments}
                  airports={airports}
                />
              ) : (
                <Card className="h-full bg-card border-border flex items-center justify-center">
                  <div className="text-center text-muted-foreground p-4">
                    <MapPin className="h-8 w-8 mx-auto mb-2 opacity-50" />
                    <p className="text-sm">Seleccione un aeropuerto en el mapa</p>
                    <p className="text-xs mt-1">para ver información detallada</p>
                  </div>
                </Card>
              )}
            </div>
          ) : (
            <Card className="h-full bg-card border-border flex flex-col items-center py-4 gap-2">
              <Button
                variant="ghost"
                size="icon"
                className="h-8 w-8"
                onClick={() => setRightPanelOpen(true)}
                title="Abrir Detalle"
              >
                <PanelRightClose className="h-4 w-4" />
              </Button>
              <div className="flex-1 flex flex-col items-center justify-center gap-4">
                <div className="text-xs font-medium text-muted-foreground [writing-mode:vertical-lr] rotate-180">
                  {selectedAirportData ? selectedAirportData.code : "Detalle"}
                </div>
              </div>
            </Card>
          )}
        </div>
      </div>
    </div>
  )
}
