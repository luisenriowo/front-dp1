import { SimulationState, SimulationMetrics, CollapseEvent } from "@/lib/simulation-data"
import { apiClient } from "./client"

export const simulationApi = {
  getState: () => apiClient.get<SimulationState>("/simulation/state"),
  getMetrics: () => apiClient.get<SimulationMetrics>("/simulation/metrics"),
  getCollapseEvents: () => apiClient.get<CollapseEvent[]>("/simulation/collapse-events"),
  start: () => apiClient.post<void>("/simulation/start", {}),
  pause: () => apiClient.post<void>("/simulation/pause", {}),
  reset: () => apiClient.post<void>("/simulation/reset", {}),
}
