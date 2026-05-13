"use client"

import { Flight, Airport } from "@/lib/simulation-data"
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
import { Plane, MapPin, Clock, Users, ArrowRight } from "lucide-react"

interface ActiveFlightsDialogProps {
  flights: Flight[]
  activeFlights: { flight: Flight; progress: number; shipments: number }[]
  airports: Airport[]
  currentHour: number
  children: React.ReactNode
}

export function ActiveFlightsDialog({
  flights,
  activeFlights,
  airports,
  currentHour,
  children,
}: ActiveFlightsDialogProps) {
  const getAirportInfo = (id: string) => {
    const airport = airports.find((a) => a.id === id)
    return airport ? { code: airport.code, city: airport.city, continent: airport.continent } : { code: id, city: id, continent: "Unknown" }
  }

  const getFlightType = (origin: string, destination: string) => {
    const originAirport = airports.find((a) => a.id === origin)
    const destAirport = airports.find((a) => a.id === destination)
    if (originAirport && destAirport) {
      return originAirport.continent === destAirport.continent
        ? "Continental"
        : "Intercontinental"
    }
    return "Unknown"
  }

  const formatTime = (hour: number) => {
    return `${String(hour % 24).padStart(2, "0")}:00`
  }

  const getEstimatedArrival = (departureTime: number, duration: number) => {
    const arrivalHour = (departureTime + Math.ceil(duration * 24)) % 24
    return formatTime(arrivalHour)
  }

  // Separate active flights from scheduled flights
  const scheduledFlights = flights.filter(
    (f) => !activeFlights.some((af) => af.flight.id === f.id)
  )

  // Next departures (flights departing in the next 6 hours)
  const nextDepartures = scheduledFlights
    .filter((f) => {
      const timeDiff = (f.departureTime - currentHour + 24) % 24
      return timeDiff <= 6 && timeDiff > 0
    })
    .sort((a, b) => {
      const aDiff = (a.departureTime - currentHour + 24) % 24
      const bDiff = (b.departureTime - currentHour + 24) % 24
      return aDiff - bDiff
    })

  return (
    <Dialog>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="max-w-3xl max-h-[80vh] overflow-hidden flex flex-col">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Plane className="h-5 w-5 text-primary" />
            Vuelos Activos
            <Badge variant="secondary" className="ml-2">
              {activeFlights.length} en vuelo
            </Badge>
          </DialogTitle>
        </DialogHeader>

        <ScrollArea className="flex-1 pr-4">
          {/* Active Flights */}
          <div className="mb-6">
            <h3 className="text-sm font-medium mb-3 flex items-center gap-2">
              <div className="h-2 w-2 rounded-full bg-status-green animate-pulse" />
              Vuelos en Curso
            </h3>
            {activeFlights.length === 0 ? (
              <Card className="border-dashed">
                <CardContent className="py-8 text-center text-muted-foreground">
                  <Plane className="h-8 w-8 mx-auto mb-2 opacity-50" />
                  <p>No hay vuelos activos en este momento</p>
                </CardContent>
              </Card>
            ) : (
              <div className="space-y-3">
                {activeFlights.map(({ flight, progress, shipments }) => {
                  const flightType = getFlightType(flight.origin, flight.destination)
                  const originInfo = getAirportInfo(flight.origin)
                  const destInfo = getAirportInfo(flight.destination)

                  return (
                    <Card key={flight.id} className="border-primary/30">
                      <CardContent className="py-4">
                        <div className="flex items-start justify-between mb-3">
                          <div className="flex items-center gap-2">
                            <Plane className="h-4 w-4 text-primary" />
                            <span className="font-mono text-sm font-medium">
                              {flight.id.toUpperCase()}
                            </span>
                            <Badge
                              variant={
                                flightType === "Intercontinental"
                                  ? "default"
                                  : "secondary"
                              }
                              className="text-[10px]"
                            >
                              {flightType}
                            </Badge>
                          </div>
                          <Badge variant="outline" className="text-status-green">
                            En Vuelo
                          </Badge>
                        </div>

                        <div className="flex items-center gap-4 mb-4">
                          <div className="text-center">
                            <p className="text-lg font-bold">{originInfo.code}</p>
                            <p className="text-[10px] text-muted-foreground">
                              {originInfo.city}
                            </p>
                          </div>
                          <div className="flex-1 flex items-center gap-2">
                            <div className="h-px flex-1 bg-border" />
                            <Plane className="h-4 w-4 text-primary rotate-90" />
                            <div className="h-px flex-1 bg-border" />
                          </div>
                          <div className="text-center">
                            <p className="text-lg font-bold">{destInfo.code}</p>
                            <p className="text-[10px] text-muted-foreground">
                              {destInfo.city}
                            </p>
                          </div>
                        </div>

                        <div className="space-y-2 mb-3">
                          <div className="flex justify-between text-xs">
                            <span className="text-muted-foreground">
                              Progreso del vuelo
                            </span>
                            <span className="font-mono">
                              {(progress * 100).toFixed(0)}%
                            </span>
                          </div>
                          <Progress
                            value={progress * 100}
                            className="h-2 [&>div]:bg-primary"
                          />
                        </div>

                        <div className="grid grid-cols-4 gap-2 text-xs">
                          <div>
                            <p className="text-muted-foreground">Salida</p>
                            <p className="font-mono">
                              {formatTime(flight.departureTime)}
                            </p>
                          </div>
                          <div>
                            <p className="text-muted-foreground">Llegada Est.</p>
                            <p className="font-mono">
                              {getEstimatedArrival(
                                flight.departureTime,
                                flight.duration
                              )}
                            </p>
                          </div>
                          <div>
                            <p className="text-muted-foreground">Capacidad</p>
                            <p className="font-mono">{flight.capacity}</p>
                          </div>
                          <div>
                            <p className="text-muted-foreground">Envíos</p>
                            <p className="font-mono">{shipments}</p>
                          </div>
                        </div>
                      </CardContent>
                    </Card>
                  )
                })}
              </div>
            )}
          </div>

          {/* Next Departures */}
          <div>
            <h3 className="text-sm font-medium mb-3 flex items-center gap-2">
              <Clock className="h-4 w-4 text-muted-foreground" />
              Próximas Salidas (6h)
            </h3>
            {nextDepartures.length === 0 ? (
              <Card className="border-dashed">
                <CardContent className="py-6 text-center text-muted-foreground">
                  <p className="text-sm">No hay vuelos programados en las próximas 6 horas</p>
                </CardContent>
              </Card>
            ) : (
              <div className="space-y-2">
                {nextDepartures.slice(0, 8).map((flight) => {
                  const originInfo = getAirportInfo(flight.origin)
                  const destInfo = getAirportInfo(flight.destination)
                  const timeDiff = (flight.departureTime - currentHour + 24) % 24

                  return (
                    <Card key={flight.id} className="border-border">
                      <CardContent className="py-3 flex items-center justify-between">
                        <div className="flex items-center gap-3">
                          <div className="text-center min-w-[50px]">
                            <p className="font-mono font-medium">
                              {formatTime(flight.departureTime)}
                            </p>
                            <p className="text-[10px] text-muted-foreground">
                              en {timeDiff}h
                            </p>
                          </div>
                          <div className="flex items-center gap-2">
                            <Badge variant="outline" className="text-xs">
                              {originInfo.code}
                            </Badge>
                            <ArrowRight className="h-3 w-3 text-muted-foreground" />
                            <Badge variant="outline" className="text-xs">
                              {destInfo.code}
                            </Badge>
                          </div>
                        </div>
                        <div className="flex items-center gap-2 text-xs text-muted-foreground">
                          <Users className="h-3 w-3" />
                          <span>{flight.capacity}</span>
                        </div>
                      </CardContent>
                    </Card>
                  )
                })}
              </div>
            )}
          </div>
        </ScrollArea>

        <div className="pt-4 border-t border-border mt-4">
          <div className="grid grid-cols-4 gap-4 text-center">
            <div>
              <p className="text-2xl font-bold font-mono text-primary">
                {activeFlights.length}
              </p>
              <p className="text-xs text-muted-foreground">En vuelo</p>
            </div>
            <div>
              <p className="text-2xl font-bold font-mono">
                {nextDepartures.length}
              </p>
              <p className="text-xs text-muted-foreground">Próximos</p>
            </div>
            <div>
              <p className="text-2xl font-bold font-mono">
                {flights.length}
              </p>
              <p className="text-xs text-muted-foreground">Total rutas</p>
            </div>
            <div>
              <p className="text-2xl font-bold font-mono">
                {activeFlights.reduce((acc, af) => acc + af.shipments, 0)}
              </p>
              <p className="text-xs text-muted-foreground">Envíos en vuelo</p>
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
