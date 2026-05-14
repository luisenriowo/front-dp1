import { SimulationState, SimulationMetrics, CollapseEvent } from "@/lib/simulation-data"
import { apiClient } from "./client"
import { toAirport } from "./airports"

export type PlanSimulationDto = {
  startDay?: number
  startDate?: string
  days?: number
  maxIterations?: number
  maxHops?: number
  timeLimitMs?: number
  seed?: number
}

const STATUS_MAP: Record<string, "pending" | "in-transit" | "delivered" | "delayed"> = {
  PENDING: "pending",
  IN_TRANSIT: "in-transit",
  DELIVERED: "delivered",
  DELIVERED_LATE: "delayed",
  NO_ROUTE: "pending",
}

export function toSimulationState(raw: any): SimulationState {
  return {
    ...raw,
    airports: Array.isArray(raw.airports) ? raw.airports.map(toAirport) : [],
    flights: Array.isArray(raw.flights) ? raw.flights : [],
    shipments: Array.isArray(raw.shipments)
      ? raw.shipments.map((shipment: any) => ({
          ...shipment,
          status: STATUS_MAP[String(shipment.status)] ?? "pending",
          createdAt: new Date(shipment.createdAt),
          deadline: new Date(shipment.deadline),
        }))
      : [],
  }
}

export const simulationApi = {
  getState: () => apiClient.get<any>("/simulation/state").then(toSimulationState),
  getMetrics: () => apiClient.get<SimulationMetrics>("/simulation/metrics"),
  getCollapseEvents: () => apiClient.get<CollapseEvent[]>("/simulation/collapse-events"),
  plan: (dto: PlanSimulationDto) =>
    apiClient.post<any>("/simulation/plan", dto).then(toSimulationState),
  start: () => apiClient.post<void>("/simulation/start", {}),
  pause: () => apiClient.post<void>("/simulation/pause", {}),
  reset: () => apiClient.post<void>("/simulation/reset", {}),
}
