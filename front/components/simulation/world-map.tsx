"use client"

import { memo } from "react"
import {
  ComposableMap,
  Geographies,
  Geography,
  Marker,
  Line,
} from "react-simple-maps"
import { Airport, Flight, CollapseEvent, getStatusColor } from "@/lib/simulation-data"

const geoUrl = "https://cdn.jsdelivr.net/npm/world-atlas@2/countries-110m.json"

interface WorldMapProps {
  airports: Airport[]
  flights: Flight[]
  activeFlights: { flight: Flight; progress: number; shipments: number }[]
  selectedAirport: string | null
  onAirportClick: (airportId: string) => void
  collapseEvents?: CollapseEvent[]
  cancelledFlightIds?: Set<string>
}

function WorldMapComponent({
  airports,
  activeFlights,
  flights,
  selectedAirport,
  onAirportClick,
  collapseEvents = [],
  cancelledFlightIds = new Set(),
}: WorldMapProps) {
  const getAirportById = (id: string) => airports.find((a) => a.id === id)

  const collapsingAirportIds = new Set(
    collapseEvents.filter(e => !e.resolved).map(e => e.airportId)
  )

  const collapseEventByAirport = new Map(
    collapseEvents.filter(e => !e.resolved).map(e => [e.airportId, e])
  )

  const getFlightPosition = (
    origin: [number, number],
    dest: [number, number],
    progress: number
  ): [number, number] => {
    return [
      origin[0] + (dest[0] - origin[0]) * progress,
      origin[1] + (dest[1] - origin[1]) * progress,
    ]
  }

  const getFlightAngle = (
    origin: [number, number],
    dest: [number, number]
  ): number => {
    const dx = dest[0] - origin[0]
    const dy = dest[1] - origin[1]
    return Math.atan2(dy, dx) * (180 / Math.PI) + 90
  }

  // Separar vuelos activos en cancelados y operativos
  const activeNonCancelled = activeFlights.filter(({ flight }) => !cancelledFlightIds.has(flight.id))
  const activeCancelled = activeFlights.filter(({ flight }) => cancelledFlightIds.has(flight.id))

  // Rutas canceladas que aún no tienen vuelo activo (vuelos bloqueados en tierra)
  const groundedFlights = flights.filter(f => cancelledFlightIds.has(f.id))

  return (
    <div className="relative w-full h-full rounded-lg overflow-hidden" style={{ backgroundColor: "#0a1628" }}>
      <ComposableMap
        projection="geoMercator"
        projectionConfig={{ scale: 130, center: [20, 30] }}
        className="w-full h-full"
      >
        <Geographies geography={geoUrl}>
          {({ geographies }) =>
            geographies.map((geo) => (
              <Geography
                key={geo.rsmKey}
                geography={geo}
                fill="#1e3a5f"
                stroke="#2d4a6f"
                strokeWidth={0.5}
                style={{
                  default: { outline: "none" },
                  hover: { outline: "none", fill: "#2a4d73" },
                  pressed: { outline: "none" },
                }}
              />
            ))
          }
        </Geographies>

        {/* Rutas de vuelos cancelados (en tierra) — rojo */}
        {groundedFlights.map((flight) => {
          const origin = getAirportById(flight.origin)
          const dest = getAirportById(flight.destination)
          if (!origin || !dest) return null
          return (
            <Line
              key={`grounded-${flight.id}`}
              from={origin.coordinates}
              to={dest.coordinates}
              stroke="#ef4444"
              strokeWidth={1.5}
              strokeDasharray="4 4"
              strokeLinecap="round"
              strokeOpacity={0.45}
            />
          )
        })}

        {/* Rutas de vuelos activos operativos — azul */}
        {activeNonCancelled.map(({ flight }) => {
          const origin = getAirportById(flight.origin)
          const dest = getAirportById(flight.destination)
          if (!origin || !dest) return null
          return (
            <Line
              key={flight.id}
              from={origin.coordinates}
              to={dest.coordinates}
              stroke="#60a5fa"
              strokeWidth={1.5}
              strokeDasharray="6 3"
              strokeLinecap="round"
              strokeOpacity={0.6}
            />
          )
        })}

        {/* Rutas de vuelos activos cancelados en vuelo — naranja */}
        {activeCancelled.map(({ flight }) => {
          const origin = getAirportById(flight.origin)
          const dest = getAirportById(flight.destination)
          if (!origin || !dest) return null
          return (
            <Line
              key={`cancelled-active-${flight.id}`}
              from={origin.coordinates}
              to={dest.coordinates}
              stroke="#f97316"
              strokeWidth={1.5}
              strokeDasharray="3 5"
              strokeLinecap="round"
              strokeOpacity={0.5}
            />
          )
        })}

        {/* Aviones operativos en movimiento */}
        {activeNonCancelled.map(({ flight, progress, shipments }) => {
          const origin = getAirportById(flight.origin)
          const dest = getAirportById(flight.destination)
          if (!origin || !dest) return null

          const currentPos = getFlightPosition(origin.coordinates, dest.coordinates, progress)
          const angle = getFlightAngle(origin.coordinates, dest.coordinates)

          return (
            <Marker key={`plane-${flight.id}`} coordinates={currentPos}>
              <g transform={`rotate(${angle})`}>
                <ellipse cx="0" cy="2" rx="10" ry="3.5" fill="rgba(0,0,0,0.3)" />
                <path
                  d="M0,-15 C1.5,-13 2.5,-9 2.5,-5 L13,3 L13,5.5 L2.5,2 L2.5,8 L5.5,10 L5.5,12 L0,11 L-5.5,12 L-5.5,10 L-2.5,8 L-2.5,2 L-13,5.5 L-13,3 L-2.5,-5 C-2.5,-9 -1.5,-13 0,-15 Z"
                  fill="#ffffff"
                  stroke="#94a3b8"
                  strokeWidth={0.5}
                />
                <ellipse cx="-7" cy="-1" rx="2.5" ry="1.2" fill="#e2e8f0" opacity="0.9" />
                <ellipse cx="7" cy="-1" rx="2.5" ry="1.2" fill="#e2e8f0" opacity="0.9" />
                <ellipse cx="0" cy="-9" rx="1.2" ry="1.5" fill="#60a5fa" opacity="0.9" />
                <g transform="translate(15, -2)">
                  <circle r="7" fill="#f97316" stroke="#ffffff" strokeWidth={1} />
                  <text x="0" y="0" textAnchor="middle" dominantBaseline="central" fill="#ffffff" fontSize="8" fontWeight="bold">
                    {shipments}
                  </text>
                </g>
              </g>
            </Marker>
          )
        })}

        {/* Aeropuertos */}
        {airports.map((airport) => {
          const utilizationPercent = (airport.currentStorage / airport.storageCapacity) * 100
          const status = getStatusColor(Math.min(utilizationPercent, 100))
          const isSelected = selectedAirport === airport.id
          const isCollapsing = collapsingAirportIds.has(airport.id)
          const collapseEvent = collapseEventByAirport.get(airport.id)

          const statusColor =
            status === "green" ? "#22c55e" :
            status === "amber" ? "#f59e0b" : "#ef4444"

          const collapseSeverityColor =
            collapseEvent?.severity === "catastrophic" ? "#dc2626" :
            collapseEvent?.severity === "critical" ? "#ea580c" : "#ca8a04"

          return (
            <Marker
              key={airport.id}
              coordinates={airport.coordinates}
              onClick={() => onAirportClick(airport.id)}
              style={{ cursor: "pointer" }}
            >
              {/* Anillo de colapso pulsante */}
              {isCollapsing && (
                <>
                  <circle
                    r="26"
                    fill={`${collapseSeverityColor}18`}
                    stroke={collapseSeverityColor}
                    strokeWidth={2.5}
                    className="animate-ping"
                    style={{ animationDuration: "1.2s" }}
                  />
                  <circle
                    r="20"
                    fill="transparent"
                    stroke={collapseSeverityColor}
                    strokeWidth={1.5}
                    strokeDasharray="5 3"
                    className="animate-pulse"
                  />
                </>
              )}

              {/* Anillo de selección */}
              {isSelected && !isCollapsing && (
                <circle
                  r="18"
                  fill="transparent"
                  stroke="#ffffff"
                  strokeWidth={2}
                  strokeDasharray="4 2"
                  className="animate-pulse"
                />
              )}

              {/* Base del aeropuerto */}
              <circle
                r={isSelected ? 14 : 12}
                fill={isCollapsing ? `${collapseSeverityColor}30` : "#1e293b"}
                stroke={isCollapsing ? collapseSeverityColor : statusColor}
                strokeWidth={isCollapsing ? 2.5 : 2}
              />

              {/* Icono de aeropuerto */}
              <g transform={isSelected ? "scale(1.1)" : "scale(1)"}>
                <rect x="-9" y="-1.5" width="18" height="4" rx="1.5" fill={isCollapsing ? collapseSeverityColor : statusColor} />
                <rect x="-1" y="-8" width="2" height="7" rx="0.5" fill="#ffffff" />
                <rect x="-2.5" y="-10" width="5" height="2.5" rx="1" fill="#ffffff" />
                <rect x="-7" y="2.5" width="1.5" height="4" rx="0.75" fill={isCollapsing ? collapseSeverityColor : statusColor} />
                <rect x="-0.75" y="2.5" width="1.5" height="4" rx="0.75" fill={isCollapsing ? collapseSeverityColor : statusColor} />
                <rect x="5.5" y="2.5" width="1.5" height="4" rx="0.75" fill={isCollapsing ? collapseSeverityColor : statusColor} />
              </g>

              {/* Indicador de capacidad */}
              <g transform="translate(10, -10)">
                <circle r="5" fill={isCollapsing ? collapseSeverityColor : statusColor} stroke="#0a1628" strokeWidth={1} />
                <text x="0" y="0.5" textAnchor="middle" dominantBaseline="central" fill="#ffffff" fontSize="6" fontWeight="bold">
                  {Math.min(Math.round(utilizationPercent), 99)}
                </text>
              </g>

              {/* Ícono de advertencia de colapso */}
              {isCollapsing && (
                <g transform="translate(-13, -13)">
                  <circle r="6" fill={collapseSeverityColor} stroke="#0a1628" strokeWidth={1} />
                  <text x="0" y="0.5" textAnchor="middle" dominantBaseline="central" fill="#ffffff" fontSize="8" fontWeight="bold">!</text>
                </g>
              )}

              {/* Código del aeropuerto */}
              <text
                textAnchor="middle"
                y={isSelected ? 26 : 22}
                fill={isCollapsing ? collapseSeverityColor : "#ffffff"}
                fontSize={isSelected ? "11" : "10"}
                fontWeight={isSelected || isCollapsing ? "bold" : "normal"}
                fontFamily="monospace"
              >
                {airport.code}
              </text>
            </Marker>
          )
        })}
      </ComposableMap>

      {/* Leyenda */}
      <div className="absolute bottom-3 left-3 bg-slate-900/95 backdrop-blur p-3 rounded-lg border border-slate-700 shadow-xl">
        <p className="text-xs text-slate-300 mb-2 font-medium">Capacidad Almacén</p>
        <div className="flex items-center gap-4 text-xs">
          <div className="flex items-center gap-1.5">
            <div className="w-3 h-3 rounded-full bg-green-500 shadow-sm shadow-green-500/50" />
            <span className="text-slate-400">{"<60%"}</span>
          </div>
          <div className="flex items-center gap-1.5">
            <div className="w-3 h-3 rounded-full bg-amber-500 shadow-sm shadow-amber-500/50" />
            <span className="text-slate-400">60-85%</span>
          </div>
          <div className="flex items-center gap-1.5">
            <div className="w-3 h-3 rounded-full bg-red-500 shadow-sm shadow-red-500/50" />
            <span className="text-slate-400">{">85%"}</span>
          </div>
        </div>
        <div className="mt-3 pt-2 border-t border-slate-700 space-y-1.5">
          <div className="flex items-center gap-3 text-xs">
            <div className="flex items-center gap-1.5">
              <svg width="16" height="16" viewBox="-16 -16 32 32">
                <path d="M0,-15 C1.5,-13 2.5,-9 2.5,-5 L13,3 L13,5.5 L2.5,2 L2.5,8 L5.5,10 L5.5,12 L0,11 L-5.5,12 L-5.5,10 L-2.5,8 L-2.5,2 L-13,5.5 L-13,3 L-2.5,-5 C-2.5,-9 -1.5,-13 0,-15 Z" fill="#ffffff" />
              </svg>
              <span className="text-slate-400">Vuelo activo</span>
            </div>
            <div className="flex items-center gap-1.5">
              <svg width="16" height="16" viewBox="-12 -12 24 24">
                <circle r="12" fill="#1e293b" stroke="#22c55e" strokeWidth="2" />
                <rect x="-7" y="-1" width="14" height="3" rx="1" fill="#22c55e" />
                <rect x="-0.75" y="-7" width="1.5" height="6" rx="0.5" fill="#ffffff" />
                <rect x="-2" y="-8.5" width="4" height="2" rx="0.75" fill="#ffffff" />
                <rect x="-5.5" y="2" width="1.2" height="3" rx="0.6" fill="#22c55e" />
                <rect x="-0.6" y="2" width="1.2" height="3" rx="0.6" fill="#22c55e" />
                <rect x="4.3" y="2" width="1.2" height="3" rx="0.6" fill="#22c55e" />
              </svg>
              <span className="text-slate-400">Aeropuerto</span>
            </div>
          </div>
          <div className="flex items-center gap-1.5 text-xs">
            <svg width="16" height="8" viewBox="0 0 16 8">
              <line x1="0" y1="4" x2="16" y2="4" stroke="#ef4444" strokeWidth="2" strokeDasharray="4 3" />
            </svg>
            <span className="text-slate-400">Ruta bloqueada</span>
          </div>
          <div className="flex items-center gap-1.5 text-xs">
            <div className="w-4 h-4 rounded-full border-2 border-red-500 flex items-center justify-center bg-red-900/30">
              <span className="text-red-400 text-[8px] font-bold leading-none">!</span>
            </div>
            <span className="text-slate-400">Colapso activo</span>
          </div>
        </div>
      </div>

      {/* Etiquetas de continentes */}
      <div className="absolute top-3 left-3 flex gap-2 text-xs">
        <span className="bg-slate-900/80 text-slate-300 px-2 py-1 rounded border border-slate-700">América</span>
        <span className="bg-slate-900/80 text-slate-300 px-2 py-1 rounded border border-slate-700">Europa</span>
        <span className="bg-slate-900/80 text-slate-300 px-2 py-1 rounded border border-slate-700">Asia</span>
      </div>
    </div>
  )
}

export const WorldMap = memo(WorldMapComponent)
